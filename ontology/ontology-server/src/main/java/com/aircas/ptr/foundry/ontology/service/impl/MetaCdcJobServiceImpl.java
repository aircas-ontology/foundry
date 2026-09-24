package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.ontology.converter.OntologyMetaConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyMetaDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyInstanceRepository;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyMetaRepository;
import com.aircas.ptr.foundry.ontology.service.MetaCdcJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
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
public class MetaCdcJobServiceImpl implements MetaCdcJobService {
    @Autowired
    OntologyMetaRepository ontologyMetaRepository;
    @Autowired
    OntologyInstanceRepository ontologyInstanceRepository;
    @Autowired
    ElasticsearchOperations elasticsearchOperations;

    @Override
    public void handleCreateCdc(OntologyMeta ontologyMeta) {
        EsOntologyMetaDTO esOntologyMetaDTO = OntologyMetaConverter.convert(ontologyMeta);
        ontologyMetaRepository.save(esOntologyMetaDTO);
    }

    @Override
    public boolean handleUpdateCdc(OntologyMeta before, OntologyMeta after) {
        if (after == null) {
            return false;
        }
        // 1. 更新本体元数据文档（ontology_meta 索引）
        ontologyMetaRepository.save(OntologyMetaConverter.convert(after));

        // 2. 实例文档（ontology_instance 索引）中来源于本体的字段：
        //    ontology_name = 本体 displayName，api_name = 本体 apiName。
        //    仅当这两个字段发生变化时，才回刷该本体下的全部数据湖实例对象。
        boolean displayNameChanged = before != null
                && !Objects.equals(before.getDisplayName(), after.getDisplayName());
        boolean apiNameChanged = before != null
                && !Objects.equals(before.getApiName(), after.getApiName());
        if (!displayNameChanged && !apiNameChanged) {
            return true;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("ontologyName", after.getDisplayName());
        params.put("apiName", after.getApiName());

        UpdateQuery updateQuery = UpdateQuery.builder(instanceQueryByOntologyUid(after.getUniqueIdentifier()))
                .withScript("ctx._source.ontology_name = params.ontologyName;"
                        + "ctx._source.api_name = params.apiName;")
                .withScriptType(ScriptType.INLINE)
                .withParams(params)
                .withRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .build();
        var response = elasticsearchOperations.updateByQuery(updateQuery,
                elasticsearchOperations.getIndexCoordinatesFor(EsOntologyInstanceDTO.class));
        log.info("本体[{}]更新，回刷实例文档 ontology_name/api_name 受影响 {} 条",
                after.getUniqueIdentifier(), response.getUpdated());
        return true;
    }

    @Override
    public boolean handleDeleteCdc(OntologyMeta ontologyMeta) {
        if (ontologyMeta == null) {
            return false;
        }
        // 1. 删除本体元数据文档
        if (ontologyMeta.getId() != null) {
            ontologyMetaRepository.deleteById(ontologyMeta.getId());
        }

        // 2. 级联删除该本体下所有由数据湖同步来的实例对象文档
        DeleteQuery deleteQuery = DeleteQuery.builder(instanceQueryByOntologyUid(ontologyMeta.getUniqueIdentifier()))
                .withRefresh(Boolean.TRUE)
                .build();
        var response = elasticsearchOperations.delete(deleteQuery, EsOntologyInstanceDTO.class);
        log.info("本体[{}]删除，级联删除实例文档 {} 条",
                ontologyMeta.getUniqueIdentifier(), response.getDeleted());
        return true;
    }

    /**
     * 构造"某本体下全部实例对象"的查询：按 ontology_uid（= 本体 unique_identifier）精确匹配。
     * 使用 by-query 方式可自动携带各分片 routing，满足 ontology_instance 索引 _routing.required 约束。
     */
    private Query instanceQueryByOntologyUid(String ontologyUid) {
        return NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("ontology_uid").value(ontologyUid)))
                .build();
    }
}
