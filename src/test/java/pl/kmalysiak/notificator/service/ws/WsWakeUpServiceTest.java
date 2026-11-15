package pl.kmalysiak.notificator.service.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.model.UserTokenId;
import pl.kmalysiak.notificator.repo.UserTokenRepository;
import pl.kmalysiak.notificator.service.NotificationLogService;
import pl.kmalysiak.notificator.service.NotificationManager;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WsWakeUpServiceTest {

    private final UserTokenRepository repo = mock(UserTokenRepository.class);
    private final NotificationManager notificationManager = mock(NotificationManager.class);
    private final NotificationLogService logService = mock(NotificationLogService.class);
    private final WsWakeUpService service = new WsWakeUpService(repo, notificationManager, logService, new SessionRegistry());

    private UserToken offline;

    @BeforeEach
    void setUp() {
        offline = new UserToken(new UserTokenId("guid", "fcm-token-abcdef"), "a@b.pl", LocalDateTime.now().minusDays(1));
        offline.setNotifyAttempts(0);
        offline.setConnectionLostAt(TimeZoneUtils.getLocalDateTimeNow().minusHours(1));
        when(repo.findAll()).thenReturn(List.of(offline));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void firstNotificationWithoutCampaignSendsPushImmediately() throws Exception {
        service.onNewNotification();

        verify(notificationManager, times(1)).sendWakeUpMsgToFirebaseToken(eq("fcm-token-abcdef"), any());
        assertEquals(1, offline.getNotifyAttempts());
        assertNotNull(offline.getFirstFailedAt());
    }

    @Test
    void burstOfNotificationsSendsSinglePush() throws Exception {
        for (int i = 0; i < 5; i++) {
            service.onNewNotification();
        }

        verify(notificationManager, times(1)).sendWakeUpMsgToFirebaseToken(any(), any());
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        assertFalse(offline.getNextRetryAt().isAfter(now.plusSeconds(31)), "następna próba najwyżej za ~30 s");
    }

    @Test
    void activeCampaignWithDistantRetryIsPulledInWithoutPush() throws Exception {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        offline.setFirstFailedAt(now.minusMinutes(10));
        offline.setNotifyAttempts(5);
        offline.setNextRetryAt(now.plusMinutes(4));

        service.onNewNotification();

        verify(notificationManager, never()).sendWakeUpMsgToFirebaseToken(any(), any());
        assertFalse(offline.getNextRetryAt().isAfter(now.plusSeconds(31)));
        assertEquals(5, offline.getNotifyAttempts(), "kampania trwa dalej");
    }

    @Test
    void campaignAboutToExpireIsRestartedSoNewNotificationIsNotLost() throws Exception {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        offline.setFirstFailedAt(now.minusMinutes(29).minusSeconds(50));
        offline.setNotifyAttempts(9);
        offline.setNextRetryAt(now.plusMinutes(5));

        service.onNewNotification();

        verify(notificationManager, never()).sendWakeUpMsgToFirebaseToken(any(), any());
        assertEquals(0, offline.getNotifyAttempts());
        assertTrue(offline.getFirstFailedAt().isAfter(now.minusSeconds(5)), "nowa kampania od teraz");
        assertEquals(1, offline.getFailedCampaigns(), "wygasła kampania liczy się jako nieudana");
    }

    @Test
    void campaignExpiringAfterPreviousFailureEntersDormantInsteadOfRestart() throws Exception {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        LocalDateTime campaignStart = now.minusMinutes(29).minusSeconds(50);
        offline.setFirstFailedAt(campaignStart);
        offline.setNotifyAttempts(9);
        offline.setNextRetryAt(now.plusMinutes(5));
        offline.setFailedCampaigns(1);

        service.onNewNotification();

        verify(notificationManager, never()).sendWakeUpMsgToFirebaseToken(any(), any());
        assertEquals(2, offline.getFailedCampaigns());
        assertEquals(campaignStart, offline.getFirstFailedAt(), "bez nowej kampanii");
        assertTrue(WsWakeUpService.isDormant(offline));
    }

    @Test
    void secondGiveUpInSchedulerEntersDormant() throws Exception {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        offline.setFirstFailedAt(now.minusMinutes(28));
        offline.setNotifyAttempts(8);
        offline.setNextRetryAt(now.minusSeconds(1));
        offline.setFailedCampaigns(1);
        when(repo.findDisconnectedForWakeUp(any(), any())).thenReturn(List.of(offline));
        when(logService.hasPendingSince(any(), anyLong())).thenReturn(true);

        service.wakeUpDroppedClients();

        verify(notificationManager, times(1)).sendWakeUpMsgToFirebaseToken(any(), any());
        assertEquals(2, offline.getFailedCampaigns());
        assertTrue(WsWakeUpService.isDormant(offline));
    }

    @Test
    void dormantDeviceGetsAtMostOnePushPerInterval() throws Exception {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        offline.setFirstFailedAt(now.minusHours(3));
        offline.setNotifyAttempts(20);
        offline.setNextRetryAt(now.minusMinutes(1));
        offline.setFailedCampaigns(3);

        for (int i = 0; i < 5; i++) {
            service.onNewNotification();
        }

        verify(notificationManager, times(1)).sendWakeUpMsgToFirebaseToken(any(), any());
        assertTrue(offline.getNextRetryAt().isAfter(now.plus(WsWakeUpService.DORMANT_INTERVAL).minusSeconds(5)));
        assertEquals(now.minusHours(3), offline.getFirstFailedAt(), "bez nowej kampanii");
    }

    @Test
    void dormantDeviceWithinIntervalGetsNoPush() throws Exception {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        offline.setFirstFailedAt(now.minusHours(3));
        offline.setNextRetryAt(now.plusMinutes(10));
        offline.setFailedCampaigns(2);

        service.onNewNotification();

        verify(notificationManager, never()).sendWakeUpMsgToFirebaseToken(any(), any());
    }

    @Test
    void schedulerSkipsDormantDevice() throws Exception {
        offline.setFailedCampaigns(2);
        when(repo.findDisconnectedForWakeUp(any(), any())).thenReturn(List.of(offline));
        when(logService.hasPendingSince(any(), anyLong())).thenReturn(true);

        service.wakeUpDroppedClients();

        verify(notificationManager, never()).sendWakeUpMsgToFirebaseToken(any(), any());
    }

    @Test
    void givenUpCampaignStartsAnewWithImmediatePush() throws Exception {
        LocalDateTime now = TimeZoneUtils.getLocalDateTimeNow();
        offline.setFirstFailedAt(now.minusHours(2));
        offline.setNotifyAttempts(9);
        offline.setNextRetryAt(now.minusHours(1));

        service.onNewNotification();

        verify(notificationManager, times(1)).sendWakeUpMsgToFirebaseToken(any(), any());
        assertEquals(1, offline.getNotifyAttempts());
    }
}
