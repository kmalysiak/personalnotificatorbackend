package pl.kmalysiak.notificator.model;

public record GuidResult(boolean isOk, String guid, String email, String errorDesc) {
}
