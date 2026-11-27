package com.aircas.ptr.foundry.topic.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.topic.model.enums.DeletedFlagEnum;
import com.aircas.ptr.foundry.topic.model.param.UploadFileCreateParam;
import com.aircas.ptr.foundry.topic.model.param.UploadFileQueryParam;
import com.aircas.ptr.foundry.topic.model.po.UploadFile;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.UploadFileVO;
import com.aircas.ptr.foundry.topic.repository.UploadFileMapper;
import com.aircas.ptr.foundry.topic.service.UploadFileService;
import com.aircas.ptr.foundry.topic.service.support.UploadFileContentReader;
import com.aircas.ptr.foundry.topic.service.support.UploadFileStorage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadFileServiceImpl extends ServiceImpl<UploadFileMapper, UploadFile> implements UploadFileService {

    private static final Set<String> FILE_TYPES = Set.of("txt", "doc", "docx", "pdf", "xlsx", "png", "jpg");

    @Resource
    private UploadFileStorage uploadFileStorage;

    @Resource
    private UploadFileContentReader uploadFileContentReader;

    @Transactional
    @Override
    public Long create(MultipartFile file, UploadFileCreateParam param) {
        PreconditionUtils.checkArgument(file != null && !file.isEmpty(), "上传文件不能为空", HttpStatus.BAD_REQUEST);
        String fileName = originalFileName(file);
        PreconditionUtils.checkArgument(fileName.length() <= 256, "文件名称最长256个字符", HttpStatus.BAD_REQUEST);
        String fileType = fileType(fileName);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + fileName;
        Path saved = null;
        try {
            saved = uploadFileStorage.save(file, storedName);
            String context = uploadFileContentReader.read(fileType, saved);
            UploadFile record = UploadFile.builder()
                    .fileName(fileName)
                    .fileType(fileType)
                    .ontologySpace(StringUtils.trimToNull(param.getOntologySpace()))
                    .ontologySpaceName(StringUtils.trimToNull(param.getOntologySpaceName()))
                    .uploadPath(saved.toString())
                    .extractProgress(param.getExtractProgress() == null ? 0 : param.getExtractProgress())
                    .uploader(StringUtils.isBlank(param.getUploader()) ? "admin" : param.getUploader().trim())
                    .extractTime(param.getExtractTime())
                    .context(context)
                    .deleted(DeletedFlagEnum.NOT_DELETED.getCode())
                    .build();
            save(record);
            return record.getId();
        } catch (RuntimeException ex) {
            uploadFileStorage.deleteQuietly(saved);
            throw ex;
        }
    }

    private String originalFileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(original), "文件名称不能为空", HttpStatus.BAD_REQUEST);
        String name = Path.of(original).getFileName().toString().replaceAll("[\\\\/]", "_").trim();
        PreconditionUtils.checkArgument(StringUtils.isNotBlank(name) && !".".equals(name) && !"..".equals(name),
                "文件名称不合法", HttpStatus.BAD_REQUEST);
        return name;
    }

    private String fileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        PreconditionUtils.checkArgument(dot > 0 && dot < fileName.length() - 1, "文件类型不能为空", HttpStatus.BAD_REQUEST);
        String type = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        PreconditionUtils.checkArgument(FILE_TYPES.contains(type),
                "文件类型只支持txt、doc、docx、pdf、xlsx、png、jpg", HttpStatus.BAD_REQUEST);
        return type;
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
        return files;
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
