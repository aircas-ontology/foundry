package com.aircas.ptr.foundry.rule.executor.service.impl;

import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import com.aircas.ptr.foundry.rule.executor.service.ICheckService;
import com.aircas.ptr.foundry.rule.executor.utils.LocalCacheUtils;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CheckServiceImpl implements ICheckService {
    @Override
    public List<OntologyMeta> getMeta(String table) {
        if (LocalCacheUtils.ontologyMetaMap == null){
            return null;
        }
        return LocalCacheUtils.ontologyMetaMap.get(table);
    }

    public List<OntologyProperty> getPropertyList(String ontologyUniqueIdentifier){
        if (LocalCacheUtils.ontologyPropertyMap == null){
            return null;
        }
        return LocalCacheUtils.ontologyPropertyMap.get(ontologyUniqueIdentifier);
    }

    @Override
    public boolean checkRule(CheckDataVO vo, ActionHandleRule rule,List<OntologyProperty> propertyList) {
        Map<String,Object> dataMap = vo.getData();
        String primaryKeys = rule.getObjectPrimaryKey();
        // 1 and  2 or
        int connectType = rule.getRuleConnectType();
        JSONArray array = JSONArray.parseArray(rule.getRules());
        boolean primaryCheck = false;
        if (primaryKeys.length()>0){
            String[] primaryValus = primaryKeys.split(",");
            OntologyProperty currentProperty = null;
            for (OntologyProperty property : propertyList){
                if (property.getIsPrimaryKey()==1){
                    currentProperty = property;
                    break;
                }
            }
            if (currentProperty!=null){
                String dataKeyValue = dataMap.get(currentProperty.getDatasourceColumnName())+"";
                for (String primaryValue : primaryValus){
                    if (dataKeyValue.equals(primaryValue)){
                        primaryCheck = true;
                    }
                }
            }
        }else{
            primaryCheck = true;
        }

        if (!primaryCheck){
            System.out.println("规则校验未通过:观察数据主键不满足条件");
            return false;
        }

        int flagCount = 0;
        for (int i=0;i<array.size();i++){
            JSONObject item = array.getJSONObject(i);
            String key = item.getString("columnName");
            String value = item.getString("columnValue");
            String dataMapValue = dataMap.get(key)+"";
            if (!dataMapValue.equals(value)){
                flagCount++;
                if (connectType==2){
                    return true;
                }
            }else{
                if (connectType==1){
                    return false;
                }
            }
        }
        return flagCount>0;
    }

    public String getNewRules(CheckDataVO vo,ActionHandleRule rule){
        Map<String,Object> dataMap = vo.getData();
        JSONArray array = JSONArray.parseArray(rule.getRules());
        for (int i=0;i<array.size();i++){
            JSONObject item = array.getJSONObject(i);
            String key = item.getString("columnName");
            String dataMapValue = dataMap.get(key)+"";
            item.put("columnValue",dataMapValue);
        }
        return JSONObject.toJSONString(array);
    }

    @Override
    public Object getPrimaryValue(CheckDataVO vo, List<OntologyProperty> propertyList) {
        Map<String,Object> dataMap = vo.getData();
        OntologyProperty currentProperty = null;
        for (OntologyProperty property : propertyList){
            if (property.getIsPrimaryKey()==1){
                currentProperty = property;
                break;
            }
        }
        if (currentProperty!=null){
            return dataMap.get(currentProperty.getDatasourceColumnName());
        }
        return null;
    }
}
