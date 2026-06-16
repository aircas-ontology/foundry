package com.aircas.ptr.foundry.ontology.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RabbitMQListener {

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void receiveMessage(String message) {
        log.info("接收到rmq消息: {}", message);
    }
}
