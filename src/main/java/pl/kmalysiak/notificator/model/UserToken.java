package pl.kmalysiak.notificator.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_token", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@IdClass(UserTokenId.class)
public class UserToken {
    @Id
    private String userFirebaseGuid;
    @Id
    private String fcmToken;

    private String email;
    private LocalDateTime created;
    private LocalDateTime connectionLostAt;  // null = aktualnie połączony
    private Integer notifyAttempts;          // ile razy próbowaliśmy
    private LocalDateTime nextRetryAt;   // kiedy następna próba (null = jeszcze nie próbowano)
    private LocalDateTime firstFailedAt; // kiedy zaczęliśmy się dobijać (do logiki usuwania)
    public UserToken(String userFirebaseGuid, String fcmToken, String email, LocalDateTime created) {
        this.userFirebaseGuid = userFirebaseGuid;
        this.fcmToken = fcmToken;
        this.email = email;
        this.created = created;
    }

    public UserToken(UserTokenId utId, String email, LocalDateTime created) {
        this.userFirebaseGuid = utId.getUserFirebaseGuid();
        this.fcmToken = utId.getFcmToken();
        this.email = email;
        this.created = created;
    }


    @Transient
    public UserTokenId getUserTokenId(){
        return new UserTokenId(userFirebaseGuid, fcmToken);
    }

    @Transient
    public String getDesc(){
        return  String.format("fiGuid:%s fcmToken:%s, email:%s, created:%s", userFirebaseGuid, fcmToken, email, created);
    }
}