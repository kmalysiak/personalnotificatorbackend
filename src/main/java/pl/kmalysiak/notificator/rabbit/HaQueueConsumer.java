package pl.kmalysiak.notificator.rabbit;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.dto.HaEntityDto;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.rabbit.model.EventNotification;
import pl.kmalysiak.notificator.service.HaEventService;
import pl.kmalysiak.notificator.service.NotificationLogService;
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
    private final NotificationLogService logService;

    @RabbitListener(
            queues = "${queue.ha}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void consume(Message message) {
        try {
            HaEntityDto dto = rks.updateHaEntityStateOnHaEvent(EventNotification.fromMessage(message));
            if (BooleanUtils.isNotTrue(dto.getNotifyMobile())) {
                log.debug("nie wysłano powiadomienia mobile dla encji:{}, rec:{}, timestamp:{} z uwagi na notifyMobile:{}", dto.getEntityId(), dto.getCurrTimestamp(), dto.getCurrReceived(), dto.getNotifyMobile());
                return;
            }

            if (!dto.shouldSendMobileNotification()) {
                log.debug("nie wysłano powiadomienia mobile dla encji:{}, curr:{}, prev:{}, z uwagi na notifyFreq:{}", dto.getEntityId(), dto.getCurrTimestamp(), dto.getPrevTimestamp(), dto.getNotifyFreq());
                return;
            }

            NotificationData nd = notificationCalculator.getEntFriendlyNameAndMsgContentForNotification(dto);
            if ("##no_notification##".equals(nd.msg())) {
                log.debug("nie wysłano powiadomienia mobile dla encji:{}, rec:{}, timestamp:{}, z uwagi na stan:{}", dto.getEntityId(), dto.getCurrTimestamp(), dto.getCurrReceived(), dto.getCurrState());
                return;
            }

            if ("none".equals(dto.getRecipientEmails()) || dto.getRecipientEmailsAsSet().isEmpty()) {
                log.debug("nie wysłano powiadomienia mobile dla encji:{} z uwagi na odbiorcy:{}", dto.getEntityId(), dto.getRecipientEmails());
                return;
            }

            log.info("wysyłka powiadomienia do:{}", dto.getRecipientEmails());
            if ("all".equalsIgnoreCase(dto.getRecipientEmails())) {
                logService.addToLog(nd, "all");
                notificationManager.sendMsgToAllUsersViaWs(nd);
                rks.updateLastNotified(dto.getEntityId());
            } else {
                logService.addToLog(nd, dto.getRecipientEmails());
                notificationManager.sendMsgToUsers(dto.getRecipientEmailsAsSet(), nd);
                rks.updateLastNotified(dto.getEntityId());
            }

        } catch (Exception e) {
            log.error(String.format("Błąd obsługi komunikatu dla body:%s, msg:%s", new String(message.getBody(), StandardCharsets.UTF_8), e.getMessage()));
            log.error("{}", e.getMessage(), e);
        }
    }
}
