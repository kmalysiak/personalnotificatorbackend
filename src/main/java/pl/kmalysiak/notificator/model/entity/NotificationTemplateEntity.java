package pl.kmalysiak.notificator.model.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "notification_template",
        schema = "public"
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String statusTemplate;
}
