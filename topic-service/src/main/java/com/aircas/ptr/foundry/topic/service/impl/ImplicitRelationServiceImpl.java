package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.ImplicitRelationStatusEnum;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationCreateParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationQueryParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.ImplicitRelation;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationVO;
import com.aircas.ptr.foundry.topic.repository.ImplicitRelationMapper;
import com.aircas.ptr.foundry.topic.service.ImplicitRelationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImplicitRelationServiceImpl extends ServiceImpl<ImplicitRelationMapper, ImplicitRelation>
        implements ImplicitRelationService {

    @Transactional
    @Override
    public Long create(ImplicitRelationCreateParam param) {
        ImplicitRelation relation = ImplicitRelation.builder()
                .startNodeCode(param.getStartNodeCode().trim())
                .startNodeName(param.getStartNodeName().trim())
                .belongSpace(param.getBelongSpace().trim())
                .relationType(param.getRelationType().trim())
                .hopCount(param.getHopCount())
                .implicitCount(param.getImplicitCount() == null ? 0 : param.getImplicitCount())
                .progressStatus(param.getProgressStatus() == null
                        ? ImplicitRelationStatusEnum.NOT_FOUND.getCode() : param.getProgressStatus())
                .creator(blankToAdmin(param.getCreator()))
                .updater(blankToAdmin(param.getUpdater()))
                .build();
        save(relation);
        return relation.getId();
    }

    @Transactional
    @Override
    public void update(ImplicitRelationUpdateParam param) {
        ImplicitRelation relation = requireRelation(param.getId());
        relation.setStartNodeCode(param.getStartNodeCode().trim());
        relation.setStartNodeName(param.getStartNodeName().trim());
        relation.setBelongSpace(param.getBelongSpace().trim());
        relation.setRelationType(param.getRelationType().trim());
        relation.setHopCount(param.getHopCount());
        relation.setImplicitCount(param.getImplicitCount());
        relation.setProgressStatus(param.getProgressStatus());
        relation.setUpdater(blankToAdmin(param.getUpdater()));
        relation.setUpdateTime(null);
        updateById(relation);
    }

    @Override
    public IPage<ImplicitRelationVO> listRelations(ImplicitRelationQueryParam param) {
        ImplicitRelationQueryParam query = param == null ? new ImplicitRelationQueryParam() : param;
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        String keyword = StringUtils.trimToEmpty(query.getKeyword());
        LambdaQueryWrapper<ImplicitRelation> wrapper = new LambdaQueryWrapper<ImplicitRelation>()
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(ImplicitRelation::getStartNodeName, keyword)
                        .or().like(ImplicitRelation::getRelationType, keyword)
                        .or().apply("cast(id as varchar) like {0}", "%" + keyword + "%"))
                .orderByAsc(ImplicitRelation::getId);
        Page<ImplicitRelation> page = page(new Page<>(pageNum, pageSize), wrapper);
        Page<ImplicitRelationVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Transactional
    @Override
    public void delete(Long id) {
        requireRelation(id);
        removeById(id);
    }

    @Override
    public ImplicitRelationDetailVO get(Long id) {
        return toDetailVO(requireRelation(id));
    }

    private ImplicitRelation requireRelation(Long id) {
        ImplicitRelation relation = getById(id);
        PreconditionUtils.checkNotNull(relation, "隐含关系不存在", HttpStatus.BAD_REQUEST);
        return relation;
    }

    private String blankToAdmin(String value) {
        return StringUtils.isBlank(value) ? "admin" : value.trim();
    }

    private ImplicitRelationVO toVO(ImplicitRelation relation) {
        return ImplicitRelationVO.builder()
                .id(relation.getId())
                .startNodeCode(relation.getStartNodeCode())
                .startNodeName(relation.getStartNodeName())
                .belongSpace(relation.getBelongSpace())
                .relationType(relation.getRelationType())
                .hopCount(relation.getHopCount())
                .implicitCount(relation.getImplicitCount())
                .progressStatus(relation.getProgressStatus())
                .updateTime(relation.getUpdateTime())
                .build();
    }

    private ImplicitRelationDetailVO toDetailVO(ImplicitRelation relation) {
        return ImplicitRelationDetailVO.builder()
                .id(relation.getId())
                .startNodeCode(relation.getStartNodeCode())
                .startNodeName(relation.getStartNodeName())
                .belongSpace(relation.getBelongSpace())
                .relationType(relation.getRelationType())
                .hopCount(relation.getHopCount())
                .build();
    }
}
