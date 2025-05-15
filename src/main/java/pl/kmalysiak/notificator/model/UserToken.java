package pl.kmalysiak.notificator.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_token", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class UserToken {
    public UserToken(String guid, String fcmToken, String login, LocalDateTime created) {
        this.guid = guid;
        this.fcmToken = fcmToken;
        this.login = login;
        this.created = created;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String guid;
    private String fcmToken;
    private String login;
    private LocalDateTime created;

}