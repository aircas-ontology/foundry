package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.ImplicitRelationStatusEnum;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationCreateParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationQueryParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.ImplicitRelation;
import com.aircas.ptr.foundry.topic.model.po.ImplicitRelationOverview;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationOverviewStatVO;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationVO;
import com.aircas.ptr.foundry.topic.repository.ImplicitRelationMapper;
import com.aircas.ptr.foundry.topic.repository.ImplicitRelationOverviewMapper;
import com.aircas.ptr.foundry.topic.service.ImplicitRelationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImplicitRelationServiceImpl extends ServiceImpl<ImplicitRelationMapper, ImplicitRelation>
        implements ImplicitRelationService {

    @Resource
    private ImplicitRelationOverviewMapper implicitRelationOverviewMapper;

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
        Map<Long, List<ImplicitRelationOverviewStatVO>> overviewStats = loadOverviewStats(page.getRecords());
        Page<ImplicitRelationVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(relation -> {
            ImplicitRelationVO vo = toVO(relation);
            vo.setOverviewStats(overviewStats.getOrDefault(relation.getId(), List.of()));
            return vo;
        }).toList());
        return result;
    }

    private Map<Long, List<ImplicitRelationOverviewStatVO>> loadOverviewStats(List<ImplicitRelation> relations) {
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = relations.stream().map(ImplicitRelation::getId).toList();
        List<ImplicitRelationOverview> rows = implicitRelationOverviewMapper.selectList(new LambdaQueryWrapper<ImplicitRelationOverview>()
                .in(ImplicitRelationOverview::getImplicitRelationId, ids)
                .select(ImplicitRelationOverview::getImplicitRelationId,
                        ImplicitRelationOverview::getRelationType,
                        ImplicitRelationOverview::getName,
                        ImplicitRelationOverview::getQuantity));
        Map<Long, Map<StatKey, Integer>> grouped = new HashMap<>();
        for (ImplicitRelationOverview row : rows) {
            StatKey key = new StatKey(StringUtils.defaultString(row.getRelationType()),
                    StringUtils.defaultString(row.getName()));
            int quantity = row.getQuantity() == null ? 0 : row.getQuantity();
            grouped.computeIfAbsent(row.getImplicitRelationId(), id -> new HashMap<>())
                    .merge(key, quantity, Integer::sum);
        }
        Map<Long, List<ImplicitRelationOverviewStatVO>> stats = new HashMap<>();
        grouped.forEach((id, counts) -> stats.put(id, counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(StatKey::type).thenComparing(StatKey::name)))
                .map(entry -> ImplicitRelationOverviewStatVO.builder()
                        .relationType(entry.getKey().type())
                        .name(entry.getKey().name())
                        .quantity(entry.getValue())
                        .build())
                .toList()));
        return stats;
    }

    private record StatKey(String type, String name) {
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
