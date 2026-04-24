package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.service.ws.SessionRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationManager {

    private final NotificationService notificationService;
    private final UserTokenService userTokenService;
    private final SessionRegistry sessionRegistry;
    public void sendMsgToAllUsersViaFCM(NotificationData nd) {

        sessionRegistry.getAll();

        List<String> tokens = userTokenService.getAllFcmTokens();
        if (CollectionUtils.isEmpty(tokens))
            log.warn("No tokens");
        for (String token : tokens) {
            notificationService.sendMessage(token, nd, null, -1);
        }
    }

    public void sendMsgToAllUsersViaWs(NotificationData nd) {
        List<WebSocketSession> sessions = sessionRegistry.getAll();
        if (CollectionUtils.isEmpty(sessions))
            log.warn("No sessions");
        for (WebSocketSession session : sessions) {
            notificationService.sendMessageWsChannel(session, nd);
        }
    }

    public void sendMsgToUser(String userUid, NotificationData nd) {
        List<String> tokens = userTokenService.getFcmTokensForGuid(userUid);
        if (CollectionUtils.isEmpty(tokens)) log.error("No tokens for UID: " + userUid);

        tokens.forEach(token -> notificationService.sendMessage(token, nd,null, -1));
    }


    public void sendMsgToUsers(Set<String> emails, NotificationData nd) {
        for (String email : emails) {
            List<String> tokens = userTokenService.getFcmTokensForEmail(email);
            if (CollectionUtils.isEmpty(tokens))
                log.error("No tokens for email: " + email);
            tokens.forEach(token -> notificationService.sendMessage(token, nd,null, -1));
        }
    }


    public void sendWakeUpMsgToFirebaseToken(String fcmToken, NotificationData nd) {
        notificationService.sendMessage(fcmToken, nd,"ws_wakeup", Duration.ofMinutes(28).toMillis());
    }
}
