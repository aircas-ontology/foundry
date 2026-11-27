package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationOverviewUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.ImplicitRelation;
import com.aircas.ptr.foundry.topic.model.po.ImplicitRelationOverview;
import com.aircas.ptr.foundry.topic.repository.ImplicitRelationMapper;
import com.aircas.ptr.foundry.topic.repository.ImplicitRelationOverviewMapper;
import com.aircas.ptr.foundry.topic.service.ImplicitRelationOverviewService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImplicitRelationOverviewServiceImpl extends ServiceImpl<ImplicitRelationOverviewMapper, ImplicitRelationOverview>
        implements ImplicitRelationOverviewService {

    @Resource
    private ImplicitRelationMapper implicitRelationMapper;

    @Transactional
    @Override
    public Long create(ImplicitRelationOverviewCreateParam param) {
        requireImplicitRelation(param.getImplicitRelationId());
        ImplicitRelationOverview overview = ImplicitRelationOverview.builder()
                .implicitRelationId(param.getImplicitRelationId())
                .relationType(param.getRelationType().trim())
                .name(param.getName().trim())
                .quantity(param.getQuantity())
                .creator(StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim())
                .build();
        save(overview);
        return overview.getId();
    }

    @Transactional
    @Override
    public void update(ImplicitRelationOverviewUpdateParam param) {
        ImplicitRelationOverview overview = getById(param.getId());
        PreconditionUtils.checkNotNull(overview, "隐含关系总览统计不存在", HttpStatus.BAD_REQUEST);
        requireImplicitRelation(param.getImplicitRelationId());
        overview.setImplicitRelationId(param.getImplicitRelationId());
        overview.setRelationType(param.getRelationType().trim());
        overview.setName(param.getName().trim());
        overview.setQuantity(param.getQuantity());
        updateById(overview);
    }

    private void requireImplicitRelation(Long implicitRelationId) {
        ImplicitRelation relation = implicitRelationMapper.selectById(implicitRelationId);
        PreconditionUtils.checkNotNull(relation, "隐含关系不存在", HttpStatus.BAD_REQUEST);
    }
}
