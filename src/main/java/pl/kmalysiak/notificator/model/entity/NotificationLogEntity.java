package pl.kmalysiak.notificator.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ha_notification_log_entity",
        schema = "public"
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationLogEntity {
    @Id
    private String id;
    private String receiver;
    private String rawTimestamp;
    private LocalDateTime timestamp;
    private String entityFriendlyName;
    private String type;
    private String msg;
    private String priority;

}
