package pl.kmalysiak.notificator.dto;

/**
 * Generyczna koperta wiadomości WS: {@code {"msgType": "...", "payload": {...}}}.
 * Odróżnia strukturalne broadcasty stanu urządzenia (np. snapshot pompy Haier) od płaskiego
 * {@link pl.kmalysiak.notificator.model.NotificationData} używanego przez ping/powiadomienia z HA.
 */
public record WsEnvelopeDeviceStatus<DEVICE>(String msgType, DEVICE payload) {
}
