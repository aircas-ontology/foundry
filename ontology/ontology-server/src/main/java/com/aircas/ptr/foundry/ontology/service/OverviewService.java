package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.vo.OverviewCountVO;

/**
 * @author wangweigang
 * @description 概览页面service
 */
public interface OverviewService {

    /**
     * 获取概览页面统计数据，包括本体、关系、动作、函数等。
     * @return
     */
    OverviewCountVO getCount();
}
