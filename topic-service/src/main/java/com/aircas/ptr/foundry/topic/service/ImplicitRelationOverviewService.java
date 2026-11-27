package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationOverviewUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.ImplicitRelationOverview;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationOverviewStatVO;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface ImplicitRelationOverviewService extends IService<ImplicitRelationOverview> {

    Long create(ImplicitRelationOverviewCreateParam param);

    void update(ImplicitRelationOverviewUpdateParam param);

    List<ImplicitRelationOverviewStatVO> listStats();
}
