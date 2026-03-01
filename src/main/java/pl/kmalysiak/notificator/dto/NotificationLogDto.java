package pl.kmalysiak.notificator.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationLogDto {
    private String id;
    private String receiver;
    private LocalDateTime timestamp;
    private String entityFriendlyName;
    private String type;
    private String msg;
    private String priority;
}
