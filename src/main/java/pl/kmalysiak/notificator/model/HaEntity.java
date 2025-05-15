package pl.kmalysiak.notificator.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ha_entity",
        schema = "public",
        indexes = {
                @Index(
                        name = "idx_ha_entity_entity_id",
                        columnList = "entity_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class HaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String entityId;
    private Long receivedCount;
    private Boolean notifyMobile;
    private LocalDateTime prevReceived;
    private LocalDateTime currReceived;
    private LocalDateTime prevTimestamp;
    private LocalDateTime currTimestamp;
    private LocalDateTime lastNotified;
    private String currState;
    private String prevState;
    private String notifyFreq;
    private Integer payloadHistCount;
    private String prevUnit;
    private String currUnit;
    @Column(columnDefinition = "TEXT")
    private String payload;
    private String recipientEmails;
}
