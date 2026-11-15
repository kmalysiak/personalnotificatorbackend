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
 * Nowe powiadomienie dla urządzenia bez żywej sesji zaczyna kampanię (push od razu) albo — gdy
 * kampania już trwa — tylko przyspiesza następną próbę, max 1 push / 30 s ({@link #onNewNotification()}).
 * <p>
 * Tryb rzadki: po {@link #DORMANT_AFTER_CAMPAIGNS} kampaniach z rzędu bez reconnectu (np. wyłączony
 * emulator — ~130 pushy przez noc) urządzenie dostaje najwyżej 1 push / {@link #DORMANT_INTERVAL},
 * tylko przy nowym powiadomieniu. Handshake WS zeruje licznik ({@code UserTokenService.resetBackoff}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WsWakeUpService {

    private static final Duration GIVE_UP_AFTER = Duration.ofMinutes(30);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    // Tryb rzadki: po tylu kampaniach z rzędu zakończonych GIVE-UP bez reconnectu (emulator wyłączony,
    // telefon bez sieci / rozładowany) nie zaczynamy już pełnych kampanii przy każdym powiadomieniu —
    // najwyżej 1 push na DORMANT_INTERVAL, aż urządzenie samo się połączy (handshake WS zeruje licznik).
    static final int DORMANT_AFTER_CAMPAIGNS = 2;
    static final Duration DORMANT_INTERVAL = Duration.ofMinutes(30);

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
            if (isDormant(userToken)) {
                continue; // tryb rzadki — pushe tylko z onNewNotification(), max 1 / DORMANT_INTERVAL
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
     * Wołane po wysłaniu nowego powiadomienia przez WS dla urządzeń bez żywej sesji (śpiące, martwe
     * gniazdo już wykryte, po restarcie serwera).
     * <p>
     * Ograniczanie pushy: HA potrafi wysłać kilka powiadomień w tej samej sekundzie (np. start pompy:
     * rozruch + obieg + zawór + tryb), a wcześniej każde wysyłało osobny push — 5 pushy w 1 s. Android
     * obniża priorytet pushom high-priority, które nie kończą się widocznym powiadomieniem, więc taka
     * seria mogłaby zepsuć wybudzenie właśnie wtedy, gdy jest potrzebne. Teraz:
     * <ul>
     *   <li>brak trwającej kampanii (albo poprzednia się poddała) → nowa kampania, push od razu;</li>
     *   <li>kampania trwa → bez natychmiastowego pusha; tylko przyspieszamy następną próbę do
     *       najwyżej {@link #BASE_BACKOFF} od teraz (czyli max 1 push / 30 s przy serii zdarzeń);</li>
     *   <li>kampania kończyłaby się przed tą próbą → zaczynamy nową od tej (odroczonej) próby,
     *       żeby nowe powiadomienie nie przepadło przez GIVE-UP.</li>
     * </ul>
     */
    @Transactional
    public void onNewNotification() {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        for (UserToken userToken : userRepo.findAll()) {
            if (sessionRegistry.hasLiveSession(userToken.getUserTokenId())) continue;

            if (userToken.getConnectionLostAt() == null) {
                userToken.setConnectionLostAt(now); // np. po restarcie serwera rejestr sesji jest pusty
            }
            sleepOkLogged.remove(userToken.getFcmToken());

            if (isDormant(userToken)) {
                dormantWakeUp(userToken, now);
                continue;
            }

            LocalDateTime campaignStart = userToken.getFirstFailedAt();
            boolean campaignActive = campaignStart != null && campaignStart.isAfter(now.minus(GIVE_UP_AFTER));
            if (!campaignActive) {
                resetBackoff(userToken);
                wakeUp(userToken, now, "nowe powiadomienie, offline od " + userToken.getConnectionLostAt());
                continue;
            }

            LocalDateTime soonest = now.plus(BASE_BACKOFF);
            if (userToken.getNextRetryAt() == null || userToken.getNextRetryAt().isAfter(soonest)) {
                userToken.setNextRetryAt(soonest);
            }
            String note = "";
            if (campaignStart.plus(GIVE_UP_AFTER).isBefore(userToken.getNextRetryAt())) {
                // kampania wygasłaby przed odroczoną próbą — też się nie udała, liczy się jak GIVE-UP
                int failed = failedCampaigns(userToken) + 1;
                userToken.setFailedCampaigns(failed);
                if (failed >= DORMANT_AFTER_CAMPAIGNS) {
                    // kampania wygaśnie, a odroczona próba pójdzie już w trybie rzadkim (przy kolejnym powiadomieniu)
                    note = ", kampania wygasa; " + failed + ". z rzędu bez skutku → tryb rzadki";
                } else {
                    // nowa kampania zaczyna się od tej próby, żeby nowe powiadomienie nie przepadło
                    userToken.setFirstFailedAt(now);
                    userToken.setNotifyAttempts(0);
                    note = ", nowa kampania (poprzednia by wygasła)";
                }
            }
            userRepo.save(userToken);
            WsMon.LOG.info("[WSMON] WAKE-DEFER {} nowe powiadomienie w trakcie kampanii (próba {}) — bez dodatkowego pusha, następna za {}{}",
                    WsMon.dev(userToken), userToken.getNotifyAttempts(), WsMon.ago(now, userToken.getNextRetryAt()), note);
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
                    int failed = failedCampaigns(userToken) + 1;
                    userToken.setFailedCampaigns(failed);
                    userRepo.save(userToken);
                    if (failed >= DORMANT_AFTER_CAMPAIGNS) {
                        WsMon.LOG.warn("[WSMON] GIVE-UP {} — {} prób wybudzenia od {} bez reconnectu; {}. kampania z rzędu bez skutku → tryb rzadki: max 1 push / {} do ponownego połączenia",
                                WsMon.dev(userToken), userToken.getNotifyAttempts(), userToken.getFirstFailedAt(), failed, WsMon.ago(now, now.plus(DORMANT_INTERVAL)));
                    } else {
                        WsMon.LOG.warn("[WSMON] GIVE-UP {} — {} prób wybudzenia od {} bez reconnectu; kolejna kampania dopiero przy nowym powiadomieniu",
                                WsMon.dev(userToken), userToken.getNotifyAttempts(), userToken.getFirstFailedAt());
                    }
                }
            }
            case INVALID_TOKEN -> deleteInvalidToken(userToken);
        }
    }

    /**
     * Tryb rzadki (urządzenie nie wróciło po {@link #DORMANT_AFTER_CAMPAIGNS} kampaniach): push najwyżej
     * raz na {@link #DORMANT_INTERVAL}, tylko przy nowym powiadomieniu. {@code nextRetryAt} oznacza tu
     * "nie wcześniej niż"; {@code firstFailedAt} zostaje z ostatniej (wygasłej) kampanii, więc scheduler
     * {@link #wakeUpDroppedClients()} tego urządzenia nie podejmuje.
     */
    private void dormantWakeUp(UserToken userToken, LocalDateTime now) {
        if (userToken.getNextRetryAt() != null && userToken.getNextRetryAt().isAfter(now)) {
            // bez INFO — przy ruchu z HA byłaby to linia na każde powiadomienie przez całą noc
            WsMon.LOG.debug("[WSMON] WAKE-DORMANT-SKIP {} następny push najwcześniej za {}", WsMon.dev(userToken), WsMon.ago(now, userToken.getNextRetryAt()));
            return;
        }
        WakeUpResult result = trySendWakeUp(userToken);
        if (result == WakeUpResult.INVALID_TOKEN) {
            deleteInvalidToken(userToken);
            return;
        }
        userToken.setNotifyAttempts(attempts(userToken) + 1);
        userToken.setNextRetryAt(now.plus(DORMANT_INTERVAL));
        userRepo.save(userToken);
        WsMon.LOG.info("[WSMON] WAKE-DORMANT {} wynik={} (tryb rzadki po {} nieudanych kampaniach, offline od {}); następny najwcześniej za {}",
                WsMon.dev(userToken), result, failedCampaigns(userToken), userToken.getConnectionLostAt(), WsMon.ago(now, userToken.getNextRetryAt()));
    }

    private void deleteInvalidToken(UserToken userToken) {
        WsMon.LOG.warn("[WSMON] WAKE {} wynik=INVALID_TOKEN — usuwam token (aplikacja odinstalowana/token wygasł)", WsMon.dev(userToken));
        log.warn("Invalid FCM token, clearing userToken:{}", userToken.getDesc());
        sessionRegistry.unregister(userToken.getUserTokenId());
        userRepo.delete(userToken);
    }

    static boolean isDormant(UserToken userToken) {
        return failedCampaigns(userToken) >= DORMANT_AFTER_CAMPAIGNS;
    }

    private static int failedCampaigns(UserToken userToken) {
        return userToken.getFailedCampaigns() == null ? 0 : userToken.getFailedCampaigns();
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
