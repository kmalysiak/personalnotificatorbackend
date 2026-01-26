package pl.kmalysiak.notificator.model;

public record SendRequest(String uid, NotificationData nd) {
}