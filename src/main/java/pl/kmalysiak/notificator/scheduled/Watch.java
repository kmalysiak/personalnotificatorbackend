package pl.kmalysiak.notificator.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.controller.PushNotificationController;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.SendRequest;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.service.UserTokenService;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "jobs.watch.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class Watch {
    private final UserTokenService userTokenService;
    private final PushNotificationController pushNotificationController;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${admin.email:not_specified}")
    private String adminEmail;

    @Scheduled(cron = "0 */5 * * * *")
    public void checkTime() {
        String ldtNow = TimeZoneUtils.getLocalDateTimeNow().format(formatter);
        log.info("RunningCron:{}", ldtNow);

        if ("note_specified".equals(adminEmail)) {
            log.warn("Admin email not specified");
            return;
        }


        userTokenService.getUserTokensForEmail(adminEmail)
                .stream()
                .map(UserToken::getUserFirebaseGuid)
                .distinct()
                .forEach(guid ->
                        pushNotificationController.send(new SendRequest(guid, new NotificationData("kron", "cron", "Kron przeszedł", TimeZoneUtils.epochSecondsNow()))));
    }
}
