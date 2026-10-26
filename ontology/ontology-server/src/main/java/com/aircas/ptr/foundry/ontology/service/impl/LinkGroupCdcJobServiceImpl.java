package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.converter.OntologyLinkGroupConverter;
import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyLinkGroupDTO;
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

/**
 * 主库 ontology_link_group CDC -> ES ontology_link_group 索引 + ArangoDB relation 边同步。
 * <p>
 * 关系新增：只同步 ES 文档，图库不再预铺候选边（实例关系边只在真实成立或人工激活时落库）；
 * 关系删除：该关系在图库里的全部边一并删除，与平台 API 路径
 * {@code OntologyLinkGroupServiceImpl#deleteLinkByLinkUniqueIdentifier} 复用同一方法。
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

    /** 图库未启用时（foundry.integration.arangodb.enabled=false）跳过图库边清理 */
    @Value("${foundry.integration.arangodb.enabled:true}")
    boolean graphEnabled;

    @Override
    public void handleCreateCdc(OntologyLinkGroup after) {
        EsOntologyLinkGroupDTO esOntologyLinkGroupDTO = OntologyLinkGroupConverter.convert(after);
        enrichLinkGroupDTO(esOntologyLinkGroupDTO);
        ontologyLinkGroupRepository.save(esOntologyLinkGroupDTO);
        log.info("save data success :{}", JSONObject.toJSONString(esOntologyLinkGroupDTO));
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
     * 关系删除 -> 图库中该关系的全部实例边一起删除。
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
