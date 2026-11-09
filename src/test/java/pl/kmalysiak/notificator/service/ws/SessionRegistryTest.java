package pl.kmalysiak.notificator.service.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.model.UserTokenId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry();
    private final UserTokenId device = new UserTokenId("guid", "fcm");

    private static WebSocketSession session(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        return s;
    }

    @Test
    void registerReturnsDecoratedSessionAndTracksActivity() {
        WebSocketSession raw = session("s1");

        SessionRegistry.Registration reg = registry.register(raw, device);

        assertNotSame(raw, reg.session());
        assertEquals("s1", reg.session().getId());
        assertNull(reg.replaced());
        assertTrue(registry.hasLiveSession(device));
        assertNotNull(registry.getLastActivity(raw));
        assertEquals(1, registry.getAll().size());
    }

    @Test
    void reconnectReplacesOldSessionOfSameDevice() {
        WebSocketSession old = session("old");
        WebSocketSession fresh = session("new");
        registry.register(old, device);

        SessionRegistry.Registration reg = registry.register(fresh, device);

        assertNotNull(reg.replaced());
        assertEquals("old", reg.replaced().getId());
        assertEquals(1, registry.getAll().size());
        assertEquals("new", registry.getAll().get(0).getId());
    }

    @Test
    void lateCloseOfReplacedSessionIsNotCurrentAndKeepsLiveSession() {
        WebSocketSession old = session("old");
        WebSocketSession fresh = session("new");
        registry.register(old, device);
        registry.register(fresh, device);

        boolean wasCurrent = registry.unregister(old);

        assertFalse(wasCurrent, "spóźnione zamknięcie starej sesji nie może oznaczyć utraty połączenia");
        assertTrue(registry.hasLiveSession(device));
    }

    @Test
    void unregisterOfCurrentSessionReportsLoss() {
        WebSocketSession s = session("s1");
        registry.register(s, device);

        assertTrue(registry.unregister(s));
        assertFalse(registry.hasLiveSession(device));
        assertNull(registry.getLastActivity(s));
        assertFalse(registry.unregister(s), "drugie wywołanie (transport error + closed) to no-op");
    }

    @Test
    void snapshotCountsClientMessagesAndResets() {
        WebSocketSession s = session("s1");
        registry.register(s, device);
        registry.touch(s);
        registry.touch(s);

        var first = registry.snapshotAndResetCounters();
        var second = registry.snapshotAndResetCounters();

        assertEquals(1, first.size());
        assertEquals(device, first.get(0).userTokenId());
        assertEquals(2, first.get(0).clientMsgCount());
        assertEquals(0, second.get(0).clientMsgCount());
    }

    @Test
    void touchRefreshesLastActivity() throws InterruptedException {
        WebSocketSession s = session("s1");
        registry.register(s, device);
        Instant before = registry.getLastActivity(s);

        Thread.sleep(5);
        registry.touch(s);

        assertTrue(registry.getLastActivity(s).isAfter(before));
    }
}
