package pl.kmalysiak.notificator.model;

public record TokenRequest(String uid, String fcmToken, String login) {}