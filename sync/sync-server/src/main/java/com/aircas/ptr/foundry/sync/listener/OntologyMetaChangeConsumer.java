package com.aircas.ptr.foundry.sync.listener;


import com.aircas.ptr.foundry.common.pg.SyncEventContext;
import com.alibaba.fastjson.JSON;

import io.transwarp.studio.dm.mq.topic.MetaDataMQTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 *
 */
@Component
@RocketMQMessageListener(
        topic = MetaDataMQTopics.ONTOLOGY_META_CHANGE_NOTIFY,                      // 1.topic：消息的发送者使用同一个topic
        consumerGroup = "grp_sync",               // 2.group：不用和生产者group相同 ( 在RocketMQ中消费者和发送者组没有关系 )
        selectorExpression = "*",                   // 3.tag：设置为 * 时，表示全部。
        messageModel = MessageModel.CLUSTERING    // 4.消费模式：默认 CLUSTERING （ CLUSTERING：负载均衡 ）（ BROADCASTING：广播机制 ）
)
@Slf4j
public class OntologyMetaChangeConsumer implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {


    @Value("${mq.max-reconsume-times:3}")
    Integer maxReconsumeTimes;


    @Override
    public void onMessage(String str) {
        SyncEventContext message = JSON.parseObject(str, SyncEventContext.class);
        log.info("接收到消息:{}", message);

    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);
    }

}
