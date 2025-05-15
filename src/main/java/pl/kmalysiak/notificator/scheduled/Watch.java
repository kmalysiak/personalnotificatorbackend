package pl.kmalysiak.notificator.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.controller.PushNotificationController;
import pl.kmalysiak.notificator.model.SendRequest;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.service.UserService;

import java.time.LocalDateTime;
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
    private final UserService userService;
    private final PushNotificationController pushNotificationController;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${admin.email:not_specified}")
    private  String adminEmail;

    @Scheduled(cron = "0 */5 * * * *")
    public void checkTime() {
        String ldtNow = LocalDateTime.now().format(formatter);
        log.info("RunningCron:{}", ldtNow);

        if("note_specified".equals(adminEmail)) {
            log.warn("Admin email not specified");
            return;
        }


        userService.getUserTokensForLogin(adminEmail)
                .stream()
                .map(UserToken::getGuid)
                .distinct()
                .forEach(guid ->
                        pushNotificationController.send(new SendRequest(guid, ldtNow, "watch")));
    }
}
