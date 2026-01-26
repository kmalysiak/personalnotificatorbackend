package pl.kmalysiak.notificator.rabbit;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.dto.HaEntityDto;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.rabbit.model.EventNotification;
import pl.kmalysiak.notificator.service.HaEventService;
import pl.kmalysiak.notificator.service.NotificationManager;
import pl.kmalysiak.notificator.service.NotificationMapper;

import java.nio.charset.StandardCharsets;

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
        try {
            HaEntityDto dto = rks.updateHaEntityStateOnHaEvent(EventNotification.fromMessage(message));
            if (dto.shouldSendMobileNotification()) {
                NotificationData nd = notificationCalculator.getEntFriendlyNameAndMsgContentForNotification(dto);
                if (!"##no_notification##".equals(nd.msg())) {
                    if ("all".equalsIgnoreCase(dto.getRecipientEmails())) {
                        notificationManager.sendMsgToAllUsers(nd);
                        rks.updateLastNotified(dto.getEntityId());
                    } else if (!"none".equals(dto.getRecipientEmails()) && !dto.getRecipientEmailsAsSet().isEmpty()) {
                        notificationManager.sendMsgToUsers(dto.getRecipientEmailsAsSet(), nd);
                        rks.updateLastNotified(dto.getEntityId());
                    }
                }
            }
        } catch (Exception e){
            log.error("Błąd obsługi komunikatu:" + new String(message.getBody(), StandardCharsets.UTF_8));
        }
    }
}
