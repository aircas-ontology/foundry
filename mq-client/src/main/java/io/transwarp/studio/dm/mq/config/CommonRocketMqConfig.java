package io.transwarp.studio.dm.mq.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * RocketMQ的配置
 * 配置特定的MQ实例，用于给该MQ实例中推送消息
 */
//@Slf4j
//@Data
//@ConditionalOnExpression("${common.rocketmq.enable:false}")
//@Configuration
public class CommonRocketMqConfig {}
//
//    @Value("${common.rocketmq.name-server}")
//    private String nameServers;
//    @Value("${common.rocketmq.producer.group:GID_SGXT_GROUP}")
//    private String producerGroup;
//    @Value("${common.rocketmq.producer.retry-times-when-send-failed:3}")
//    private Integer retryTimesWhenSendFailed;
//    //@Value("${rocketmq.consumer.group}")
//    //private String consumerGroup;
//    @Value("${common.rocketmq.producer.access-key:}")
//    private String producerAccessKey;
//    @Value("${common.rocketmq.producer.secret-key:}")
//    private String producerSecretKey;
//
//    @Bean("commonProducer")
//    public Producer commonProducer() {
//        Properties properties = new Properties();
//        properties.setProperty(PropertyKeyConst.GROUP_ID, producerGroup);
//        properties.setProperty(PropertyKeyConst.AccessKey, producerAccessKey);
//        properties.setProperty(PropertyKeyConst.SecretKey, producerSecretKey);
//        properties.setProperty(PropertyKeyConst.SendMsgTimeoutMillis, "3000");
//        properties.setProperty(PropertyKeyConst.NAMESRV_ADDR, nameServers);
//
//        Producer producer = ONSFactory.createProducer(properties);
//        producer.start();
//        return producer;
//    }
//
//
//    public static void main(String[] args) throws MQClientException {
//        DefaultMQProducer producer = new DefaultMQProducer("GID_SGXT_GROUP");
//        producer.setNamesrvAddr("30.195.249.163:9876");
//        producer.start();
//
////        DefaultLitePullConsumer consumer = new DefaultLitePullConsumer("GID_SGXT_GROUP");
////        consumer.setNamesrvAddr("30.195.249.163:9876");
//
//        RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
//        rocketMQTemplate.setProducer(producer);
////        rocketMQTemplate.setConsumer(consumer);
//        rocketMQTemplate.convertAndSend("test_topic:123", "测试数据");
//        producer.shutdown();
//    }
//
//    public static void main3(String[] args) {
//        Properties properties = new Properties();
//        properties.setProperty(PropertyKeyConst.GROUP_ID, "GID_SGXT_GROUP");
//        properties.setProperty(PropertyKeyConst.AccessKey, "VvNjRiv3GAGxB9h8");
//        properties.setProperty(PropertyKeyConst.SecretKey, "aeAj7zwueovGFWKufyHEEtCuqKLfMQ");
//        properties.setProperty(PropertyKeyConst.SendMsgTimeoutMillis, "3000");
//        properties.setProperty(PropertyKeyConst.NAMESRV_ADDR, "30.195.249.163:9876");
//
//        Producer producer = ONSFactory.createProducer(properties);
//        producer.start();
//        Message message = new Message();
//        message.setTopic("test_topic");
//        message.setTag("test1");
//        message.setBody("测试数据".getBytes(StandardCharsets.UTF_8));
//
//        producer.sendAsync(message, new SendCallback() {
//            @Override
//            public void onSuccess(SendResult sendResult) {
//            }
//
//            @Override
//            public void onException(OnExceptionContext onExceptionContext) {
//                log.error("rocketmq message send error: topic={},messageId={}",
//                        onExceptionContext.getTopic(),
//                        onExceptionContext.getMessageId(),
//                        onExceptionContext.getException());
//            }
//        });
//        producer.shutdown();
//    }
//}
