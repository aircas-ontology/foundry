package io.transwarp.studio.dm.mq.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Properties;

/**
 * RocketMQ的配置
 *
 * @author yibo.tang
 * @date 2021-04-25 14:39:30
 * @since 1.0
 */
@Slf4j
@Data
//@ConditionalOnExpression("${rocketmq.cloud:false}")
@Configuration
public class RocketMqConfig {

    @Value("${rocketmq.name-server}")
    private String nameServers;
    @Value("${rocketmq.producer.group:GID_SGXT_GROUP}")
    private String producerGroup;
    @Value("${rocketmq.producer.retry-times-when-send-failed:3}")
    private Integer retryTimesWhenSendFailed;
    //@Value("${rocketmq.consumer.group}")
    //private String consumerGroup;
    @Value("${rocketmq.producer.access-key:}")
    private String producerAccessKey;
    @Value("${rocketmq.producer.secret-key:}")
    private String producerSecretKey;


}
