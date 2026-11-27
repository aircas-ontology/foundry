package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileOverviewUpdateParam;
import com.aircas.ptr.foundry.topic.model.po.UploadFile;
import com.aircas.ptr.foundry.topic.model.po.UploadFileOverview;
import com.aircas.ptr.foundry.topic.repository.UploadFileMapper;
import com.aircas.ptr.foundry.topic.repository.UploadFileOverviewMapper;
import com.aircas.ptr.foundry.topic.service.UploadFileOverviewService;
import com.aircas.ptr.foundry.topic.service.support.ExtractListSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadFileOverviewServiceImpl extends ServiceImpl<UploadFileOverviewMapper, UploadFileOverview>
        implements UploadFileOverviewService {

    @Resource
    private UploadFileMapper uploadFileMapper;

    @Transactional
    @Override
    public Long create(UploadFileOverviewCreateParam param) {
        requireFile(param.getFileId());
        ExtractListSupport.check(param.getExtractList());
        UploadFileOverview overview = UploadFileOverview.builder()
                .fileId(param.getFileId())
                .fileTotal(param.getFileTotal())
                .extractList(param.getExtractList())
                .targetCount(param.getTargetCount())
                .implicitRelation(param.getImplicitRelation())
                .creator(StringUtils.isBlank(param.getCreator()) ? "admin" : param.getCreator().trim())
                .build();
        save(overview);
        return overview.getId();
    }

    @Transactional
    @Override
    public void update(UploadFileOverviewUpdateParam param) {
        UploadFileOverview overview = getById(param.getId());
        PreconditionUtils.checkNotNull(overview, "文件总览统计不存在", HttpStatus.BAD_REQUEST);
        requireFile(param.getFileId());
        ExtractListSupport.check(param.getExtractList());
        overview.setFileId(param.getFileId());
        overview.setFileTotal(param.getFileTotal());
        overview.setExtractList(param.getExtractList());
        overview.setTargetCount(param.getTargetCount());
        overview.setImplicitRelation(param.getImplicitRelation());
        updateById(overview);
    }

    private void requireFile(Long fileId) {
        UploadFile file = uploadFileMapper.selectOne(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getId, fileId)
                .eq(UploadFile::getDeleted, DeletedFlagEnum.NOT_DELETED.getCode()));
        PreconditionUtils.checkNotNull(file, "文件不存在", HttpStatus.BAD_REQUEST);
    }
}
