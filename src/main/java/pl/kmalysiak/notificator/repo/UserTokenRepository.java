package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.kmalysiak.notificator.model.UserToken;

import java.util.List;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    List<UserToken> findByGuid(String uid);
    List<UserToken> findByLogin(String login);
    List<UserToken> findByGuidAndFcmToken(String guid, String fcmToken);
}