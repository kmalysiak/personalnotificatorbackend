package pl.kmalysiak.notificator.service.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.service.UserTokenService;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class PingScheduler {

    private final SessionRegistry sessionRegistry;
    private final NotificationHandler notificationHandler;
    private final UserTokenService userTokenService;

    // Sesja bez żadnej wiadomości od klienta (pong na ws_ping) dłużej niż tyle = martwa. Telefon w Doze
    // potrafi "zamrozić" gniazdo tak, że serwer (zwłaszcza za Cloudflare) dalej bez błędu do niego pisze.
    // Domyślnie 35 s przy pingu co 10 s = ~3 pingi bez odpowiedzi. <= 0 wyłącza wykrywanie.
    @Value("${ws.dead-after-seconds:35}")
    private long deadAfterSeconds;

    // co ile minut logować WSMON STATUS (stan sesji + urządzenia offline) zamiast linii co ping
    @Value("${ws.monitor.summary-minutes:5}")
    private long summaryMinutes;

    private Instant lastSummaryAt = Instant.EPOCH;

    @Scheduled(fixedRateString = "${ws.broadcast.interval:10}000")
    public void ping() {
        Collection<WebSocketSession> sessions = sessionRegistry.getAll();

        for (WebSocketSession session : sessions) {
            if (isDead(session)) {
                closeDeadSession(session);
                continue;
            }
            try {
                notificationHandler.sendMessage(session, new NotificationData("ping wss", "ws_ping", "ping", TimeZoneUtils.epochSecondsNowStr()));
                UserToken ut = userTokenService.getTokenById(WsSessionUtil.getUserTokenId(session));
                if (ut == null) {
                    log.warn("Próba ping dla nieistniejącego ut:{}, usuwam z repo sesji.", WsSessionUtil.getUserTokenId(session));
                    sessionRegistry.unregister(session);
                }
            } catch (IOException e) {
                log.error("Błąd ping do session={}", session.getId(), e);
            }
        }

        logSummaryIfDue();
    }

    private boolean isDead(WebSocketSession session) {
        if (deadAfterSeconds <= 0) return false;
        Instant lastActivity = sessionRegistry.getLastActivity(session);
        return lastActivity != null && Duration.between(lastActivity, Instant.now()).getSeconds() > deadAfterSeconds;
    }

    // Wyrejestrowanie + connection_lost_at robimy sami, nie czekając na afterConnectionClosed —
    // zamknięcie martwego gniazda może się nie zakończyć szybko (albo wcale). Dalej to już
    // WsWakeUpService: jeśli urządzenie ma zaległe powiadomienia, budzi je przez FCM.
    private void closeDeadSession(WebSocketSession session) {
        Instant lastActivity = sessionRegistry.getLastActivity(session);
        long silentFor = lastActivity == null ? -1 : Duration.between(lastActivity, Instant.now()).getSeconds();
        WsMon.LOG.warn("[WSMON] DEAD {} session={} — brak odpowiedzi od klienta od {}s (próg {}s), zamykam",
                WsMon.dev(WsSessionUtil.getUserTokenId(session)), session.getId(), silentFor, deadAfterSeconds);
        notificationHandler.markConnectionLost(session, "uznana za martwą (brak pong)");
        notificationHandler.closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
    }

    // Okresowy przegląd: żywe sesje (ile pongów w okresie, kiedy ostatni) + urządzenia offline
    // (od kiedy, ile prób wybudzenia, watermark). Ciągłość tych linii przez noc pokazuje, czy
    // telefon odpowiadał, a luki — kiedy przestał.
    private void logSummaryIfDue() {
        if (summaryMinutes <= 0) return;
        Instant now = Instant.now();
        if (Duration.between(lastSummaryAt, now).toMinutes() < summaryMinutes) return;
        lastSummaryAt = now;

        List<SessionRegistry.SessionStat> stats = sessionRegistry.snapshotAndResetCounters();
        String live = stats.stream()
                .map(s -> String.format("%s pongi=%d ostatni=%ss temu", WsMon.dev(s.userTokenId()), s.clientMsgCount(),
                        s.lastActivity() == null ? "?" : Duration.between(s.lastActivity(), now).getSeconds()))
                .collect(Collectors.joining("; "));

        LocalDateTime nowLocal = TimeZoneUtils.getLocalDateTimeNow();
        String offline = userTokenService.getAll().stream()
                .filter(ut -> !sessionRegistry.hasLiveSession(ut.getUserTokenId()))
                .map(ut -> String.format("%s offline %s, wakeAttempts=%s, lastSeen=%s", WsMon.dev(ut),
                        WsMon.ago(ut.getConnectionLostAt(), nowLocal), ut.getNotifyAttempts(), ut.getLastSeenAt()))
                .collect(Collectors.joining("; "));

        WsMon.LOG.info("[WSMON] STATUS sesje={} [{}] | offline: [{}]", stats.size(), live, offline);
    }
}
