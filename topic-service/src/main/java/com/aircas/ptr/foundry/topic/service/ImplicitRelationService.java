package com.aircas.ptr.foundry.topic.service;

import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationCreateParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationQueryParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.ImplicitRelation;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;

public interface ImplicitRelationService extends IService<ImplicitRelation> {

    Long create(ImplicitRelationCreateParam param);

    void update(ImplicitRelationUpdateParam param);

    IPage<ImplicitRelationVO> listRelations(ImplicitRelationQueryParam param);

    void delete(Long id);

    ImplicitRelationDetailVO get(Long id);
}
