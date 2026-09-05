package pl.kmalysiak.notificator.service.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.dto.HaierHeatPumpDto;
import pl.kmalysiak.notificator.dto.WsEnvelopeDeviceStatus;
import pl.kmalysiak.notificator.service.HaierHeatPumpService;

import java.io.IOException;
import java.util.Collection;

/**
 * Co {@code ws.haier.broadcast.interval} ms wysyła aktualny snapshot pompy Haier
 * (patrz {@link HaierHeatPumpService#getHaierData()}) do wszystkich aktywnych sesji WS,
 * zapakowany w {@link WsEnvelopeDeviceStatus} jako {@code msgType="haier_snapshot"}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HaierSnapshotBroadcaster {

    private static final String MSG_TYPE = "haier_snapshot";

    private final SessionRegistry sessionRegistry;
    private final NotificationHandler notificationHandler;
    private final HaierHeatPumpService haierHeatPumpService;

    @Scheduled(fixedRateString = "${ws.haier.broadcast.interval:5000}")
    public void broadcastSnapshot() {
        Collection<WebSocketSession> sessions = sessionRegistry.getAll();
        if (sessions.isEmpty()) return;

        HaierHeatPumpDto dto;
        try {
            dto = haierHeatPumpService.getHaierData();
        } catch (Exception e) {
            log.error("Nie udało się zbudować snapshotu Haier - pomijam ten cykl broadcastu", e);
            return;
        }

        WsEnvelopeDeviceStatus<HaierHeatPumpDto> envelope = new WsEnvelopeDeviceStatus<>(MSG_TYPE, dto);

        int sent = 0;
        for (WebSocketSession session : sessions) {
            try {
                notificationHandler.sendMessage(session, envelope);
                sent++;
            } catch (IOException e) {
                log.error("Błąd broadcastu snapshotu Haier do session={}", session.getId(), e);
            }
        }

        log.debug("Wysłano snapshot Haier do {}/{} klientów", sent, sessions.size());
    }
}
