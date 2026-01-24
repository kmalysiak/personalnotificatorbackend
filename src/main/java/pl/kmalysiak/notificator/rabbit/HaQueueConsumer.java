package pl.kmalysiak.notificator.rabbit;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.dto.HaEntityDto;
import pl.kmalysiak.notificator.rabbit.model.EventNotification;
import pl.kmalysiak.notificator.service.HaEventService;
import pl.kmalysiak.notificator.service.NotificationManager;
import pl.kmalysiak.notificator.service.NotificationMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class HaQueueConsumer {
    private final HaEventService rks;
    private final NotificationManager notificationManager;
    private final NotificationMapper notificationCalculator;

    @RabbitListener(
            queues = "${queue.ha}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void consume(Message message) {
        HaEntityDto dto = rks.updateHaEntityStateOnHaEvent(EventNotification.fromMessage(message));
        if (dto.shouldSendMobileNotification()) {
            Pair<String, String> typeAndBody = notificationCalculator.getNotificationTypeAndStatus(dto);
            if(!"##no_notification##".equals(typeAndBody.getSecond())) {
                if ("all".equalsIgnoreCase(dto.getRecipientEmails()))
                    notificationManager.sendMsgToAllUsers(typeAndBody.getFirst(), typeAndBody.getSecond());
                else if (!"none".equals(dto.getRecipientEmails()) && !dto.getRecipientEmailsAsSet().isEmpty()) {
                    notificationManager.sendMsgToUsers(dto.getRecipientEmailsAsSet(), typeAndBody.getFirst(), typeAndBody.getSecond());
                }
            }
        }
    }
}
