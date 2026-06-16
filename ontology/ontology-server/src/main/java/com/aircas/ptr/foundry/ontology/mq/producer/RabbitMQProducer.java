package com.aircas.ptr.foundry.ontology.mq.producer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class RabbitMQProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    // 发送消息，使用 topic 路由键
    public void sendMessage(String routingKey, String message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info("rmq消息已发送 - Exchange: {}, RoutingKey: {}, Message:{}", exchange, routingKey, message);
    }
}
