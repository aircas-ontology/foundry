package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.param.UploadFileCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileQueryParam;
import com.aircas.ptr.foundry.topic.model.po.UploadFile;
import com.aircas.ptr.foundry.topic.model.po.UploadFileOverview;
import com.aircas.ptr.foundry.topic.model.vo.ExtractItemVO;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileOverviewStatVO;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileVO;
import com.aircas.ptr.foundry.topic.repository.UploadFileMapper;
import com.aircas.ptr.foundry.topic.repository.UploadFileOverviewMapper;
import com.aircas.ptr.foundry.topic.service.UploadFileService;
import com.aircas.ptr.foundry.topic.service.support.ExtractListSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
public class UploadFileServiceImpl extends ServiceImpl<UploadFileMapper, UploadFile> implements UploadFileService {

    @Resource
    private UploadFileOverviewMapper uploadFileOverviewMapper;

    @Transactional
    @Override
    public Long create(UploadFileCreateParam param) {
        UploadFile file = UploadFile.builder()
                .fileName(param.getFileName().trim())
                .fileType(param.getFileType().trim())
                .ontologySpace(StringUtils.trimToNull(param.getOntologySpace()))
                .ontologySpaceName(StringUtils.trimToNull(param.getOntologySpaceName()))
                .uploadPath(StringUtils.trimToNull(param.getUploadPath()))
                .extractProgress(param.getExtractProgress() == null ? 0 : param.getExtractProgress())
                .uploader(StringUtils.isBlank(param.getUploader()) ? "admin" : param.getUploader().trim())
                .extractTime(param.getExtractTime())
                .deleted(DeletedFlagEnum.NOT_DELETED.getCode())
                .build();
        save(file);
        return file.getId();
    }

    @Override
    public List<UploadFileVO> listFiles(UploadFileQueryParam param) {
        String keyword = param == null ? "" : StringUtils.trimToEmpty(param.getKeyword());
        List<UploadFileVO> files = list(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode())
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(UploadFile::getFileName, keyword)
                        .or().like(UploadFile::getOntologySpaceName, keyword)
                        .or().apply("cast(id as varchar) like {0}", "%" + keyword + "%"))
                .orderByDesc(UploadFile::getId))
                .stream()
                .map(this::toVO)
                .toList();
        Map<Long, UploadFileOverviewStatVO> overviewStats = loadOverviewStats(files);
        files.forEach(file -> file.setOverviewStat(overviewStats.getOrDefault(file.getId(), emptyStat())));
        return files;
    }

    private Map<Long, UploadFileOverviewStatVO> loadOverviewStats(List<UploadFileVO> files) {
        if (files.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = files.stream().map(UploadFileVO::getId).toList();
        List<UploadFileOverview> rows = uploadFileOverviewMapper.selectList(new LambdaQueryWrapper<UploadFileOverview>()
                .in(UploadFileOverview::getFileId, ids));
        Map<Long, Integer> fileTotals = new HashMap<>();
        Map<Long, Integer> targetCounts = new HashMap<>();
        Map<Long, Integer> implicitRelations = new HashMap<>();
        Map<Long, Map<String, ExtractItemVO>> extractItems = new HashMap<>();
        for (UploadFileOverview row : rows) {
            Long fileId = row.getFileId();
            fileTotals.merge(fileId, row.getFileTotal() == null ? 0 : row.getFileTotal(), Integer::sum);
            targetCounts.merge(fileId, row.getTargetCount() == null ? 0 : row.getTargetCount(), Integer::sum);
            implicitRelations.merge(fileId, row.getImplicitRelation() == null ? 0 : row.getImplicitRelation(), Integer::sum);
            Map<String, ExtractItemVO> named = extractItems.computeIfAbsent(fileId, key -> new HashMap<>());
            for (ExtractItemVO item : ExtractListSupport.read(row.getExtractList())) {
                named.merge(item.getName(), item, (left, right) -> ExtractItemVO.builder()
                        .name(left.getName())
                        .quantity(left.getQuantity() + right.getQuantity())
                        .proportion(left.getProportion().add(right.getProportion()))
                        .build());
            }
        }
        Map<Long, UploadFileOverviewStatVO> stats = new HashMap<>();
        for (Long fileId : ids) {
            if (!fileTotals.containsKey(fileId)) {
                continue;
            }
            List<ExtractItemVO> extractList = extractItems.getOrDefault(fileId, Map.of()).values().stream()
                    .sorted(Comparator.comparing(ExtractItemVO::getName))
                    .toList();
            stats.put(fileId, UploadFileOverviewStatVO.builder()
                    .fileTotal(fileTotals.get(fileId))
                    .targetCount(targetCounts.get(fileId))
                    .implicitRelation(implicitRelations.get(fileId))
                    .extractList(extractList)
                    .build());
        }
        return stats;
    }

    private UploadFileOverviewStatVO emptyStat() {
        return UploadFileOverviewStatVO.builder()
                .fileTotal(0)
                .targetCount(0)
                .implicitRelation(0)
                .extractList(List.of())
                .build();
    }

    @Transactional
    @Override
    public void delete(Long id) {
        requireFile(id);
        update(new LambdaUpdateWrapper<UploadFile>()
                .eq(UploadFile::getId, id)
                .eq(UploadFile::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode())
                .set(UploadFile::getDeleted, DeletedFlagEnum.DELETED.getCode()));
    }

    @Override
    public UploadFileDetailVO get(Long id) {
        return toDetailVO(requireFile(id));
    }

    private UploadFile requireFile(Long id) {
        UploadFile file = getOne(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getId, id)
                .eq(UploadFile::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkNotNull(file, "文件不存在", HttpStatus.BAD_REQUEST);
        return file;
    }

    private UploadFileVO toVO(UploadFile file) {
        return UploadFileVO.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .fileType(file.getFileType())
                .ontologySpace(file.getOntologySpace())
                .ontologySpaceName(file.getOntologySpaceName())
                .uploadPath(file.getUploadPath())
                .extractProgress(file.getExtractProgress())
                .uploader(file.getUploader())
                .extractTime(file.getExtractTime())
                .deleted(file.getDeleted())
                .build();
    }

    private UploadFileDetailVO toDetailVO(UploadFile file) {
        return UploadFileDetailVO.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .deleted(file.getDeleted())
                .build();
    }
}
