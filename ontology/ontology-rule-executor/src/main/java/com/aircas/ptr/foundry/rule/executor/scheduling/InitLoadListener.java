package com.aircas.ptr.foundry.rule.executor.scheduling;

import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.utils.LocalCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InitLoadListener implements ApplicationRunner {

    @Autowired
    private IOntologyServer ontologyServer;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=============== 初始化本地缓存数据 ============");
        this.loadOntologyMeta();
        this.loadOntologyProperty();
    }

    private void loadOntologyProperty(){
        log.info("========= 初始化实体属性信息 ===========");
        List<OntologyProperty> propertyList = ontologyServer.getAllOntologProprety();
        Map<String,List<OntologyProperty>> map = propertyList.stream().collect(Collectors.groupingBy(OntologyProperty::getOntologyUniqueIdentifier));
        LocalCacheUtils.ontologyPropertyMap = map;
    }


    private void loadOntologyMeta(){
        log.info("========= 初始化实体信息 ===========");
        List<OntologyMeta> metaList =  ontologyServer.getAllOntologies();
//        Map<String,OntologyMeta> datamap = metaList.stream().collect(Collectors.toMap(OntologyMeta::getBackingDatasourceId, Function.identity()));
        Map<String,List<OntologyMeta>> datamap = metaList.stream().collect(Collectors.groupingBy(OntologyMeta::getBackingDatasourceId));
        LocalCacheUtils.ontologyMetaMap = datamap;
    }
}
