package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class BroadcastScheduler {

    private final SessionRegistry sessionRegistry;
    private final NotificationHandler notificationHandler;

    @Scheduled(fixedRateString = "${ws.broadcast.interval:10}000")
    public void broadcast() {
        Collection<WebSocketSession> sessions = sessionRegistry.getAll();
        if (sessions.isEmpty()) return;

        Map<String, Object> payload = Map.of(
                "type", "broadcast",
                "message", "ping z serwera",
                "timestamp", Instant.now().getEpochSecond()
        );

        sessions.forEach(session -> {
            try {
                notificationHandler.sendMessage(session, payload);
            } catch (IOException e) {
                log.error("Błąd broadcast do session={}", session.getId(), e);
            }
        });

        log.info("Broadcast do {} klientów", sessions.size());
    }
}