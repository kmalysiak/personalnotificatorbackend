package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.repo.UserTokenRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserTokenRepository repo;

    @Transactional
    public Set<Long> saveUserToken(String guid, String fcmToken, String login, LocalDateTime now) {
        Set<Long> userIds = repo.findByGuidAndFcmToken(guid, fcmToken).stream().map(UserToken::getId).collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.singleton(repo.save(new UserToken(guid, fcmToken, login, now)).getId());
        } else return userIds;
    }

    public List<String> getFcmTokensForGuid(String uid) {
        return repo.findByGuid(uid).stream().map(UserToken::getFcmToken).collect(Collectors.toList());
    }

    public List<UserToken> getUserTokensForLogin(String login) {
        return repo.findByLogin(login);
    }

    public List<String> getFcmTokensForLogin(String login) {
        return repo.findByLogin(login).stream().map(UserToken::getFcmToken).collect(Collectors.toList());
    }

    public List<String> getAllFcmTokens() {
        return repo.findAll().stream().map(UserToken::getFcmToken).collect(Collectors.toList());
    }
}