package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.model.UserTokenId;
import pl.kmalysiak.notificator.repo.UserTokenRepository;
import pl.kmalysiak.notificator.service.ws.SessionRegistry;
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

    @Transactional
    public UserTokenId onUserFcmTokenAuthorised(UserTokenId userTokenId, String email) {

        if (!userTokenId.isValid() || email == null)
            throw new RuntimeException(String.format("guid:%s. fcmToken:%s, email:%s", userTokenId.getUserFirebaseGuid(), userTokenId.getFcmToken(), email));

        Optional<UserToken> userToken = repo.findById(userTokenId);

        if (userToken.isEmpty()) {
            UserToken ut = new UserToken(userTokenId, email, TimeZoneUtils.getLocalDateTimeNow());
            log.info("Zapis nowego użytkownika:{}", ut.getDesc());
            return repo.save(ut).getUserTokenId();
        } else {
            userToken.get().setEmail(email);
            resetBackoff(userToken.get());
            log.info("Użytkownik:{} już istnieje. Aktualizacja mail i reset backoff", userToken.get().getDesc());
            return repo.save(userToken.get()).getUserTokenId();
        }

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
            log.info("Connection lost dla userToken:{}", userToken.getDesc());
            userToken.setConnectionLostAt(now); //właśnie nastąpiło connection lost
            return repo.save(userToken).getUserTokenId();
        }
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


    private void resetBackoff(UserToken userToken) {
        userToken.setNotifyAttempts(0);
        userToken.setNextRetryAt(null);
        userToken.setFirstFailedAt(null);
        userToken.setConnectionLostAt(null);
    }

    public UserToken getTokenById(UserTokenId userTokenId) {
        return repo.findById(userTokenId).orElse(null);
    }
}