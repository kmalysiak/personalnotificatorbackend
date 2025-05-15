package pl.kmalysiak.notificator.rabbit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.rabbit.model.SingleUidNotification;

import static pl.kmalysiak.notificator.rabbit.Names.FIREBASE_PUSH_EXCHANGE;
import static pl.kmalysiak.notificator.rabbit.Names.FIREBASE_PUSH_EXCHANGE_KEY_SINGLE;

@RequiredArgsConstructor
@Slf4j
@Service
public class RabbitPublisher {
    private final RabbitTemplate rabbitTemplate;

    public void pushUserUpdateNotification(SingleUidNotification notification) {
        rabbitTemplate.convertAndSend(FIREBASE_PUSH_EXCHANGE, FIREBASE_PUSH_EXCHANGE_KEY_SINGLE, notification, msg -> {
            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return msg;
        });
    }
}
