package pl.kmalysiak.notificator.rabbit.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.util.CollectionUtils;
import pl.kmalysiak.notificator.utils.CustomObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EventNotification {
    private String receivedRoutingKey;
    private String receivedExchange;
    private String payload;
    @JsonProperty("entity_id")
    private String entityId;
    private String state;
    private LocalDateTime timestamp;
    private Map<String, Object> attributes;


    public static EventNotification fromMessage(Message msg) {
        MessageProperties props = msg.getMessageProperties();
        String rawPayload = new String(msg.getBody(), StandardCharsets.UTF_8);
        EventNotification en = CustomObjectMapper.fromJson(rawPayload, EventNotification.class);
        en.setReceivedRoutingKey(props.getReceivedRoutingKey());
        en.setReceivedExchange(props.getReceivedExchange());
        en.setPayload(rawPayload);
        return en;
    }

    public String getUnitOfMeasurement() {
        if (!CollectionUtils.isEmpty(attributes)) {
            Object obj = attributes.getOrDefault("unit_of_measurement", null);
            if (obj == null) return null;
            if (obj instanceof String) return (String) obj;
            return String.valueOf(obj);
        }
        return null;
    }
}



