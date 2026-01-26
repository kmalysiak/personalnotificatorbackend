package pl.kmalysiak.notificator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.SendRequest;
import pl.kmalysiak.notificator.service.NotificationManager;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.ZoneOffset;

@RestController
@RequestMapping("/push-api")
@RequiredArgsConstructor
@Slf4j
public class PushNotificationController {

    private final NotificationManager notificationManager;

    @PostMapping("/send")
    public void send(@RequestBody SendRequest request) {
        notificationManager.sendMsgToUser(request.uid(), request.nd());
    }


    @PostMapping("/sendAll")
    public void sendAll(@RequestBody NotificationData nd) {
        notificationManager.sendMsgToAllUsers(nd);
    }

    @GetMapping("/heartbeat")
    public String heartbeat() {
        return "Behold! So... The essence of the world is: " + TimeZoneUtils.getLocalDateTimeNow().toEpochSecond(ZoneOffset.UTC);
    }
}
