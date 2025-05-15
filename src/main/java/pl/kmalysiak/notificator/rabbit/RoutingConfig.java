package pl.kmalysiak.notificator.rabbit;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static pl.kmalysiak.notificator.rabbit.Names.*;

@Configuration
public class RoutingConfig {

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(FIREBASE_PUSH_EXCHANGE, true, false); // durable, non-auto-delete
    }

    @Bean(name = FIREBASE_PUSH_QUEUE_ALL)
    public Queue firebaseQueueAll() {
        return new Queue(FIREBASE_PUSH_QUEUE_ALL, true, false, false);
    }

    @Bean(name = FIREBASE_PUSH_QUEUE_SINGLE)
    public Queue firebaseQueueSingle() {
        return new Queue(FIREBASE_PUSH_QUEUE_SINGLE, true, false, false);
    }


    @Bean
    public Binding bindingAll(@Qualifier(value = FIREBASE_PUSH_QUEUE_ALL) Queue firebaseQueueAll, DirectExchange exchange) {
        return BindingBuilder.bind(firebaseQueueAll)
                .to(exchange)
                .with(FIREBASE_PUSH_EXCHANGE_KEY_ALL);
    }


    @Bean
    public Binding bindingSingle(@Qualifier(value = FIREBASE_PUSH_QUEUE_SINGLE) Queue firebaseQueueSingle, DirectExchange exchange) {
        return BindingBuilder.bind(firebaseQueueSingle)
                .to(exchange)
                .with(FIREBASE_PUSH_EXCHANGE_KEY_SINGLE);
    }
}
