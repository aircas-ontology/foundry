package com.aircas.ptr.foundry.rule.executor.scheduling;


import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

@Service
@RocketMQMessageListener(topic = "ONTOLOGY_META_CHANGE_NOTIFY",consumerGroup = "grp_group",messageModel = MessageModel.CLUSTERING)
public class OntologyMetaChangeConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String s) {
        System.out.println("接收到的mq消息是:"+s);
    }
}
