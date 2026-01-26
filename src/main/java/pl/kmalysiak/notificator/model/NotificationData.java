package pl.kmalysiak.notificator.model;

public record NotificationData(String entityFriendlyName, String type, String msg, String timestamp) {
}
