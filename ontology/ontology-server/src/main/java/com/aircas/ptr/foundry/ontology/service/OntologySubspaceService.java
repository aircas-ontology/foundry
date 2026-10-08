package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologySubspaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySubspaceCreateVO;

/**
 * 子空间创建服务。
 */
public interface OntologySubspaceService {

    /**
     * 基于父空间创建子空间，复制选中的对象、属性（含筛选条件）、实例和关系。
     *
     * @param param 子空间创建参数
     * @return 创建结果，包含子空间 id 及本体/关系映射
     */
    OntologySubspaceCreateVO createSubspace(OntologySubspaceCreateParam param);
}
