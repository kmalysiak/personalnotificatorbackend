package pl.kmalysiak.notificator.service.ws;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.repo.UserTokenRepository;
import pl.kmalysiak.notificator.service.FcmException;
import pl.kmalysiak.notificator.service.NotificationManager;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final SessionRegistry sessionRegistry;

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void wakeUpDroppedClients() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime giveUpAt = now.minus(GIVE_UP_AFTER);

        List<UserToken> candidates = userRepo.findDisconnectedForWakeUp(giveUpAt, now);

        log.debug("WS wake-up candidates: {}", candidates.size());

        for (UserToken userToken : candidates) {
            WakeUpResult result = trySendWakeUp(userToken);

            switch (result) {
                case SENT -> {
                    applyBackoff(userToken, now);
                    userRepo.save(userToken);
                }
                case FAILED -> {
                    resetBackoff(userToken);
                    userRepo.save(userToken);
                }
                case INVALID_TOKEN -> {
                    log.warn("Invalid FCM token, clearing userToken:{}", userToken.getDesc());
                    sessionRegistry.unregister(userToken.getUserFirebaseGuid());
                    userRepo.delete(userToken);
                }
            }
        }
    }

    // === Pętla cleanup: raz na godzinę ===
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteGhostUsers() {
        LocalDateTime deleteThreshold = LocalDateTime.now().minus(DELETE_THRESHOLD);
        List<UserToken> ghosts = userRepo.findByConnectionLostAtBefore(deleteThreshold);
        if (!ghosts.isEmpty()) {
            log.info("Deleting {} ghost users", ghosts.size());
            userRepo.deleteAll(ghosts);
        }
    }

    private WakeUpResult trySendWakeUp(UserToken userToken) {
        try {
            notificationManager.sendWakeUpMsgToFirebaseToken(userToken.getFcmToken(), new NotificationData(
                    "połącz się", "reconnect_request", "próba " + userToken.getNotifyAttempts(), TimeZoneUtils.epochSecondsNowStr())
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


    private void applyBackoff(UserToken userToken, LocalDateTime now) {
        if (userToken.getFirstFailedAt() == null) userToken.setFirstFailedAt(now);
        int attempts = userToken.getNotifyAttempts() + 1;
        userToken.setNotifyAttempts(attempts);

        long delaySec = Math.min(
                BASE_BACKOFF.toSeconds() * (1L << (attempts - 1)),
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