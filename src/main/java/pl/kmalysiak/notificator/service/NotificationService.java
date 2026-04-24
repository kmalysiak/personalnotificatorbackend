package pl.kmalysiak.notificator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.service.ws.NotificationHandler;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationHandler handler;

    public void sendMessage(String fcmToken, NotificationData nd, String colapseKey, long ttl) {

        // konwertuj nd do Map<String, String> — FCM data payload wymaga String wartości
        Map<String, String> dataMap = mapper.convertValue(nd, new TypeReference<>() {
        });

        AndroidConfig.Builder acb = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH);

        if(colapseKey != null){
            acb.setCollapseKey(colapseKey);
        }
        if(ttl != -1){
            acb.setTtl(ttl);
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .putAllData(dataMap)
                .setAndroidConfig(
                    acb.build()
                )
                .build();


        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("FCM sent ok: messageId={}, token={}", messageId, fcmToken);

        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            log.warn("FCM send failed: code={}, token={}, msg={}", code, fcmToken, e.getMessage());

            throw switch (code) {
                case UNREGISTERED, INVALID_ARGUMENT ->
                        new FcmException(FcmException.ErrorCode.INVALID_TOKEN, "Token invalid/unregistered: " + fcmToken, e);

                case QUOTA_EXCEEDED -> new FcmException(FcmException.ErrorCode.RATE_LIMITED, "FCM rate limited", e);
                case UNAVAILABLE, INTERNAL ->
                        new FcmException(FcmException.ErrorCode.TRANSIENT, "FCM transient error: " + code, e);
                default -> new FcmException(FcmException.ErrorCode.UNKNOWN, "FCM error: " + code, e);
            };
        }
    }


    public void sendMessageWsChannel(WebSocketSession session, NotificationData nd) {
        Map<String, String> dataMap = mapper.convertValue(nd, new TypeReference<>() {});
        try {
            handler.sendMessage(session, dataMap);
        } catch (IOException e) {
            log.error("Błąd broadcast do session={}", session.getId(), e);
        }
    }
}


