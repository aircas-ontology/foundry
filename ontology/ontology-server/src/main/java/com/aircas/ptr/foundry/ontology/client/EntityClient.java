package com.aircas.ptr.foundry.ontology.client;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.common.param.*;
import com.aircas.ptr.foundry.ontology.common.vo.EntityDetailVO;
import com.aircas.ptr.foundry.ontology.common.vo.EntityRelationVO;
import com.aircas.ptr.foundry.ontology.common.vo.EntityVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;


@Slf4j
@Component
public class EntityClient {

    @Value("${client.entity.url-prefix}")
    private String urlPrefix;

    @Value("${client.entity.create-table}")
    private String createTable;

    @Value("${client.entity.copy-table}")
    private String copyTable;

    @Value("${client.entity.delete-table}")
    private String deleteTable;

    @Value("${client.entity.node-relation}")
    private String nodeRelation;

    @Value("${client.entity.query-node-relation}")
    private String queryNodeRelation;

    @Value("${client.entity.query-record}")
    private String queryRecords;

    @Value("${client.entity.query-record-detail}")
    private String queryRecordDetail;

    @Value("${client.entity.column}")
    private String columnUrl;

    public void createColumns(EntityColumnCreateParam param) {
        HttpUtil.postJson(urlPrefix + columnUrl, new HashMap<>(), param, new TypeReference<RestResult>() {
        });
    }


    public List<EntityRelationVO> queryRelation(EntityRelationQueryParam param) {
        var result = HttpUtil.postJson(urlPrefix + queryNodeRelation, new HashMap<>(), param, new TypeReference<RestResult<List<EntityRelationVO>>>() {
        });
        return result.getData();
    }


    public List<EntityDetailVO> queryRecordDetail(EntityDetailQueryParam param) {
        String url = urlPrefix + queryRecordDetail;
        var res = HttpUtil.postJson(url, new HashMap<>(), param, new TypeReference<RestResult<List<EntityDetailVO>>>() {
        });
        return res.getData();
    }

    public Page<EntityVO> queryRecords(String tableName, Integer pageNum, Integer pageSize) {
        var paramMap = new HashMap<String, String>();
        paramMap.put("tableName", tableName);
        paramMap.put("pageNum", String.valueOf(pageNum));
        paramMap.put("pageSize", String.valueOf(pageSize));
        var res = HttpUtil.get(urlPrefix + queryRecords, paramMap, new TypeReference<RestResult<Page<EntityVO>>>() {
        });
        return res.getData();
    }


    public void createEntityRelation(EntityRelationCreateParam param) {
        String url = urlPrefix + nodeRelation;
        HttpUtil.postJson(url, Maps.newHashMap(), param, new TypeReference<RestResult>() {
        });
    }

    public void deleteTableAndEntities(String tableName) {
        String url = urlPrefix + deleteTable;
        HttpUtil.deletePathVariable(url, Lists.newArrayList(tableName), new TypeReference<RestResult>() {
        });
    }

    public void createTableAndEntities(EntityCreateParam param) {
        String url = urlPrefix + createTable;
        HttpUtil.postJson(url, Maps.newHashMap(), param, new TypeReference<RestResult>() {
        });
    }

    public void copyTableAndEntities(EntityCopyParam param) {
        String url = urlPrefix + copyTable;
        HttpUtil.postJson(url, Maps.newHashMap(), param, new TypeReference<RestResult>() {
        });
    }


}
