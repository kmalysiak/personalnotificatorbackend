package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.model.UserTokenId;
import pl.kmalysiak.notificator.repo.UserTokenRepository;
import pl.kmalysiak.notificator.service.ws.SessionRegistry;
import pl.kmalysiak.notificator.service.ws.WsMon;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTokenService {
    private final UserTokenRepository repo;
    private final SessionRegistry registry;

    /** Skąd przyszła autoryzacja tokenu — od tego zależy, czy zerujemy stan połączenia. */
    public enum AuthSource {
        /** handshake WS — urządzenie faktycznie się połączyło: zerujemy connection_lost_at i kampanię wybudzania */
        WS_HANDSHAKE,
        /**
         * REST {@code POST /api/register} — tylko rejestracja tokenu/maila. NIE zeruje stanu połączenia:
         * apka woła to przed łączeniem WS (np. po wybudzeniu FCM), a jeśli samo połączenie WS się potem
         * nie uda, wyzerowany connection_lost_at sprawiłby, że WsWakeUpService przestałby budzić telefon.
         */
        REST_REGISTER
    }

    @Transactional
    public UserTokenId onUserFcmTokenAuthorised(UserTokenId userTokenId, String email, AuthSource source) {

        if (!userTokenId.isValid() || email == null)
            throw new RuntimeException(String.format("guid:%s. fcmToken:%s, email:%s", userTokenId.getUserFirebaseGuid(), userTokenId.getFcmToken(), email));

        Optional<UserToken> userToken = repo.findById(userTokenId);

        if (userToken.isEmpty()) {
            UserToken ut = new UserToken(userTokenId, email, TimeZoneUtils.getLocalDateTimeNow());
            ut.setNotifyAttempts(0);
            log.info("Zapis nowego użytkownika ({}):{}", source, ut.getDesc());
            return repo.save(ut).getUserTokenId();
        }

        UserToken ut = userToken.get();
        ut.setEmail(email);
        if (source == AuthSource.WS_HANDSHAKE) {
            logReconnectAfterOutage(ut);
            resetBackoff(ut);
        } else {
            WsMon.LOG.info("[WSMON] REGISTER {} (REST, bez zmiany stanu połączenia: żywa sesja WS={}, offline od {}, wakeAttempts={})",
                    WsMon.dev(ut), registry.hasLiveSession(userTokenId), ut.getConnectionLostAt(), ut.getNotifyAttempts());
        }
        return repo.save(ut).getUserTokenId();
    }

    @Transactional
    public UserTokenId onConnectionLost(UserTokenId utId) {
        UserToken userToken = repo.findById(utId).orElse(null);
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        if (userToken == null) {
            //do nothing -> user nigdy niewidziany
            log.info("Connection lost dla nieznanego:{}", utId.getDesc());
            return new UserTokenId(null, null);
        } else {
            log.debug("Connection lost dla userToken:{}", userToken.getDesc()); // zdarzenie: WSMON CLOSE / DEAD
            userToken.setConnectionLostAt(now); //właśnie nastąpiło connection lost
            return repo.save(userToken).getUserTokenId();
        }
    }


    @Transactional
    public void onPong(UserTokenId utId, String lastSeenEpochSeconds) {
        UserToken userToken = repo.findById(utId).orElse(null);
        if (userToken == null) {
            log.warn("Pong dla nieznanego userToken:{}", utId.getDesc());
            return;
        }
        userToken.setLastSeenAt(TimeZoneUtils.epochSecondsToLocalDateTime(lastSeenEpochSeconds));
        repo.save(userToken);
    }

    public List<String> getFcmTokensForGuid(String uid) {
        return repo.findByUserFirebaseGuid(uid).stream().map(UserToken::getFcmToken).collect(Collectors.toList());
    }


    public List<UserToken> getUserTokensForEmail(String email) {
        return repo.findByEmail(email);
    }

    public List<String> getFcmTokensForEmail(String email) {
        return repo.findByEmail(email).stream().map(UserToken::getFcmToken).collect(Collectors.toList());
    }

    public List<String> getAllFcmTokens() {
        return repo.findAll().stream().map(UserToken::getFcmToken).collect(Collectors.toList());
    }


    // Ocena mechanizmu wybudzania: ile urządzenie było offline i czy wróciło po pushu FCM
    // (wakeAttempts > 0) czy samo (0 — np. wybudzenie użytkownika / okno maintenance Doze).
    private void logReconnectAfterOutage(UserToken ut) {
        if (ut.getConnectionLostAt() == null) {
            WsMon.LOG.info("[WSMON] HANDSHAKE {} (bez wcześniejszej utraty połączenia)", WsMon.dev(ut));
            return;
        }
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        int attempts = ut.getNotifyAttempts() == null ? 0 : ut.getNotifyAttempts();
        WsMon.LOG.info("[WSMON] HANDSHAKE {} po przerwie {} (lostAt={}), wakeAttempts={}, pierwszy wake {} temu, lastSeen={}",
                WsMon.dev(ut), WsMon.ago(ut.getConnectionLostAt(), now), ut.getConnectionLostAt(), attempts,
                WsMon.ago(ut.getFirstFailedAt(), now), ut.getLastSeenAt());
    }

    private void resetBackoff(UserToken userToken) {
        userToken.setNotifyAttempts(0);
        userToken.setNextRetryAt(null);
        userToken.setFirstFailedAt(null);
        userToken.setConnectionLostAt(null);
    }

    public List<UserToken> getAll() {
        return repo.findAll();
    }

    public UserToken getTokenById(UserTokenId userTokenId) {
        return repo.findById(userTokenId).orElse(null);
    }
}