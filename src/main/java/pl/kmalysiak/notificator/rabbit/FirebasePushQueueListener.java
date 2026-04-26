package pl.kmalysiak.notificator.rabbit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pl.kmalysiak.notificator.controller.PushNotificationController;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.SendRequest;
import pl.kmalysiak.notificator.rabbit.model.SingleUidNotification;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class FirebasePushQueueListener {

    private final PushNotificationController pushNotificationController;

    @RabbitListener(queues = Names.FIREBASE_PUSH_QUEUE_SINGLE)
    public void handleMessage(SingleUidNotification notification) {
        log.info("Pushing notification to uid:{}, msg:{}", notification.getUid(), notification.getMsg());
        SendRequest sr = new SendRequest(notification.getUid(), new NotificationData("zalogowano", "push", notification.getMsg(), TimeZoneUtils.epochSecondsNowStr()));
        pushNotificationController.send(sr);
    }
}
