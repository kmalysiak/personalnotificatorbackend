package pl.kmalysiak.notificator.service.ws;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.model.UserTokenId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final Map<String, WebSocketSession> wsSessionsByWsSessionId = new ConcurrentHashMap<>();
    private final BiMap<UserTokenId, String> userTokenIdToWsSessionId = Maps.synchronizedBiMap(HashBiMap.create());

    public void register(WebSocketSession session, UserTokenId userTokenId) {
        String sessionId = session.getId();

        // Usuń starą sesję jeśli userTokenId już istnieje
        String oldSessionId = userTokenIdToWsSessionId.forcePut(userTokenId, sessionId);
        if (oldSessionId != null) {
            wsSessionsByWsSessionId.remove(oldSessionId);
        }

        wsSessionsByWsSessionId.put(sessionId, session);
    }

    public void unregister(String uid) {;
        wsSessionsByWsSessionId.remove(uid);
        userTokenIdToWsSessionId.remove(uid);
    }


    public void unregister(WebSocketSession session) {
        String sessionId = session.getId();
        wsSessionsByWsSessionId.remove(sessionId);
        userTokenIdToWsSessionId.inverse().remove(sessionId);
    }

    public List<WebSocketSession> getAll() {
        return new ArrayList<>(wsSessionsByWsSessionId.values());
    }

}