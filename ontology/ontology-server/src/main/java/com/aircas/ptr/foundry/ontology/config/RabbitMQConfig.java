package com.aircas.ptr.foundry.ontology.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {


    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue}")
    private String queue;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    // 声明 Topic Exchange，默认持久化+不删除
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(exchange);
    }

    // 声明队列,默认持久化+不删除
    @Bean
    public Queue entitySchemaQueue() {
        return new Queue(queue);
    }

    // 绑定队列到交换机，使用 routing key
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(entitySchemaQueue())
                .to(topicExchange())
                .with(routingKey);
    }
}
