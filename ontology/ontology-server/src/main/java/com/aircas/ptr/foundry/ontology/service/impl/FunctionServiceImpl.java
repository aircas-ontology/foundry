package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.util.PreconditionUtils;
import com.aircas.ptr.foundry.ontology.context.UserContextHolder;
import com.aircas.ptr.foundry.ontology.model.dto.ActionContextInfoDTO;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.enums.*;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionExecuteResult;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionExecuteResultMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.FunctionMapper;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.OntologyActionMapper;
import com.aircas.ptr.foundry.ontology.service.FunctionParamService;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
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
    private FunctionParamService functionParamService;
    @Resource
    private GroovyService groovyService;
    @Lazy
    @Resource
    private EntityServiceImpl entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FunctionDetailVO getFunctionDetailByApi(String api, Long functionVersionId) {
        return detail(requireVersion(api, functionVersionId));
    }

    private FunctionDetailVO detail(Function function) {
        List<FunctionParameterVO> parameters = functionParamService.list(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionVersionId, function.getId()).orderByAsc(FunctionParamPO::getParamOrder)).stream().map(parameter -> FunctionParameterVO.builder().paramId(parameter.getId()).paramName(parameter.getParamName()).category(parameter.getCategory()).paramOrder(parameter.getParamOrder()).paramSchema(parameter.getParamSchema()).paramType(parameter.getParamType()).description(parameter.getDescription()).build()).collect(Collectors.toList());
        return FunctionDetailVO.builder().functionVersionId(function.getId()).functionApi(function.getApi()).version(function.getVersion()).versionStatus(function.getVersionStatus()).displayName(function.getDisplayName()).description(function.getDescription()).type(function.getType()).model(function.getModel()).code(function.getCode()).referenceName(function.getReferenceName()).params(parameters).build();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void deleteByApi(String api) {
        long versionCount = count(new LambdaQueryWrapper<Function>().eq(Function::getApi, api).ne(Function::getStatus, Status.DELETE.getValue()));
        PreconditionUtils.checkArgument(versionCount > 0, "函数 API 不存在：" + api, HttpStatus.BAD_REQUEST);
        Long referenceCount = ontologyActionMapper.selectCount(new LambdaQueryWrapper<OntologyAction>().eq(OntologyAction::getFunctionApi, api));
        PreconditionUtils.checkArgument(referenceCount == 0, "函数任一版本已被行为绑定，不能删除：" + api, HttpStatus.BAD_REQUEST);
        update(new LambdaUpdateWrapper<Function>().eq(Function::getApi, api).set(Function::getStatus, Status.DELETE.getValue()));
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public FunctionVersionCreatedVO createFunction(FunctionCreateParam param) {
        String version = StringUtils.defaultIfBlank(param.getVersion(), "1.0.0");
        long duplicateCount = count(new LambdaQueryWrapper<Function>().eq(Function::getApi, param.getFunctionApi()).eq(Function::getVersion, version));
        PreconditionUtils.checkArgument(duplicateCount == 0, "函数 API 与版本号已存在：" + param.getFunctionApi() + "#" + version, HttpStatus.BAD_REQUEST);
        validateExecutable(param.getType(), param.getCode(), param.getReferenceName());
        String user = UserContextHolder.get() == null ? null : UserContextHolder.get().getUsername();
        Function function = Function.builder().api(param.getFunctionApi()).version(version).versionStatus(FunctionStatusEnum.PUBLISHED).description(param.getDescription()).displayName(param.getDisplayName()).type(param.getType()).model(param.getModel()).code(param.getCode()).referenceName(param.getReferenceName()).createBy(user).publishTime(new Date()).status(Status.ENABLE.getValue()).build();
        save(function);
        insertParams(function);
        return FunctionVersionCreatedVO.builder().functionVersionId(function.getId()).version(function.getVersion()).build();
    }

    @Override
    @Transactional(value = "mainTransactionManager")
    public void updateFunction(FunctionUpdateParam param) {
        Function function = requireVersion(param.getFunctionApi(), param.getFunctionVersionId());
        PreconditionUtils.checkArgument(function.getVersion().equals(param.getVersion()), "请求版本号与 functionVersionId 对应版本不一致", HttpStatus.BAD_REQUEST);
        validateExecutable(function.getType(), param.getCode(), param.getReferenceName());
        function.setDisplayName(param.getDisplayName()).setDescription(param.getDescription()).setModel(param.getModel()).setCode(param.getCode()).setReferenceName(param.getReferenceName());
        updateById(function);
        functionParamService.remove(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionVersionId, function.getId()));
        insertParams(function);
    }

    private void validateExecutable(FunctionTypeEnum type, String code, String referenceName) {
        if (type == FunctionTypeEnum.CUSTOMIZE) {
            groovyService.parseGroovyCode(code);
        } else {
            PreconditionUtils.checkArgument(StringUtils.isNotBlank(referenceName), "外部函数引用不能为空", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public Page<FunctionVersionVO> listVersions(String api, Integer pageNum, Integer pageSize) {
        ensureApiExists(api);
        Page<Function> page = page(new Page<Function>(pageNum, pageSize).addOrder(OrderItem.desc("create_time"), OrderItem.desc("id")), new LambdaQueryWrapper<Function>().eq(Function::getApi, api).ne(Function::getStatus, Status.DELETE.getValue()));
        List<FunctionVersionVO> records = page.getRecords().stream().map(function -> FunctionVersionVO.builder().functionVersionId(function.getId()).functionApi(function.getApi()).version(function.getVersion()).versionStatus(function.getVersionStatus()).changeLog(function.getChangeLog()).createBy(function.getCreateBy()).publishTime(function.getPublishTime()).createTime(function.getCreateTime()).build()).collect(Collectors.toList());
        return new Page<FunctionVersionVO>(pageNum, pageSize).setTotal(page.getTotal()).setRecords(records);
    }

    @Override
    public Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize) {
        Page<Function> page = page(new Page<Function>(pageNum, pageSize).addOrder(OrderItem.desc("create_time"), OrderItem.desc("id")), new LambdaQueryWrapper<Function>().ne(Function::getStatus, Status.DELETE.getValue()));
        List<FunctionInfoVO> records = page.getRecords().stream().map(function -> FunctionInfoVO.builder().functionVersionId(function.getId()).functionApi(function.getApi()).version(function.getVersion()).versionStatus(function.getVersionStatus()).displayName(function.getDisplayName()).type(function.getType()).model(function.getModel()).description(function.getDescription()).build()).collect(Collectors.toList());
        return new Page<FunctionInfoVO>(pageNum, pageSize).setTotal(page.getTotal()).setRecords(records);
    }

    @Override
    @SneakyThrows
    public String executeFunction(FunctionExecuteParam param) {
        Function function = requireVersion(param.getFunctionApi(), param.getFunctionVersionId());
        PreconditionUtils.checkArgument(function.getVersionStatus() == FunctionStatusEnum.PUBLISHED, "只能执行已发布版本", HttpStatus.BAD_REQUEST);
        List<FunctionParamPO> inputParameters = functionParamService.list(new LambdaQueryWrapper<FunctionParamPO>().eq(FunctionParamPO::getFunctionVersionId, function.getId()).eq(FunctionParamPO::getCategory, FunctionParamCategoryEnum.INPUT).orderByAsc(FunctionParamPO::getParamOrder));
        Map<String, Object> values = CollectionUtils.isEmpty(param.getParameters()) ? new HashMap<>() : param.getParameters().stream().collect(HashMap::new, (map, parameter) -> map.put(parameter.getParamName(), parameter.getParamValue()), HashMap::putAll);
        String json = groovyService.executeGroovy(function.getId(), function.getApi(), function.getCode(), values, inputParameters);
        FunctionResultVO result = objectMapper.readValue(json, new TypeReference<FunctionResultVO>() {
        });
        if (result != null && StringUtils.isNotEmpty(result.getTaskId())) {
            executeResultMapper.insert(FunctionExecuteResult.builder().taskId(result.getTaskId()).functionApi(function.getApi()).functionVersionId(function.getId()).functionParam(objectMapper.writeValueAsString(param.getParameters())).taskStatus(TaskStatusEnum.PENDING).build());
        }
        return json;
    }

    @Override
    @SneakyThrows
    public FunctionExecuteResultVO getExecuteResult(String taskId) {
        FunctionExecuteResult result = executeResultMapper.selectOne(new LambdaQueryWrapper<FunctionExecuteResult>().eq(FunctionExecuteResult::getTaskId, taskId));
        if (result == null) {
            return null;
        }
        return FunctionExecuteResultVO.builder().taskId(result.getTaskId()).functionApi(result.getFunctionApi()).functionVersionId(result.getFunctionVersionId()).actionApi(result.getActionApi()).functionParam(result.getFunctionParam()).taskStatus(result.getTaskStatus()).result(StringUtils.isEmpty(result.getResult()) ? null : objectMapper.readValue(result.getResult(), new TypeReference<FunctionResultVO>() {
        })).build();
    }

    @Override
    @SneakyThrows
    @Transactional(value = "mainTransactionManager")
    public void callback(FunctionResultVO result) {
        FunctionExecuteResult executeResult = executeResultMapper.selectOne(new LambdaQueryWrapper<FunctionExecuteResult>().eq(FunctionExecuteResult::getTaskId, result.getTaskId()).eq(FunctionExecuteResult::getTaskStatus, TaskStatusEnum.PENDING));
        PreconditionUtils.checkNotNull(executeResult, "task id 不存在或已完成：" + result.getTaskId());
        if (StringUtils.isNotEmpty(executeResult.getActionApi()) && StringUtils.isNotEmpty(executeResult.getActionContextInfo())) {
            entityService.updateEntityPropertyAndRelation(objectMapper.writeValueAsString(result), objectMapper.readValue(executeResult.getActionContextInfo(), new TypeReference<ActionContextInfoDTO>() {
            }));
        }
        executeResult.setTaskStatus(TaskStatusEnum.COMPLETED).setResult(objectMapper.writeValueAsString(result));
        executeResultMapper.updateById(executeResult);
    }

    private void ensureApiExists(String api) {
        long count = count(new LambdaQueryWrapper<Function>().eq(Function::getApi, api).ne(Function::getStatus, Status.DELETE.getValue()));
        PreconditionUtils.checkArgument(count > 0, "函数 API 不存在：" + api, HttpStatus.BAD_REQUEST);
    }

    private Function requireVersion(String api, Long functionVersionId) {
        PreconditionUtils.checkNotNull(functionVersionId, "functionVersionId 不能为空");
        Function function = getById(functionVersionId);
        PreconditionUtils.checkArgument(function != null && function.getApi().equals(api) && !Objects.equals(function.getStatus(), Status.DELETE.getValue()), "函数版本不存在或不属于函数：" + api + "#" + functionVersionId, HttpStatus.BAD_REQUEST);
        return function;
    }

    private void insertParams(Function function) {
        if (function.getType() != FunctionTypeEnum.CUSTOMIZE) {
            return;
        }
        List<FunctionParamDTO> parsed = groovyService.parseGroovyCode(function.getCode());
        if (CollectionUtils.isEmpty(parsed)) {
            return;
        }
        functionParamService.saveBatch(parsed.stream().map(parameter -> FunctionParamPO.builder().functionVersionId(function.getId()).paramName(parameter.getParamName()).paramType(parameter.getParamType()).category(parameter.getCategory()).paramSchema(parameter.getParamSchema()).paramOrder(parameter.getParamOrder()).typeReferenceName(parameter.getReferenceType()).createTime(new Date()).updateTime(new Date()).build()).collect(Collectors.toList()));
    }
}
