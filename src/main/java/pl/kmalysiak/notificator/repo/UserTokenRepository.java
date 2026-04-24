package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.model.UserTokenId;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, UserTokenId> {
    List<UserToken> findByUserFirebaseGuid(String uid);

    List<UserToken> findByEmail(String email);

    List<UserToken> findByUserFirebaseGuidAndFcmToken(String guid, String fcmToken);


    @Query("""
            SELECT u FROM UserToken u
            WHERE u.connectionLostAt IS NOT NULL
              AND u.connectionLostAt > :giveUpAt
              AND (u.nextRetryAt IS NULL OR u.nextRetryAt <= :now)
            """)
    List<UserToken> findDisconnectedForWakeUp(@Param("giveUpAt") LocalDateTime giveUpAt, @Param("now") LocalDateTime now);

    List<UserToken> findByConnectionLostAtBefore(LocalDateTime threshold);
}