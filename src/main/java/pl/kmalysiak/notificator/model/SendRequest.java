package pl.kmalysiak.notificator.model;

public record SendRequest(String uid, String type, String msg) {
}