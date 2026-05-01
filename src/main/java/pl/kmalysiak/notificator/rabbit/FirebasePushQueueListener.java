package pl.kmalysiak.notificator.rabbit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pl.kmalysiak.notificator.controller.PushNotificationController;
import pl.kmalysiak.notificator.rabbit.model.SingleUidNotification;

@Component
@Slf4j
@RequiredArgsConstructor
public class FirebasePushQueueListener {

    private final PushNotificationController pushNotificationController;

    @RabbitListener(queues = Names.FIREBASE_PUSH_QUEUE_SINGLE)
    public void handleMessage(SingleUidNotification notification) {
        log.info("Pushing notification to uid:{}, msg:{} NOT IMPLEMENTED, FIX ME!", notification.getUid(), notification.getMsg());



//        try {                                                                                                                                                                                                                                                                                                                                                                                                                                 SendRequest sr = new SendRequest(...);
//            //log.info("Pushing notification to uid:{}, msg:{}", notification.getUid(), notification.getMsg());
//
//            //SendRequest sr = new SendRequest(notification.getUid(), new NotificationData("zalogowano", "push", notification.getMsg(), TimeZoneUtils.epochSecondsNowStr()));
//            //pushNotificationController.send(sr);
//        } catch (FcmException e) {
//            if (e.getErrorCode() == FcmException.ErrorCode.INVALID_TOKEN) {
//                log.warn("Invalid FCM token for uid:{}, pomijam wiadomość", notification.getUid());
//                // nie rzucamy → Spring ACKuje, wiadomość znika z kolejki
//            } else {
//                // błąd przejściowy — możemy rzucić żeby Rabbit requeued
//                throw e;
//            }
//        } catch (Exception e) {
//            log.error("Błąd push dla uid:{}: {}", notification.getUid(), e.getMessage(), e);
//            // nie rzucamy → ACK, nie blockujemy kolejki
//        }

    }
}
