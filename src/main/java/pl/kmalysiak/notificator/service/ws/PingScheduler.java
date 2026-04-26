package pl.kmalysiak.notificator.service.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.service.UserTokenService;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PingScheduler {

    private final SessionRegistry sessionRegistry;
    private final NotificationHandler notificationHandler;
    private final UserTokenService userTokenService;

    @Scheduled(fixedRateString = "${ws.broadcast.interval:10}000")
    public void ping() {
        Collection<WebSocketSession> sessions = sessionRegistry.getAll();
        if (sessions.isEmpty()) return;

        sessions.forEach(session -> {
            try {
                notificationHandler.sendMessage(session, new NotificationData("ping wss", "ws_ping", "ping", TimeZoneUtils.epochSecondsNowStr()));
                UserToken ut = userTokenService.getTokenById(WsSessionUtil.getUserTokenId(session));
                if (ut != null)
                    log.info("Ping do sesji:{}, userToken:{}", session.getId(), ut.getDesc());
                else {
                    log.warn("Próba ping dla nieistniejącego ut:{}, usuwam z repo sesji.", WsSessionUtil.getUserTokenId(session));
                    sessionRegistry.unregister(session);
                }
            } catch (IOException e) {
                log.error("Błąd ping do session={}", session.getId(), e);
            }
        });

        log.info("Ping do {} klientów", sessions.size());
    }
}