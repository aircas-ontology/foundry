package com.aircas.ptr.foundry.rule.executor.service.impl;

import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.model.po.ActionHandleCommitFlashMemory;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.dynamics.CheckRuleStatus;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import com.aircas.ptr.foundry.rule.executor.service.ICheckService;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.utils.CompareUtil;
import com.aircas.ptr.foundry.rule.executor.utils.LocalCacheUtils;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.bridge.MessageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CheckServiceImpl implements ICheckService {

    @Autowired
    private IOntologyServer ontologyServer;

    @Autowired
    private SnowflakeIdUtil snowflakeIdUtil;

    @Override
    public List<OntologyMeta> getMeta(String table) {
        if (LocalCacheUtils.ontologyMetaMap == null) {
            return null;
        }
        return LocalCacheUtils.ontologyMetaMap.get(table);
    }

    public List<OntologyProperty> getPropertyList(String ontologyUniqueIdentifier) {
        if (LocalCacheUtils.ontologyPropertyMap == null) {
            return null;
        }
        return LocalCacheUtils.ontologyPropertyMap.get(ontologyUniqueIdentifier);
    }

    @Override
    public CheckRuleStatus checkRule(CheckDataVO vo, ActionHandleRule rule, List<OntologyProperty> propertyList) {

        Map<String, Object> dataMap = vo.getData();
        String primaryKeys = rule.getObjectPrimaryKey();
        // 1 and  2 or
        int connectType = rule.getRuleConnectType();
        JSONArray array = JSONArray.parseArray(rule.getRules());
        boolean primaryCheck = false;
        // 数据主键信息（如果配置了要校验主键）
        String dataKeyValue = "action_data_default_primary_value";
        if (primaryKeys.length() > 0) {
            String[] primaryValus = primaryKeys.split(",");
            OntologyProperty currentProperty = null;
            for (OntologyProperty property : propertyList) {
                if (property.getIsPrimaryKey() == 1) {
                    currentProperty = property;
                    break;
                }
            }
            if (currentProperty != null) {
                dataKeyValue = dataMap.get(currentProperty.getDatasourceColumnName()) + "";
                for (String primaryValue : primaryValus) {
                    if (dataKeyValue.equals(primaryValue)) {
                        primaryCheck = true;
                    }
                }
            }
        } else {
            primaryCheck = true;
        }

        if (!primaryCheck) {
            System.out.println("规则校验未通过:观察数据主键不满足条件");
            return new CheckRuleStatus(false, dataKeyValue, null);
        }

        // 拿到上次缓存的数据
        ActionHandleCommitFlashMemory lastFlashMermory = ontologyServer.getActionDataLog(rule.getActionId(), dataKeyValue);
        Map<String, String> mermoryMap = null;
        try {
            if (lastFlashMermory != null && lastFlashMermory.getData() != null)
                mermoryMap = JSONObject.parseObject(lastFlashMermory.getData(), Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int flagCount = 0;
        // 在这里开始执行规则
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            String key = item.getString("columnName");
            String value = item.getString("columnValue");
            String condition = item.getString("condition");
            String dataMapValue = dataMap.get(key) + "";
            if (CompareUtil.compare(key, condition, dataMapValue, value, mermoryMap)) {
                flagCount++;
                if (connectType == 2) {
                    return new CheckRuleStatus(true, dataKeyValue, lastFlashMermory != null ? lastFlashMermory.getId().toString() : null);
                }
            } else {
                if (connectType == 1) {
                    return new CheckRuleStatus(false, dataKeyValue, lastFlashMermory != null ? lastFlashMermory.getId().toString() : null);
                }
            }
//            if (condition.equals("gt")){
//                CompareUtil.gt(dataMapValue,value,lastFlashMermory==null?null:JSONObject.)
//                if (Double.parseDouble(dataMapValue)>Double.parseDouble(value)){
//                    flagCount++;
//                    if (connectType==2){
//                        return true;
//                    }
//                }else{
//                    if (connectType==1){
//                        return false;
//                    }
//                }
//            }else if (condition.equals("lt")){
//                if (Double.parseDouble(dataMapValue)<Double.parseDouble(value)){
//                    flagCount++;
//                    if (connectType==2){
//                        return true;
//                    }
//                }else{
//                    if (connectType==1){
//                        return false;
//                    }
//                }
//            }else if (condition.equals("eq")){
//                if (!dataMapValue.equals(value)){
//                    flagCount++;
//                    if (connectType==2){
//                        return true;
//                    }
//                }else{
//                    if (connectType==1){
//                        return false;
//                    }
//                }
//            }

        }
        return new CheckRuleStatus(flagCount > 0, dataKeyValue, lastFlashMermory != null ? lastFlashMermory.getId().toString() : null);
    }

    public String getNewRules(CheckDataVO vo, ActionHandleRule rule) {
        Map<String, Object> dataMap = vo.getData();
        JSONArray array = JSONArray.parseArray(rule.getRules());
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            String key = item.getString("columnName");
            String dataMapValue = dataMap.get(key) + "";
            item.put("columnValue", dataMapValue);
        }
        return JSONObject.toJSONString(array);
    }

    public ActionHandleCommitFlashMemory getNewflashmemory(CheckDataVO vo, ActionHandleRule rule, CheckRuleStatus status) {
        ActionHandleCommitFlashMemory memory = new ActionHandleCommitFlashMemory();
        memory.setId(status.getDataLogId() == null ? snowflakeIdUtil.get() : Long.parseLong(status.getDataLogId()));
        memory.setTableName(vo.getTable());
        memory.setActionId(rule.getActionId());
        memory.setPrimaryKey(status.getPrimaryValue());
        memory.setData(JSONObject.toJSONString(vo.getData()));
        log.info("保存当前数据缓存：" + JSONObject.toJSONString(memory));
        return memory;
    }

    @Override
    public Object getPrimaryValue(CheckDataVO vo, List<OntologyProperty> propertyList) {
        Map<String, Object> dataMap = vo.getData();
        OntologyProperty currentProperty = null;
        for (OntologyProperty property : propertyList) {
            if (property.getIsPrimaryKey() == 1) {
                currentProperty = property;
                break;
            }
        }
        if (currentProperty != null) {
            return dataMap.get(currentProperty.getDatasourceColumnName());
        }
        return null;
    }
}
