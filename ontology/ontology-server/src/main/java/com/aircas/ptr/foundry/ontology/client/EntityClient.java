package com.aircas.ptr.foundry.ontology.client;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.client.param.EntityCreateParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class EntityClient {

    @Value("${client.entity.url-prefix}")
    private String urlPrefix;

    @Value("${client.entity.create-table}")
    private String createTable;

    private final ObjectMapper objectMapper = new ObjectMapper();


    public void createTableAndEntities(EntityCreateParam param) {
        String url = urlPrefix + createTable;
        String jsonStr = "";
        try {
            jsonStr = objectMapper.writeValueAsString(param);
        } catch (JsonProcessingException e) {
            log.error("json序列化失败",e);
        }
        HttpUtil.postJson(url, Maps.newHashMap(), jsonStr, new TypeReference<RestResult>() {
        });
    }


}
