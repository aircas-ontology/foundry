package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyLinkGroupConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.repository.elasticsearch.OntologyLinkGroupRepository;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologySpaceMapper;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.aircas.ptr.foundry.ontology.service.LinkGroupCdcJobService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 主库 ontology_link_group CDC -> ES ontology_link_group 索引 + ArangoDB relation 边同步。
 * <p>
 * 关系新增：按两端本体的全部实例节点铺候选边（status=DELETE）；关系删除：该关系的全部边
 * （候选边与用户已激活的 ENABLE 边）一并删除。两者与平台 API 路径
 * （{@code OntologyLinkGroupServiceImpl#createLink} / {@code #deleteLinkByLinkUniqueIdentifier}）
 * 复用同一批 {@link EntityService} 方法，重复执行只覆盖、不产生第二份边。
 * ES 与图库各自独立成段处理，图库失败只记录日志、不影响 ES 结果。
 */
@Slf4j
@Service
public class LinkGroupCdcJobServiceImpl implements LinkGroupCdcJobService {
    @Autowired
    OntologyLinkGroupRepository ontologyLinkGroupRepository;
    @Autowired
    OntologySpaceMapper spaceMapper;
    @Autowired
    EntityService entityService;

    /** 图库未启用时（foundry.integration.arangodb.enabled=false）跳过图库铺边与清理 */
    @Value("${foundry.integration.arangodb.enabled:true}")
    boolean graphEnabled;

    @Override
    public void handleCreateCdc(OntologyLinkGroup after) {
        EsOntologyLinkGroupDTO esOntologyLinkGroupDTO = OntologyLinkGroupConverter.convert(after);
        enrichLinkGroupDTO(esOntologyLinkGroupDTO);
        ontologyLinkGroupRepository.save(esOntologyLinkGroupDTO);
        log.info("save data success :{}", JSONObject.toJSONString(esOntologyLinkGroupDTO));
        createGraphRelations(after);
    }

    @Override
    public boolean handleUpdateCdc(OntologyLinkGroup before, OntologyLinkGroup after) {
        if (after == null || after.getId() == null) {
            return false;
        }
        EsOntologyLinkGroupDTO esOntologyLinkGroupDTO = OntologyLinkGroupConverter.convert(after);
        enrichLinkGroupDTO(esOntologyLinkGroupDTO);
        ontologyLinkGroupRepository.save(esOntologyLinkGroupDTO);
        log.info("update data success :{}", JSONObject.toJSONString(esOntologyLinkGroupDTO));
        return true;
    }

    @Override
    public boolean handleDeleteCdc(OntologyLinkGroup before) {
        if (before == null || before.getId() == null) {
            return false;
        }
        ontologyLinkGroupRepository.deleteById(before.getId());
        log.info("delete data success, link group id:{}", before.getId());
        deleteGraphRelations(before.getUniqueIdentifier());
        return true;
    }

    /**
     * 关系新增 -> 按两端本体的全部实例节点铺候选边（status=DELETE，图查询按 ENABLE 过滤时不可见，
     * 需用户/函数激活后才生效）。{@code createEntityRelations} 内部按 (from, to) 去重，
     * 与 API 建关系时的同步铺边、以及全量同步铺边重复执行都只会补新对，不会产生第二份边。
     */
    private void createGraphRelations(OntologyLinkGroup link) {
        if (!graphEnabled) {
            return;
        }
        if (link == null || StringUtils.isEmpty(link.getUniqueIdentifier())) {
            log.warn("关系 CDC 新增消息缺少 unique_identifier，跳过图库铺边");
            return;
        }
        // 与全量同步选取关系的口径一致：已标记删除的关系不铺边
        if (Objects.equals(link.getStatus(), Status.DELETE.getValue())) {
            log.info("关系[{}]状态为已删除，跳过图库铺边", link.getUniqueIdentifier());
            return;
        }
        try {
            int created = entityService.createEntityRelations(link.getUniqueIdentifier());
            log.info("关系[{}]新增，图库候选关系边新建 {} 条", link.getUniqueIdentifier(), created);
        } catch (Exception e) {
            log.error("关系[{}]图库候选边铺边失败", link.getUniqueIdentifier(), e);
        }
    }

    /**
     * 关系删除 -> 图库中该关系的全部边一起删除（候选边与已激活边都不留）。
     * 图库失败不影响 ES 删除结果：主库这条关系已不存在，边留着只会让图查询出错。
     */
    private void deleteGraphRelations(String linkUniqueIdentifier) {
        if (!graphEnabled) {
            return;
        }
        if (StringUtils.isEmpty(linkUniqueIdentifier)) {
            log.warn("关系 CDC 删除消息缺少 unique_identifier，跳过图库清理");
            return;
        }
        try {
            entityService.deleteRelationsByLinkId(linkUniqueIdentifier);
            log.info("关系[{}]已删除，图库对应关系边已清理", linkUniqueIdentifier);
        } catch (Exception e) {
            log.error("关系[{}]图库边清理失败", linkUniqueIdentifier, e);
        }
    }

    private void enrichLinkGroupDTO(EsOntologyLinkGroupDTO dto) {
        if (dto.getOntologySpaceId() == null) {
            return;
        }
        OntologySpace space = spaceMapper.selectById(dto.getOntologySpaceId().intValue());
        if (space != null) {
            dto.setSpaceName(space.getDisplayName());
            dto.setSpaceType(space.getType());
        }
    }
}
