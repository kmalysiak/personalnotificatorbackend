package pl.kmalysiak.notificator.service.ws;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.model.UserTokenId;
import pl.kmalysiak.notificator.service.NotificationLogService;
import pl.kmalysiak.notificator.service.UserTokenService;
import pl.kmalysiak.notificator.utils.CustomObjectMapper;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationHandler extends TextWebSocketHandler {
    private final UserTokenService userTokenService;
    private final SessionRegistry sessionRegistry;
    private final NotificationLogService notificationLogService;

    @Value("${ws.replay.window-minutes}")
    private int replayWindowMinutes;

    @Value("${ws.replay.margin-seconds:120}")
    private long replayMarginSeconds;

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) {
        UserTokenId utId = WsSessionUtil.getUserTokenId(rawSession);
        SessionRegistry.Registration registration = sessionRegistry.register(rawSession, utId);
        String replacedId = "-";
        if (registration.replaced() != null) {
            // stara sesja tego samego urządzenia (zwykle już martwa) — jej spóźnione afterConnectionClosed
            // zostanie zignorowane, bo nie jest już aktualna w rejestrze
            replacedId = registration.replaced().getId();
            closeQuietly(registration.replaced(), CloseStatus.NORMAL);
        }
        replayRecentNotifications(registration.session(), utId, replacedId);
    }

    // Retransmisja po (re)connekcie liczona od watermarku klienta (pong "lastSeen") minus margines.
    // Margines, bo watermark to max(timestamp) widziany przez klienta: pingi niosą czas serwera,
    // a powiadomienia czas zdarzenia z HA (wcześniejszy) — powiadomienie zgubione tuż po pingu
    // miałoby timestamp < watermark. Okno (ws.replay.window-minutes) to wyłącznie fallback, gdy
    // jeszcze nie było pong-a. Klient i tak deduplikuje po hashu treści+timestamp.
    private void replayRecentNotifications(WebSocketSession session, UserTokenId utId, String replacedId) {
        UserToken userToken = userTokenService.getTokenById(utId);
        LocalDateTime lastSeenAt = userToken != null ? userToken.getLastSeenAt() : null;
        LocalDateTime since = lastSeenAt != null
                ? lastSeenAt.minusSeconds(replayMarginSeconds)
                : TimeZoneUtils.getLocalDateTimeNow().minusMinutes(replayWindowMinutes);

        List<NotificationData> recent = notificationLogService.getRecentSince(since);
        int sent = 0;
        for (NotificationData nd : recent) {
            try {
                sendMessage(session, nd);
                sent++;
            } catch (IOException e) {
                log.error("Błąd retransmisji do session={}", session.getId(), e);
                break;
            }
        }
        // jedna linia na połączenie: sesja + retransmisja (ile zaległości poszło po reconnekcie)
        WsMon.LOG.info("[WSMON] CONNECT {} session={} replaced={} replay={}/{} since={} ({})",
                userToken != null ? WsMon.dev(userToken) : WsMon.dev(utId), session.getId(), replacedId, sent, recent.size(), since,
                lastSeenAt != null ? "lastSeen-" + replayMarginSeconds + "s" : "brak lastSeen, okno " + replayWindowMinutes + " min");
        if (!recent.isEmpty()) {
            WsMon.LOG.info("[WSMON] REPLAY {} od {} do {}: {}", WsMon.dev(utId), recent.get(0).timestamp(), recent.get(recent.size() - 1).timestamp(),
                    recent.stream().map(nd -> nd.entityFriendlyName() + "=" + nd.msg()).limit(20).toList());
        }
    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // każda wiadomość od klienta = dowód, że połączenie żyje (patrz PingScheduler)
        sessionRegistry.touch(session);

        JsonNode json = CustomObjectMapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        if (type.equals("get_number")) {// klient wysyła: {"type":"get_number"}

            int number = new Random().nextInt(1000);
            WebSocketSession registered = sessionRegistry.get(session.getId());
            sendMessage(registered != null ? registered : session, Map.of("type", "number", "value", number));
        } else if (type.equals("pong")) {// klient wysyła: {"type":"pong","lastSeen":"<epoch sekundy>"}
            String lastSeen = json.get("lastSeen").asText();
            // pojedyncze pongi nie są logowane (co 10 s) — liczba pongów per sesja jest w WSMON STATUS
            userTokenService.onPong(WsSessionUtil.getUserTokenId(session), lastSeen);
        } else {
            log.warn("Nieznany typ wiadomości: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        markConnectionLost(session, "status=" + status.getCode() + (status.getReason() != null ? " " + status.getReason() : ""));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // bez stack trace — przy zrywanych połączeniach mobilnych (1006) to tylko szum
        markConnectionLost(session, "błąd transportu: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }

    /**
     * Wyrejestrowuje sesję i oznacza utratę połączenia urządzenia — ale tylko jeśli to była jego
     * aktualna sesja. Spóźnione zamknięcie starej, już zastąpionej sesji (szybki reconnect) nie
     * nadpisuje connection_lost_at na żywym połączeniu.
     */
    public void markConnectionLost(WebSocketSession session, String reason) {
        UserTokenId utId = WsSessionUtil.getUserTokenId(session);
        boolean current = sessionRegistry.unregister(session);
        if (current) {
            userTokenService.onConnectionLost(utId);
            WsMon.LOG.info("[WSMON] CLOSE {} session={} {}", WsMon.dev(utId), session.getId(), reason);
        } else {
            WsMon.LOG.debug("[WSMON] CLOSE (stara/już zamknięta sesja, ignoruję) {} session={} {}", WsMon.dev(utId), session.getId(), reason);
        }
    }

    public void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("Zamknięcie session={} nie powiodło się: {}", session.getId(), e.getMessage());
        }
    }

    public void sendMessage(WebSocketSession session, Object payload) throws IOException {
        if (session.isOpen()) {
            String json = CustomObjectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        }
    }
}