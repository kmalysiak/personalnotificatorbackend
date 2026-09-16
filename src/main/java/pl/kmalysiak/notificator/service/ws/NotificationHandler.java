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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session, WsSessionUtil.getUserTokenId(session));
        replayRecentNotifications(session);
    }

    // Retransmisja po (re)connekcie liczona od watermarku klienta (pong "lastSeen"). Okno
    // (ws.replay.window-minutes) to wyłącznie fallback, gdy jeszcze nie było pong-a (pierwszy
    // connect z danego urządzenia). Klient i tak deduplikuje po hashu treści+timestamp.
    private void replayRecentNotifications(WebSocketSession session) {
        UserToken userToken = userTokenService.getTokenById(WsSessionUtil.getUserTokenId(session));
        LocalDateTime lastSeenAt = userToken != null ? userToken.getLastSeenAt() : null;
        LocalDateTime since = lastSeenAt != null
                ? lastSeenAt
                : TimeZoneUtils.getLocalDateTimeNow().minusMinutes(replayWindowMinutes);

        List<NotificationData> recent = notificationLogService.getRecentSince(since);
        for (NotificationData nd : recent) {
            try {
                sendMessage(session, nd);
            } catch (IOException e) {
                log.error("Błąd retransmisji do session={}", session.getId(), e);
                break;
            }
        }
        log.info("Retransmisja po connect session={}: {} wiadomości (okno {} min)", session.getId(), recent.size(), replayWindowMinutes);
    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = CustomObjectMapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        if (type.equals("get_number")) {// klient wysyła: {"type":"get_number"}

            int number = new Random().nextInt(1000);
            sendMessage(session, Map.of("type", "number", "value", number));
        } else if (type.equals("pong")) {// klient wysyła: {"type":"pong","lastSeen":"<epoch sekundy>"}
            String lastSeen = json.get("lastSeen").asText();
            userTokenService.onPong(WsSessionUtil.getUserTokenId(session), lastSeen);
        } else {
            log.warn("Nieznany typ wiadomości: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        userTokenService.onConnectionLost(WsSessionUtil.getUserTokenId(session));
        log.info("Rozłączono: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Błąd transportu session={}", session.getId(), exception);
        userTokenService.onConnectionLost(WsSessionUtil.getUserTokenId(session));
    }

    public void sendMessage(WebSocketSession session, Object payload) throws IOException {
        if (session.isOpen()) {
            String json = CustomObjectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        }
    }
}