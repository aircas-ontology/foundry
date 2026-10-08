package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.context.UserContextHolder;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.po.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.*;
import com.aircas.ptr.foundry.ontology.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FunctionServiceImpl extends ServiceImpl<FunctionMapper, Function> implements FunctionService {

    @Resource
    private OntologyActionMapper ontologyActionMapper;
    @Resource
    private FunctionExecuteResultMapper executeResultMapper;
    @Resource
    private FunctionVersionMapper functionVersionMapper;
    @Resource
    private FunctionParamService functionParamService;
    @Resource
    private GroovyService groovyService;
    @Lazy
    @Resource
    private EntityServiceImpl entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FunctionDetailVO getFunctionDetailByApi(String api, Long functionVersionId) {
        Function function = requireFunction(api);
        return detail(function, requireVersion(function, functionVersionId));
    }

    private FunctionDetailVO detail(Function function, FunctionVersion version) {
        List<FunctionParameterVO> parameters = functionParamService.list(
                        new LambdaQueryWrapper<FunctionParamPO>()
                                .eq(FunctionParamPO::getFunctionVersionId, version.getId())
                                .orderByAsc(FunctionParamPO::getParamOrder))
                .stream()
                .map(parameter -> FunctionParameterVO.builder()
                        .paramId(parameter.getId())
                        .paramName(parameter.getParamName())
                        .category(parameter.getCategory())
                        .paramOrder(parameter.getParamOrder())
                        .paramSchema(parameter.getParamSchema())
                        .paramType(parameter.getParamType())
                        .description(parameter.getDescription())
                        .build())
                .collect(Collectors.toList());
        return FunctionDetailVO.builder()
                .functionApi(function.getApi())
                .displayName(function.getDisplayName())
                .description(function.getDescription())
                .type(function.getType())
                .model(function.getModel())
                .code(version.getCode())
                .referenceName(version.getReferenceName())
                .params(parameters)
                .functionVersionId(version.getId())
                .versionStatus(version.getVersionStatus())
                .build();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteByApi(String api) {
        Function function = requireFunction(api);
        Long referenceCount = ontologyActionMapper.selectCount(
                new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi, api));
        PreconditionUtils.checkArgument(referenceCount == 0, "该函数已被行为关联: " + api, HttpStatus.BAD_REQUEST);
        function.setStatus(Status.DELETE.getValue());
        updateById(function);
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public Long createFunction(FunctionCreateParam param) {
        PreconditionUtils.checkArgument(
                getOne(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi())) == null,
                "函数api已存在:" + param.getFunctionApi(), HttpStatus.BAD_REQUEST);
        Function function = Function.builder()
                .api(param.getFunctionApi())
                .description(param.getDescription())
                .displayName(param.getDisplayName())
                .type(param.getType())
                .model(param.getModel())
                .status(Status.ENABLE.getValue())
                .latestVersionNo(1)
                .build();
        save(function);
        FunctionVersion version = newVersion(
                function, 1, param.getCode(), param.getReferenceName(), param.getChangeLog());
        functionVersionMapper.insert(version);
        insertParams(function.getId(), version, function.getType());
        if (Boolean.TRUE.equals(param.getPublish())) {
            publish(function, version);
        }
        return version.getId();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateFunction(FunctionUpdateParam param) {
        Function function = requireFunction(param.getFunctionApi());
        FunctionVersion version = requireVersion(function, param.getFunctionVersionId());
        PreconditionUtils.checkArgument(version.getVersionStatus() == FunctionStatusEnum.DRAFT,
                "只能更新草稿版本", HttpStatus.BAD_REQUEST);
        function.setDisplayName(param.getDisplayName()).setDescription(param.getDescription());
        updateById(function);
        version.setCode(param.getCode())
                .setReferenceName(param.getReferenceName())
                .setChangeLog(param.getChangeLog());
        functionVersionMapper.updateById(version);
        functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>()
                .eq(FunctionParamPO::getFunctionVersionId, version.getId()));
        insertParams(function.getId(), version, function.getType());
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public Long createDraft(FunctionVersionParam param) {
        Function function = requireFunction(param.getFunctionApi());
        PreconditionUtils.checkArgument(findDraft(function.getId()) == null,
                "该函数已存在草稿", HttpStatus.BAD_REQUEST);
        FunctionVersion source = requireVersion(function, param.getFunctionVersionId());
        PreconditionUtils.checkArgument(source.getVersionStatus() == FunctionStatusEnum.PUBLISHED,
                "只能基于已发布版本创建草稿", HttpStatus.BAD_REQUEST);
        FunctionVersion version = newVersion(
                function,
                allocate(function),
                param.getCode() != null ? param.getCode() : source.getCode(),
                param.getReferenceName() != null ? param.getReferenceName() : source.getReferenceName(),
                param.getChangeLog());
        functionVersionMapper.insert(version);
        insertParams(function.getId(), version, function.getType());
        return version.getId();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void publishVersion(FunctionVersionParam param) {
        Function function = requireFunction(param.getFunctionApi());
        publish(function, requireVersion(function, param.getFunctionVersionId()));
    }

    private void publish(Function function, FunctionVersion version) {
        PreconditionUtils.checkArgument(version.getVersionStatus() == FunctionStatusEnum.DRAFT,
                "只能发布草稿版本", HttpStatus.BAD_REQUEST);
        if (function.getType() == FunctionTypeEnum.CUSTOMIZE) {
            groovyService.parseGroovyCode(version.getCode());
        } else {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(version.getReferenceName()),
                    "外部函数引用不能为空", HttpStatus.BAD_REQUEST);
        }
        version.setVersionStatus(FunctionStatusEnum.PUBLISHED).setPublishTime(new Date());
        functionVersionMapper.updateById(version);
    }

    @Override
    public Page<FunctionVersionVO> listVersions(String api, Integer pageNum, Integer pageSize) {
        Function function = requireFunction(api);
        Page<FunctionVersion> page = functionVersionMapper.selectPage(
                new Page<FunctionVersion>(pageNum, pageSize).addOrder(OrderItem.desc("version_no")),
                new LambdaQueryWrapper<FunctionVersion>().eq(FunctionVersion::getFunctionId, function.getId()));
        List<FunctionVersionVO> records = page.getRecords().stream()
                .map(version -> FunctionVersionVO.builder()
                        .functionVersionId(version.getId())
                        .functionApi(version.getFunctionApi())
                        .versionStatus(version.getVersionStatus())
                        .changeLog(version.getChangeLog())
                        .createBy(version.getCreateBy())
                        .publishTime(version.getPublishTime())
                        .createTime(version.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        return new Page<FunctionVersionVO>(pageNum, pageSize)
                .setTotal(page.getTotal())
                .setRecords(records);
    }

    @Override
    public Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize) {
        Page<Function> page = page(
                new Page<Function>(pageNum, pageSize).addOrder(OrderItem.desc("create_time")),
                new LambdaQueryWrapper<Function>().ne(Function::getStatus, Status.DELETE.getValue()));
        List<FunctionInfoVO> records = page.getRecords().stream().map(function -> {
            FunctionVersion draft = findDraft(function.getId());
            List<Long> publishedVersionIds = functionVersionMapper.selectList(
                            new LambdaQueryWrapper<FunctionVersion>()
                                    .eq(FunctionVersion::getFunctionId, function.getId())
                                    .eq(FunctionVersion::getVersionStatus, FunctionStatusEnum.PUBLISHED))
                    .stream().map(FunctionVersion::getId).collect(Collectors.toList());
            return FunctionInfoVO.builder()
                    .functionApi(function.getApi())
                    .displayName(function.getDisplayName())
                    .type(function.getType())
                    .model(function.getModel())
                    .description(function.getDescription())
                    .publishedFunctionVersionIds(publishedVersionIds)
                    .draftFunctionVersionId(draft == null ? null : draft.getId())
                    .hasDraft(draft != null)
                    .build();
        }).collect(Collectors.toList());
        return new Page<FunctionInfoVO>(pageNum, pageSize)
                .setTotal(page.getTotal())
                .setRecords(records);
    }

    @Override
    @SneakyThrows
    public String executeFunction(FunctionExecuteParam param) {
        Function function = requireFunction(param.getFunctionApi());
        FunctionVersion version = requireVersion(function, param.getFunctionVersionId());
        PreconditionUtils.checkArgument(version.getVersionStatus() == FunctionStatusEnum.PUBLISHED,
                "只能执行已发布版本", HttpStatus.BAD_REQUEST);
        List<FunctionParamPO> inputParameters = functionParamService.list(
                new LambdaQueryWrapper<FunctionParamPO>()
                        .eq(FunctionParamPO::getFunctionVersionId, version.getId())
                        .eq(FunctionParamPO::getCategory, FunctionParamCategoryEnum.INPUT)
                        .orderByAsc(FunctionParamPO::getParamOrder));
        Map<String, Object> values = CollectionUtils.isEmpty(param.getParameters())
                ? new HashMap<>()
                : param.getParameters().stream().collect(
                        HashMap::new,
                        (map, parameter) -> map.put(parameter.getParamName(), parameter.getParamValue()),
                        HashMap::putAll);
        String json = groovyService.executeGroovy(
                version.getId(), function.getApi(), version.getCode(), values, inputParameters);
        FunctionResultVO result = objectMapper.readValue(json, new TypeReference<FunctionResultVO>() { });
        if (result != null && StringUtils.isNotEmpty(result.getTaskId())) {
            executeResultMapper.insert(FunctionExecuteResult.builder()
                    .taskId(result.getTaskId())
                    .functionApi(function.getApi())
                    .functionVersionId(version.getId())
                    .functionParam(objectMapper.writeValueAsString(param.getParameters()))
                    .taskStatus(TaskStatusEnum.PENDING)
                    .build());
        }
        return json;
    }

    @Override
    @SneakyThrows
    public FunctionExecuteResultVO getExecuteResult(String taskId) {
        FunctionExecuteResult result = executeResultMapper.selectOne(
                new LambdaQueryWrapper<FunctionExecuteResult>()
                        .eq(FunctionExecuteResult::getTaskId, taskId));
        if (result == null) {
            return null;
        }
        return FunctionExecuteResultVO.builder()
                .taskId(result.getTaskId())
                .functionApi(result.getFunctionApi())
                .functionVersionId(result.getFunctionVersionId())
                .actionApi(result.getActionApi())
                .functionParam(result.getFunctionParam())
                .taskStatus(result.getTaskStatus())
                .result(StringUtils.isEmpty(result.getResult()) ? null
                        : objectMapper.readValue(result.getResult(), new TypeReference<FunctionResultVO>() { }))
                .build();
    }

    @Override
    @SneakyThrows
    @Transactional(value = "mainTransactionManager")
    public void callback(FunctionResultVO result) {
        FunctionExecuteResult executeResult = executeResultMapper.selectOne(
                new LambdaQueryWrapper<FunctionExecuteResult>()
                        .eq(FunctionExecuteResult::getTaskId, result.getTaskId())
                        .eq(FunctionExecuteResult::getTaskStatus, TaskStatusEnum.PENDING));
        PreconditionUtils.checkNotNull(executeResult, "task id 不存在或已完成：" + result.getTaskId());
        if (StringUtils.isNotEmpty(executeResult.getActionApi())
                && StringUtils.isNotEmpty(executeResult.getActionContextInfo())) {
            entityService.updateEntityPropertyAndRelation(
                    objectMapper.writeValueAsString(result),
                    objectMapper.readValue(executeResult.getActionContextInfo(),
                            new TypeReference<ActionContextInfoDTO>() { }));
        }
        executeResult.setTaskStatus(TaskStatusEnum.COMPLETED)
                .setResult(objectMapper.writeValueAsString(result));
        executeResultMapper.updateById(executeResult);
    }

    private Function requireFunction(String api) {
        Function function = getOne(new LambdaQueryWrapper<Function>()
                .eq(Function::getApi, api)
                .ne(Function::getStatus, Status.DELETE.getValue()));
        PreconditionUtils.checkArgument(function != null, "函数api不存在：" + api, HttpStatus.BAD_REQUEST);
        return function;
    }

    private FunctionVersion requireVersion(Function function, Long functionVersionId) {
        PreconditionUtils.checkNotNull(functionVersionId, "functionVersionId不能为空");
        FunctionVersion version = functionVersionMapper.selectById(functionVersionId);
        PreconditionUtils.checkArgument(
                version != null && version.getFunctionId().equals(function.getId()),
                "函数版本不存在或不属于函数: " + function.getApi() + "#" + functionVersionId,
                HttpStatus.BAD_REQUEST);
        return version;
    }

    private FunctionVersion findDraft(Long functionId) {
        return functionVersionMapper.selectOne(new LambdaQueryWrapper<FunctionVersion>()
                .eq(FunctionVersion::getFunctionId, functionId)
                .eq(FunctionVersion::getVersionStatus, FunctionStatusEnum.DRAFT));
    }

    private int allocate(Function function) {
        for (int retry = 0; retry < 5; retry++) {
            int current = function.getLatestVersionNo() == null ? 0 : function.getLatestVersionNo();
            int changed = baseMapper.update(null, new LambdaUpdateWrapper<Function>()
                    .eq(Function::getId, function.getId())
                    .eq(Function::getLatestVersionNo, current)
                    .set(Function::getLatestVersionNo, current + 1));
            if (changed == 1) {
                function.setLatestVersionNo(current + 1);
                return current + 1;
            }
            function = baseMapper.selectById(function.getId());
        }
        throw new IllegalStateException("函数版本号分配冲突，请重试");
    }

    private FunctionVersion newVersion(
            Function function, int versionNo, String code, String referenceName, String changeLog) {
        String user = UserContextHolder.get() == null ? null : UserContextHolder.get().getUsername();
        return FunctionVersion.builder()
                .functionId(function.getId())
                .functionApi(function.getApi())
                .versionNo(versionNo)
                .code(code)
                .referenceName(referenceName)
                .versionStatus(FunctionStatusEnum.DRAFT)
                .changeLog(changeLog)
                .createBy(user)
                .build();
    }

    private void insertParams(Long functionId, FunctionVersion version, FunctionTypeEnum type) {
        if (type != FunctionTypeEnum.CUSTOMIZE) {
            return;
        }
        List<FunctionParamDTO> parsed = groovyService.parseGroovyCode(version.getCode());
        if (CollectionUtils.isEmpty(parsed)) {
            return;
        }
        functionParamService.saveBatch(parsed.stream()
                .map(parameter -> FunctionParamPO.builder()
                        .functionId(functionId)
                        .functionVersionId(version.getId())
                        .paramName(parameter.getParamName())
                        .paramType(parameter.getParamType())
                        .category(parameter.getCategory())
                        .paramSchema(parameter.getParamSchema())
                        .paramOrder(parameter.getParamOrder())
                        .typeReferenceName(parameter.getReferenceType())
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build())
                .collect(Collectors.toList()));
    }
}
