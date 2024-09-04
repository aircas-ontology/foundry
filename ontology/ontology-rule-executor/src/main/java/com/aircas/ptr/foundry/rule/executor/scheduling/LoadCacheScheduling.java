package com.aircas.ptr.foundry.rule.executor.scheduling;

import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.utils.LocalCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LoadCacheScheduling {

    @Autowired
    private IOntologyServer ontologyServer;

    /**
     * 加载实体属性信息，缓存属性组件
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    private void loadOntologyProperty(){
        log.info("========= 更新实体属性信息 ===========");
        List<OntologyProperty> propertyList = ontologyServer.getAllOntologProprety();
        Map<String,List<OntologyProperty>>  map = propertyList.stream().collect(Collectors.groupingBy(OntologyProperty::getOntologyUniqueIdentifier));
        LocalCacheUtils.ontologyPropertyMap = map;
    }

    /**
     * 加载实体属性信息
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    private void loadOntologyMeta(){
        log.info("========= 更新实体信息 ===========");
        List<OntologyMeta> metaList =  ontologyServer.getAllOntologies();
//        Map<String,OntologyMeta> datamap = metaList.stream().collect(Collectors.toMap(OntologyMeta::getBackingDatasourceId, Function.identity()));
        Map<String,List<OntologyMeta>> datamap = metaList.stream().collect(Collectors.groupingBy(OntologyMeta::getBackingDatasourceId));
        LocalCacheUtils.ontologyMetaMap = datamap;
    }


//    @Scheduled(cron = "30 * * 1/1 * ? *")
//    private void loadHandleRule(){
//
//    }
}
