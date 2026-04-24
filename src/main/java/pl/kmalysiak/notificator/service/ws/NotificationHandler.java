package pl.kmalysiak.notificator.service.ws;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.kmalysiak.notificator.service.UserTokenService;
import pl.kmalysiak.notificator.utils.CustomObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationHandler extends TextWebSocketHandler {
    private final UserTokenService userTokenService;
    private final SessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session, WsSessionUtil.getUserTokenId(session));
    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = CustomObjectMapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        if (type.equals("get_number")) {// klient wysyła: {"type":"get_number"}

            int number = new Random().nextInt(1000);
            sendMessage(session, Map.of("type", "number", "value", number));
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