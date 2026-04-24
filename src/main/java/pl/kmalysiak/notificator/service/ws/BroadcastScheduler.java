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
public class BroadcastScheduler {

    private final SessionRegistry sessionRegistry;
    private final NotificationHandler notificationHandler;
    private final UserTokenService userTokenService;

    @Scheduled(fixedRateString = "${ws.broadcast.interval:10}000")
    public void broadcast() {
        Collection<WebSocketSession> sessions = sessionRegistry.getAll();
        if (sessions.isEmpty()) return;

        Map<String, Object> payload = Map.of(
                "type", "broadcast",
                "message", "ping z serwera i timestamp::",
                "timestamp", TimeZoneUtils.toEpochSeconds(LocalDateTime.now())
        );



        sessions.forEach(session -> {
            try {
                notificationHandler.sendMessage(session, new NotificationData("ping wss", "ws_ping", "ping", TimeZoneUtils.epochSecondsNow()));
                UserToken ut = userTokenService.getTokenById(WsSessionUtil.getUserTokenId(session));
                if (ut != null)
                    log.info("Broadcast do sesji:{}, userToken:{}", session.getId(), ut.getDesc());
                else {
                    log.warn("Próba broadcast dla nieistniejącego ut:{}, usuwam z repo sesji.", WsSessionUtil.getUserTokenId(session));
                    sessionRegistry.unregister(session);
                }
            } catch (IOException e) {
                log.error("Błąd broadcast do session={}", session.getId(), e);
            }
        });

        log.info("Broadcast do {} klientów", sessions.size());
    }
}