package pl.kmalysiak.notificator.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    // sesja → uid użytkownika
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUid = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, String uid) {
        sessions.put(session.getId(), session);
        sessionToUid.put(session.getId(), uid);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        sessionToUid.remove(session.getId());
    }

    public Collection<WebSocketSession> getAll() {
        return sessions.values();
    }

    public String getUid(WebSocketSession session) {
        return sessionToUid.get(session.getId());
    }
}
