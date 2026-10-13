package com.aircas.ptr.foundry.ontology.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "foundry.integration.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQListener {

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void receiveMessage(String message) {
        log.info("接收到rmq消息: {}", message);
    }
}
