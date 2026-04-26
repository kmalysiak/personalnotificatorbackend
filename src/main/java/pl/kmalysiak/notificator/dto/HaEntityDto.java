package pl.kmalysiak.notificator.dto;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class HaEntityDto {
    private Long id;
    private String entityId;
    private String entityFriendlyName;
    private String entityType;
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
    private String payload;
    private String recipientEmails;
    private Long notificationTemplateId;


    @JsonIgnore
    public Set<String> getRecipientEmailsAsSet() {
        return Optional.ofNullable(recipientEmails)
                .stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @JsonIgnore
    public boolean shouldSendMobileNotification() {
        if (currTimestamp == null || notifyFreq == null || notifyFreq.isBlank()) {
            return false;
        }

        if (prevTimestamp == null)
            prevTimestamp = LocalDateTime.MIN;

        try {
            // Use system default zone to convert LocalDateTime -> Instant
            Instant earlierInstant = prevTimestamp.atZone(TimeZoneUtils.TIME_Z_WARSAW).toInstant();
            Instant laterInstant = currTimestamp.atZone(TimeZoneUtils.TIME_Z_WARSAW).toInstant();



            if (notifyFreq.startsWith("PT")) {
                Duration limit = Duration.parse(notifyFreq);
                return Duration.between(earlierInstant, laterInstant).compareTo(limit) > 0;
            }

            Period period = Period.parse(notifyFreq);
            LocalDateTime threshold = prevTimestamp.plus(period);
            Instant thresholdInstant = threshold.atZone(TimeZoneUtils.TIME_Z_WARSAW).toInstant();

            return laterInstant.isAfter(thresholdInstant);

        } catch (DateTimeException ex) {
            return false;
        }
    }


    @JsonIgnore
    public Long getUtcTimestamp() {
        return TimeZoneUtils.toUtcTimestamp(currTimestamp);
    }
}


