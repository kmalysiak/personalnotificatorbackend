package pl.kmalysiak.notificator.rabbit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class QueueConsumer {

    @RabbitListener(
            queues = "${queue.test}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void consume(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("Received body: " + body);
    }
}
