package com.aircas.ptr.foundry.rule.executor.scheduling;


import com.aircas.ptr.foundry.common.pg.EventTypeEnum;
import com.aircas.ptr.foundry.common.pg.FieldData;
import com.aircas.ptr.foundry.common.pg.SyncEventContext;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import com.aircas.ptr.foundry.rule.executor.service.IRuleService;
import com.alibaba.fastjson.JSONObject;
import com.github.jsonldjava.utils.Obj;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RocketMQMessageListener(topic = "ONTOLOGY_META_CHANGE_NOTIFY",consumerGroup = "grp_group",messageModel = MessageModel.CLUSTERING)
public class OntologyMetaChangeConsumer implements RocketMQListener<SyncEventContext> {

    @Autowired
    private IRuleService ruleService;

    @Override
    public void onMessage(SyncEventContext context) {
        System.out.println("接收到表："+context.getTable()+"的mq消息是:"+ JSONObject.toJSONString(context));
        if (context.getEventType() == EventTypeEnum.UPDATE){
            List<FieldData> datas = context.getDatas();
            Map<String, Object> dataMap = new HashMap<>();
            for (FieldData data : datas){
                if (data.getName()!=null){
                    dataMap.put(data.getName(),data.getValue());
                }
            }

            CheckDataVO checkDataVO = new CheckDataVO();
            checkDataVO.setDb(context.getSchema());
            checkDataVO.setTable(context.getTable());
            checkDataVO.setData(dataMap);
            try{
                ruleService.checkData(checkDataVO);
            }catch (Exception e){
                System.out.println("接收消息失败:"+e.getMessage());
            }

        }
    }
}
