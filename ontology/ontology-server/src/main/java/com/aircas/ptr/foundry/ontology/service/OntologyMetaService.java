package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;



public interface OntologyMetaService extends IService<OntologyMeta> {

    String createOntology(OntologyMetaCreateParam ontologyCreateParam);

    List<OntologyGroupMetaVO> getByGroupId(String groupId, OntologyOrderByEnum orderBy, QuerySortEnum sort);

    OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier);

    void deleteOntology(String uniqueIdentifier);

    void updateMeta(OntologyUpdateParam updateParam);

    List<OntologyMetaInfoVO> searchByKeyword(String keyword);

    /**
     * 列出指定本体空间下已存在的本体元数据（仅含启用中 status=1 的记录）。
     *
     * <p>供 Agent 在「功能四 对象关系推导」中拉取同空间的已有对象作为候选目标，
     * 与 {@link #searchByKeyword(String)} 的区别在于本方法按 spaceId 硬隔离，不接受关键字。</p>
     *
     * @param spaceId 本体空间 id（ontology_space 主键）
     * @return 空间内已存在的本体列表，无则返回空集合
     */
    List<OntologyMetaInfoVO> listBySpaceId(Integer spaceId);

    List<OntologyMetaInfoVO> getByCategoryId(Integer categoryId);

    List<OntologyMetaNodeVO> getOntologyTreeByByGroupId(String groupId);


    List<String> importOntologies(MultipartFile file);
}
