package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.GlobalSearchParam;
import com.aircas.ptr.foundry.ontology.model.vo.GlobalSearchHitVO;

import java.util.List;

public interface GlobalSearchService {

    /**
     * 跨五个本体索引（空间/本体/属性/实例/关系分组）全局检索，
     * 匹配文档所有字段，按相关性得分降序返回命中文档 id 列表。
     */
    List<GlobalSearchHitVO> search(GlobalSearchParam param);
}
