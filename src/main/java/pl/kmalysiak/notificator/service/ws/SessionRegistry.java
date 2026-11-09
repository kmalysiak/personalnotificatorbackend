package pl.kmalysiak.notificator.service.ws;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import pl.kmalysiak.notificator.model.UserTokenId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rejestr aktywnych sesji WS: jedna sesja na urządzenie ({@link UserTokenId}).
 * <p>
 * Sesje są trzymane jako {@link ConcurrentWebSocketSessionDecorator} — piszą do nich równolegle
 * PingScheduler, HaierSnapshotBroadcaster, tor powiadomień i replay, a goła sesja Springa nie jest
 * bezpieczna dla współbieżnych zapisów. Do wysyłki zawsze używaj sesji z rejestru.
 * <p>
 * Dla każdej sesji pamiętany jest czas ostatniej wiadomości od klienta (pong) — PingScheduler
 * na tej podstawie wykrywa martwe połączenia (gniazdo zerwane po stronie telefonu, a zapis
 * po stronie serwera dalej "przechodzi", np. przez proxy Cloudflare).
 */
@Component
public class SessionRegistry {
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<String, WebSocketSession> sessionsById = new HashMap<>();
    private final Map<String, Instant> lastActivityById = new HashMap<>();
    // liczba wiadomości od klienta od ostatniego podsumowania (WSMON STATUS) — tylko do logów
    private final Map<String, Integer> clientMsgCountById = new HashMap<>();
    private final BiMap<UserTokenId, String> userTokenIdToSessionId = HashBiMap.create();

    /**
     * @return sesja opakowana do bezpiecznej wysyłki + ewentualna poprzednia sesja tego samego
     * urządzenia (wypchnięta z rejestru — do zamknięcia przez wołającego)
     */
    public synchronized Registration register(WebSocketSession rawSession, UserTokenId userTokenId) {
        String sessionId = rawSession.getId();
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(rawSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);

        String oldSessionId = userTokenIdToSessionId.forcePut(userTokenId, sessionId);
        WebSocketSession replaced = null;
        if (oldSessionId != null && !oldSessionId.equals(sessionId)) {
            replaced = sessionsById.remove(oldSessionId);
            lastActivityById.remove(oldSessionId);
            clientMsgCountById.remove(oldSessionId);
        }

        sessionsById.put(sessionId, session);
        lastActivityById.put(sessionId, Instant.now());
        return new Registration(session, replaced);
    }

    /**
     * @return true jeśli to była aktualna sesja swojego urządzenia — tylko wtedy wołający powinien
     * oznaczyć utratę połączenia (spóźnione zamknięcie starej, już zastąpionej sesji jest ignorowane)
     */
    public synchronized boolean unregister(WebSocketSession session) {
        String sessionId = session.getId();
        sessionsById.remove(sessionId);
        lastActivityById.remove(sessionId);
        clientMsgCountById.remove(sessionId);
        return userTokenIdToSessionId.inverse().remove(sessionId) != null;
    }

    public synchronized void unregister(UserTokenId userTokenId) {
        String sessionId = userTokenIdToSessionId.remove(userTokenId);
        if (sessionId != null) {
            sessionsById.remove(sessionId);
            lastActivityById.remove(sessionId);
            clientMsgCountById.remove(sessionId);
        }
    }

    /** Odnotowuje sygnał życia od klienta (dowolna wiadomość klient → serwer). */
    public synchronized void touch(WebSocketSession session) {
        if (lastActivityById.computeIfPresent(session.getId(), (id, prev) -> Instant.now()) != null) {
            clientMsgCountById.merge(session.getId(), 1, Integer::sum);
        }
    }

    /** Stan sesji do okresowego podsumowania; zeruje liczniki wiadomości od klienta. */
    public synchronized List<SessionStat> snapshotAndResetCounters() {
        List<SessionStat> stats = new ArrayList<>();
        Map<String, UserTokenId> bySessionId = userTokenIdToSessionId.inverse();
        for (String sessionId : sessionsById.keySet()) {
            stats.add(new SessionStat(sessionId, bySessionId.get(sessionId), lastActivityById.get(sessionId),
                    clientMsgCountById.getOrDefault(sessionId, 0)));
        }
        clientMsgCountById.clear();
        return stats;
    }

    public record SessionStat(String sessionId, UserTokenId userTokenId, Instant lastActivity, int clientMsgCount) {
    }

    public synchronized Instant getLastActivity(WebSocketSession session) {
        return lastActivityById.get(session.getId());
    }

    /** Sesja z rejestru (opakowana) dla danego id albo null. */
    public synchronized WebSocketSession get(String sessionId) {
        return sessionsById.get(sessionId);
    }

    public synchronized boolean hasLiveSession(UserTokenId userTokenId) {
        return userTokenIdToSessionId.containsKey(userTokenId);
    }

    public synchronized List<WebSocketSession> getAll() {
        return new ArrayList<>(sessionsById.values());
    }

    public record Registration(WebSocketSession session, WebSocketSession replaced) {
    }
}
