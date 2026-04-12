package pl.kmalysiak.notificator.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.kmalysiak.notificator.model.GuidResult;
import pl.kmalysiak.notificator.utils.CustomObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationHandler extends TextWebSocketHandler {
    private final TokenVerifierService tokenVerifierService;
    private final SessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // sesja dodana dopiero po weryfikacji tokenu w handleTextMessage
        log.info("Nowe połączenie (niezweryfikowane): {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = CustomObjectMapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        switch (type) {
            case "auth" -> {
                // klient wysyła: {"type":"auth","idToken":"..."}
                String idToken = json.get("idToken").asText();
                GuidResult result = tokenVerifierService.verifyAndGetUid(idToken);

                if (result.isOk()) {
                    sessionRegistry.register(session, result.guid());
                    log.info("Zautoryzowano uid={} session={}", result.guid(), session.getId());
                    sendMessage(session, Map.of("type", "auth_ok", "uid", result.guid()));
                } else {
                    log.warn("Nieudana autoryzacja session={}", session.getId());
                    sendMessage(session, Map.of("type", "auth_error", "reason", result.errorDesc()));
                    session.close(CloseStatus.NOT_ACCEPTABLE);
                }
            }

            case "get_number" -> {
                // klient wysyła: {"type":"get_number"}
                if (!isAuthenticated(session)) {
                    sendUnauthorized(session);
                    return;
                }
                int number = new Random().nextInt(1000);
                sendMessage(session, Map.of("type", "number", "value", number));
            }

            default -> log.warn("Nieznany typ wiadomości: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
        log.info("Rozłączono: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Błąd transportu session={}", session.getId(), exception);
        sessionRegistry.unregister(session);
    }


    private boolean isAuthenticated(WebSocketSession session) {
        return sessionRegistry.getUid(session) != null;
    }

    private void sendUnauthorized(WebSocketSession session) throws IOException {
        sendMessage(session, Map.of("type", "error", "reason", "not authenticated"));
    }

    public void sendMessage(WebSocketSession session, Object payload) throws IOException {
        if (session.isOpen()) {
            String json = CustomObjectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        }
    }
}