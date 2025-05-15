package pl.kmalysiak.notificator.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "notification_template",
        schema = "public",
        indexes = {
                @Index(

                        name = "idx_ha_notification_template_entity_id",
                        columnList = "entity_id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "notification_template_entity_id", columnNames = "entity_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String entityId;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String typeTemplate;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String statusTemplate;
}
