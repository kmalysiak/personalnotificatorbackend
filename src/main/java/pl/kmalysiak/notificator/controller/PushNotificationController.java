package pl.kmalysiak.notificator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import pl.kmalysiak.notificator.model.SendRequest;
import pl.kmalysiak.notificator.service.NotificationManager;
import pl.kmalysiak.notificator.service.NotificationService;
import pl.kmalysiak.notificator.service.UserService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/push-api")
@RequiredArgsConstructor
@Slf4j
public class PushNotificationController {

    private final UserService userService;
    private final NotificationService notificationService;

    private final NotificationManager notificationManager;

    @PostMapping("/send")
    public void send(@RequestBody SendRequest request) {
        notificationManager.sendMsgToUser(request.uid(), request.type(), request.msg());
    }



    @PostMapping("/sendAll")
    public void sendAll(@RequestBody SendRequest request) {
        notificationManager.sendMsgToAllUsers(request.type(), request.msg());
    }



    @GetMapping("/heartbeat")
    public String heartbeat() {
        return "Behold! So... The essence of the world is: " + LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }
}
