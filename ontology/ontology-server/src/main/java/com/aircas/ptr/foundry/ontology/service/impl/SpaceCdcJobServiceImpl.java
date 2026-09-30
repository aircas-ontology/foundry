 package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologySpaceConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologySpaceDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologySpaceRepository;
import com.aircas.ptr.foundry.ontology.service.SpaceCdcJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class SpaceCdcJobServiceImpl implements SpaceCdcJobService {
    @Autowired
    OntologySpaceRepository ontologySpaceRepository;
    @Autowired
    ElasticsearchOperations elasticsearchOperations;

    @Override
    public void handleCreateCdc(OntologySpace after) {
        EsOntologySpaceDTO esOntologySpaceDTO = OntologySpaceConverter.convert(after);
        ontologySpaceRepository.save(esOntologySpaceDTO);
    }

    @Override
    public boolean handleUpdateCdc(OntologySpace before, OntologySpace after) {
        if (after == null || after.getId() == null) {
            return false;
        }
        // 1. 更新本体空间元数据文档（ontology_space 索引）
        ontologySpaceRepository.save(OntologySpaceConverter.convert(after));

        // 2. 实例文档（ontology_instance 索引）中来源于空间的字段：
        //    space_display_name = 空间 displayName；api_name 作为数据湖 schema（同时构成实例 pk/_id），
        //    业务上不允许修改，故仅需在 displayName 变化时回刷该空间下的全部数据湖实例对象。
        boolean displayNameChanged = before != null
                && !Objects.equals(before.getDisplayName(), after.getDisplayName());
        if (!displayNameChanged) {
            return true;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("spaceDisplayName", after.getDisplayName());

        UpdateQuery updateQuery = UpdateQuery.builder(instanceQueryBySpaceId(after.getId()))
                .withScript("ctx._source.space_display_name = params.spaceDisplayName;")
                .withScriptType(ScriptType.INLINE)
                .withParams(params)
                .withRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .build();
        var response = elasticsearchOperations.updateByQuery(updateQuery,
                elasticsearchOperations.getIndexCoordinatesFor(EsOntologyInstanceDTO.class));
        log.info("空间[{}]更新，回刷实例文档 space_display_name 受影响 {} 条",
                after.getId(), response.getUpdated());
        return true;
    }

    @Override
    public boolean handleDeleteCdc(OntologySpace before) {
        if (before == null || before.getId() == null) {
            return false;
        }
        // 1. 删除本体空间元数据文档
        ontologySpaceRepository.deleteById(before.getId().longValue());

        // 2. 级联删除该空间下所有由数据湖同步来的实例对象文档
        //    （业务上空间下有本体时不允许删除，此处按 space_id 兜底清理残留实例）
        DeleteQuery deleteQuery = DeleteQuery.builder(instanceQueryBySpaceId(before.getId()))
                .withRefresh(Boolean.TRUE)
                .build();
        var response = elasticsearchOperations.delete(deleteQuery, EsOntologyInstanceDTO.class);
        log.info("空间[{}]删除，级联删除实例文档 {} 条",
                before.getId(), response.getDeleted());
        return true;
    }

    /**
     * 构造"某空间下全部实例对象"的查询：按 space_id（= ontology_space.id）精确匹配。
     * 使用 by-query 方式可自动携带各分片 routing，满足 ontology_instance 索引 _routing.required 约束。
     */
    private Query instanceQueryBySpaceId(Integer spaceId) {
        return NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("space_id").value(spaceId.longValue())))
                .build();
    }
}
