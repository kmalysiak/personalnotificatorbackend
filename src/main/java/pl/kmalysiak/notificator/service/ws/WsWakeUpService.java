package pl.kmalysiak.notificator.service.ws;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.repo.UserTokenRepository;
import pl.kmalysiak.notificator.service.FcmException;
import pl.kmalysiak.notificator.service.NotificationLogService;
import pl.kmalysiak.notificator.service.NotificationManager;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Wybudzanie telefonu przez FCM, gdy nie ma z nim żywej sesji WS. FCM niesie tylko sygnał
 * {@code reconnect_request} — treść powiadomień zawsze idzie przez WS (retransmisja po reconnekcie
 * od {@code last_seen_at}).
 * <p>
 * Budzimy tylko wtedy, gdy urządzenie ma coś do odebrania (wpisy w logu powiadomień nowsze niż
 * jego watermark) — inaczej telefon w Doze byłby budzony w kółko: wake → connect → zaśnięcie →
 * martwa sesja → wake...
 * <p>
 * "Kampania" wybudzania: backoff 30 s → 5 min, poddaje się po 30 min od pierwszej próby.
 * Każde nowe powiadomienie dla urządzenia bez żywej sesji zaczyna kampanię od nowa i od razu
 * wysyła push ({@link #onNewNotification()}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WsWakeUpService {

    private static final Duration GIVE_UP_AFTER = Duration.ofMinutes(30);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    // Osobna logika usuwania — długi czas
    private static final Duration DELETE_THRESHOLD = Duration.ofDays(30);

    private final UserTokenRepository userRepo;
    private final NotificationManager notificationManager;
    private final NotificationLogService notificationLogService;
    private final SessionRegistry sessionRegistry;

    @Value("${ws.replay.margin-seconds:120}")
    private long replayMarginSeconds;

    // tylko do logów: urządzenia, dla których już zalogowano "offline, ale nic do dostarczenia" —
    // żeby SLEEP-OK pojawiło się raz na epizod snu, a nie co 10 s przez całą noc
    private final Set<String> sleepOkLogged = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void wakeUpDroppedClients() {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        List<UserToken> candidates = userRepo.findDisconnectedForWakeUp(now.minus(GIVE_UP_AFTER), now);

        for (UserToken userToken : candidates) {
            String key = userToken.getFcmToken();
            if (sessionRegistry.hasLiveSession(userToken.getUserTokenId())) {
                sleepOkLogged.remove(key);
                continue; // nieaktualne connection_lost_at — urządzenie jest połączone
            }
            if (!notificationLogService.hasPendingSince(userToken.getLastSeenAt(), replayMarginSeconds)) {
                // nic do dostarczenia — niech telefon śpi
                if (sleepOkLogged.add(key)) {
                    WsMon.LOG.info("[WSMON] SLEEP-OK {} offline od {} ({}), brak zaległych po lastSeen={} — nie budzę",
                            WsMon.dev(userToken), userToken.getConnectionLostAt(), WsMon.ago(userToken.getConnectionLostAt(), now), userToken.getLastSeenAt());
                }
                continue;
            }
            sleepOkLogged.remove(key);
            wakeUp(userToken, now, "zaległe po lastSeen=" + userToken.getLastSeenAt());
        }
    }

    /**
     * Wołane po wysłaniu nowego powiadomienia przez WS: urządzenia bez żywej sesji (śpiące, martwe
     * gniazdo już wykryte, po restarcie serwera) budzimy od razu, zaczynając kampanię od nowa.
     */
    @Transactional
    public void onNewNotification() {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        for (UserToken userToken : userRepo.findAll()) {
            if (sessionRegistry.hasLiveSession(userToken.getUserTokenId())) continue;

            if (userToken.getConnectionLostAt() == null) {
                userToken.setConnectionLostAt(now); // np. po restarcie serwera rejestr sesji jest pusty
            }
            resetBackoff(userToken);
            sleepOkLogged.remove(userToken.getFcmToken());
            wakeUp(userToken, now, "nowe powiadomienie, offline od " + userToken.getConnectionLostAt());
        }
    }

    private void wakeUp(UserToken userToken, LocalDateTime now, String reason) {
        WakeUpResult result = trySendWakeUp(userToken);
        switch (result) {
            case SENT, FAILED -> {
                // FAILED też z backoffem — bez tego błąd FCM (np. RATE_LIMITED) dawałby próbę co cykl
                applyBackoff(userToken, now);
                userRepo.save(userToken);
                WsMon.LOG.info("[WSMON] WAKE {} próba={} wynik={} powód: {}; następna za {}",
                        WsMon.dev(userToken), userToken.getNotifyAttempts(), result, reason, WsMon.ago(now, userToken.getNextRetryAt()));
                if (userToken.getNextRetryAt().isAfter(userToken.getFirstFailedAt().plus(GIVE_UP_AFTER))) {
                    WsMon.LOG.warn("[WSMON] GIVE-UP {} — {} prób wybudzenia od {} bez reconnectu; kolejna kampania dopiero przy nowym powiadomieniu",
                            WsMon.dev(userToken), userToken.getNotifyAttempts(), userToken.getFirstFailedAt());
                }
            }
            case INVALID_TOKEN -> {
                WsMon.LOG.warn("[WSMON] WAKE {} wynik=INVALID_TOKEN — usuwam token (aplikacja odinstalowana/token wygasł)", WsMon.dev(userToken));
                log.warn("Invalid FCM token, clearing userToken:{}", userToken.getDesc());
                sessionRegistry.unregister(userToken.getUserTokenId());
                userRepo.delete(userToken);
            }
        }
    }

    // === Pętla cleanup: raz na godzinę ===
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteGhostUsers() {
        LocalDateTime deleteThreshold = TimeZoneUtils.getLocalDateTimeNow().minus(DELETE_THRESHOLD);
        List<UserToken> ghosts = userRepo.findByConnectionLostAtBefore(deleteThreshold);
        if (!ghosts.isEmpty()) {
            log.info("Deleting {} ghost users", ghosts.size());
            userRepo.deleteAll(ghosts);
        }
    }

    private WakeUpResult trySendWakeUp(UserToken userToken) {
        try {
            notificationManager.sendWakeUpMsgToFirebaseToken(userToken.getFcmToken(), new NotificationData(
                    "połącz się", "reconnect_request", "próba " + attempts(userToken), TimeZoneUtils.epochSecondsNowStr())
            );
            return WakeUpResult.SENT;
        } catch (FcmException e) {
            return switch (e.getErrorCode()) {
                case INVALID_TOKEN -> WakeUpResult.INVALID_TOKEN;
                case RATE_LIMITED -> WakeUpResult.FAILED;  // backoff, ale nie czyść tokenu
                case TRANSIENT, UNKNOWN -> WakeUpResult.FAILED;
            };
        } catch (Exception e) {
            log.warn("Unexpected error during wake-up - userToken:{} error={}", userToken.getDesc(), e.getMessage());
            return WakeUpResult.FAILED;
        }
    }

    private static int attempts(UserToken userToken) {
        return userToken.getNotifyAttempts() == null ? 0 : userToken.getNotifyAttempts();
    }

    private void applyBackoff(UserToken userToken, LocalDateTime now) {
        if (userToken.getFirstFailedAt() == null) userToken.setFirstFailedAt(now);
        int attempts = attempts(userToken) + 1;
        userToken.setNotifyAttempts(attempts);

        long delaySec = Math.min(
                BASE_BACKOFF.toSeconds() * (1L << Math.min(attempts - 1, 10)),
                MAX_BACKOFF.toSeconds()
        );
        userToken.setNextRetryAt(now.plusSeconds(delaySec));
    }

    private void resetBackoff(UserToken userToken) {
        userToken.setNotifyAttempts(0);
        userToken.setNextRetryAt(null);
        userToken.setFirstFailedAt(null);
    }

    enum WakeUpResult {SENT, FAILED, INVALID_TOKEN}
}
