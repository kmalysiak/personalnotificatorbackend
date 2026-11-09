package pl.kmalysiak.notificator.service;

import org.junit.jupiter.api.Test;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.model.UserTokenId;
import pl.kmalysiak.notificator.repo.UserTokenRepository;
import pl.kmalysiak.notificator.service.ws.SessionRegistry;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserTokenServiceTest {

    private final UserTokenRepository repo = mock(UserTokenRepository.class);
    private final UserTokenService service = new UserTokenService(repo, new SessionRegistry());
    private final UserTokenId id = new UserTokenId("guid", "fcm-token-123456");

    private UserToken offlineToken() {
        UserToken ut = new UserToken(id, "a@b.pl", LocalDateTime.now().minusDays(1));
        ut.setConnectionLostAt(LocalDateTime.now().minusMinutes(10));
        ut.setNotifyAttempts(2);
        ut.setFirstFailedAt(LocalDateTime.now().minusMinutes(5));
        ut.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
        when(repo.findById(id)).thenReturn(Optional.of(ut));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return ut;
    }

    @Test
    void restRegisterKeepsConnectionLostStateSoWakeUpContinues() {
        UserToken ut = offlineToken();

        service.onUserFcmTokenAuthorised(id, "new@b.pl", UserTokenService.AuthSource.REST_REGISTER);

        assertEquals("new@b.pl", ut.getEmail());
        assertNotNull(ut.getConnectionLostAt(), "REST register nie może udawać, że urządzenie jest połączone");
        assertEquals(2, ut.getNotifyAttempts());
        assertNotNull(ut.getFirstFailedAt());
        assertNotNull(ut.getNextRetryAt());
    }

    @Test
    void wsHandshakeResetsConnectionStateAndWakeCampaign() {
        UserToken ut = offlineToken();

        service.onUserFcmTokenAuthorised(id, "a@b.pl", UserTokenService.AuthSource.WS_HANDSHAKE);

        assertNull(ut.getConnectionLostAt());
        assertEquals(0, ut.getNotifyAttempts());
        assertNull(ut.getFirstFailedAt());
        assertNull(ut.getNextRetryAt());
    }
}
