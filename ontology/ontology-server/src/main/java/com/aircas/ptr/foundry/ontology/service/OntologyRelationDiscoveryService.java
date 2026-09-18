package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyRelationDiscoveryParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyRelationDiscoveryResultVO;

/**
 * 本体关系发现服务
 * <p>
 * 场景：构建新本体时，判断它与同一空间下已有本体是否存在潜在关系，
 * 输出关系类型（复用 OntologyLinkTypeEnum）+ 置信度 + 理由，辅助用户建立关系。
 */
public interface OntologyRelationDiscoveryService {

    /**
     * 发现新本体与同空间下已有本体的潜在关系
     */
    OntologyRelationDiscoveryResultVO discover(OntologyRelationDiscoveryParam param);
}
