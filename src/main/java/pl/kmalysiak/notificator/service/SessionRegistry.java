package pl.kmalysiak.notificator.service;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final BiMap<String, String> uidToSessionId = Maps.synchronizedBiMap(HashBiMap.create());

    public void register(WebSocketSession session, String uid) {
        String sessionId = session.getId();

        // Usuń starą sesję jeśli uid już istnieje
        String oldSessionId = uidToSessionId.forcePut(uid, sessionId);
        if (oldSessionId != null) {
            sessions.remove(oldSessionId);
        }

        sessions.put(sessionId, session);
    }

    public void unregister(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        uidToSessionId.inverse().remove(sessionId);
    }

    public Collection<WebSocketSession> getAll() {
        return sessions.values();
    }

    public String getUid(WebSocketSession session) {
        return uidToSessionId.inverse().get(session.getId());
    }
}