package io.transwarp.studio.dm.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

/**
 * mq发送终端
 */
@Component
@Slf4j
public class RocketMQClient {

    @Resource
    private RocketMQTemplate rocketMQTemplate;


    /**
     * 向mq发送一个topic的消息
     */
    public void send(String topic, String tag, Object messageObject) {

        if (StringUtils.isNotBlank(tag)) {
            rocketMQTemplate.convertAndSend(String.format("%s:%s", topic, tag), messageObject);
        } else {
            rocketMQTemplate.convertAndSend(topic, messageObject);
        }

    }



    /**
     * 向mq发送一个topic的消息
     */
    public void send(String topic, Object messageObject) {
        rocketMQTemplate.convertAndSend(topic, messageObject);
    }

}