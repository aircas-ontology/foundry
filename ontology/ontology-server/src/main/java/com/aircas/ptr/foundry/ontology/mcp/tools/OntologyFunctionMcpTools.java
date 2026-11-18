package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.bo.CompositePlanBO;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeValidationBO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeValidateParam;
import com.aircas.ptr.foundry.ontology.model.param.ContainerConfig;
import com.aircas.ptr.foundry.ontology.model.param.ExternalApiConfig;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionListQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionParameter;
import com.aircas.ptr.foundry.ontology.model.param.FunctionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ScriptParseParam;
import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.ScriptParseResultVO;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.aircas.ptr.foundry.ontology.service.ScriptParamParseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 函数算子 MCP 工具（对应 {@code OntologyFunctionController}，REST 前缀 /function）。
 *
 * <p>覆盖函数的执行、列表、创建、更新、详情、版本列表、删除、发布、下线、脚本参数解析、
 * 异步回调、回调结果查询、测试、编排校验与编排编译共 15 个端点。
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集，方法委托既有 Service，
 * 直接返回业务 VO/Page/List/String，不包 RestResult。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyFunctionMcpTools implements McpTools {

    private final FunctionService functionService;
    private final ScriptParamParseService scriptParamParseService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 版本号格式，与 FunctionConstant.VERSION_PATTERN 一致 */
    private static final String VERSION_PATTERN = "^\\d+\\.\\d+\\.\\d+$";

    @Tool(description = """
            执行函数算子（对应 REST 端点 POST /function/execute）。
            当用户需要实际运行某个已发布的函数（自定义脚本/外部API/基础查询/函数编排/容器接口算子）并拿到结果时使用。
            参数：
            - functionApi：必填，函数 api（算子 api 只在空间内唯一，须配合 ontologySpaceId 定位）；
            - version：必填，版本号，格式 x.y.z（如 1.0.0）；
            - ontologySpaceId：必填，对象空间 id；
            - parametersJson：可选，执行入参 JSON 数组字符串，元素形如
              {"paramName":"参数名","paramType":"STRING","paramValue":"值"}；
              paramType 可选值：OBJECT、STRING、INTEGER、LONG、FLOAT、DOUBLE、BOOL、BYTE、SHORT、CHAR、LIST、DATE；
              对象/复合参数可用 fields 子字段数组逐层填值（{"paramName":"obj","paramType":"OBJECT","fields":[...]}），
              LIST 类型时后端按 fields 组装为数组；参数定义可先用 getFunctionDetail 查看。
            返回字符串：保留 FunctionResultVO 外壳的 JSON（含 data 数据体、startTime/endTime 未来最近可见窗口、
            timeWindows 可见窗口列表、taskId 异步任务 id 及脚本自定义透传键）；异步函数返回 taskId 后用
            getFunctionExecuteResult 轮询结果。函数不存在/未发布/参数不匹配时抛错。
            """)
    public String executeFunction(
            @ToolParam(description = "函数 api（空间内唯一）") String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z（如 1.0.0）") String version,
            @ToolParam(description = "对象空间 id") Integer ontologySpaceId,
            @ToolParam(description = "执行入参 JSON 数组字符串，元素 {\"paramName\":\"...\",\"paramType\":\"STRING\",\"paramValue\":...}，可为空", required = false) String parametersJson) {
        requireText(functionApi, "functionApi");
        requireVersion(version);
        requireSpaceId(ontologySpaceId);
        FunctionExecuteParam param = new FunctionExecuteParam();
        param.setFunctionApi(functionApi);
        param.setVersion(version);
        param.setOntologySpaceId(ontologySpaceId);
        param.setParameters(parseParameters(parametersJson));
        // 与 controller 一致：保留 FunctionResultVO 外壳（startTime/endTime/timeWindows 不丢）
        return functionService.executeFunction(param, true);
    }

    @Tool(description = """
            多条件分页查询函数列表（对应 REST 端点 GET /function/list）。只读操作。
            当用户要按空间、名称、类型、发布状态、创建日期筛选浏览函数算子时使用。
            所有筛选参数均可选，不传即不过滤：
            - ontologySpaceId：对象空间 id；
            - displayName：函数名称，模糊搜索；
            - types：函数类型多选，逗号分隔，可选值 CUSTOMIZE（自定义脚本）/ EXTERNAL（外部API）/
              BASIC_QUERY（基础查询）/ COMPOSITE（函数编排）/ CONTAINER（容器），不传查全部类型；
            - excludeTypes：排除的函数类型，逗号分隔，取值同上；与 types 同时传时两者都生效；
            - publishStatus：发布状态，0 未发布 / 1 已发布；
            - startDate / endDate：创建日期范围，格式 yyyy-MM-dd；
            - pageNum：页号，默认 1，必须大于 0；pageSize：每页条数，默认 10，范围 1-1000。
            返回 MyBatis-Plus Page 分页对象：records 为 FunctionInfoVO 列表（functionApi、version、publishStatus、
            displayName、description、model、type、language、ontologySpaceId、createTime、updateTime、
            runningStatus 运行状态 RUNNING/NOT_RUNNING），total 总条数、current 当前页、size 页大小。
            无匹配时 records 为空数组、total=0。
            """)
    public Page<FunctionInfoVO> pageGetFunctions(
            @ToolParam(description = "对象空间 id，不传查全部空间", required = false) Integer ontologySpaceId,
            @ToolParam(description = "函数名称（模糊搜索），不传不过滤", required = false) String displayName,
            @ToolParam(description = "函数类型多选，逗号分隔：CUSTOMIZE/EXTERNAL/BASIC_QUERY/COMPOSITE/CONTAINER，不传查全部", required = false) String types,
            @ToolParam(description = "排除的函数类型，逗号分隔，取值同 types，不传不排除", required = false) String excludeTypes,
            @ToolParam(description = "发布状态：0 未发布，1 已发布，不传查全部", required = false) Integer publishStatus,
            @ToolParam(description = "创建开始日期，格式 yyyy-MM-dd", required = false) String startDate,
            @ToolParam(description = "创建结束日期，格式 yyyy-MM-dd", required = false) String endDate,
            @ToolParam(description = "页号，从 1 开始，默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 10，最大 1000", required = false) Integer pageSize) {
        FunctionListQueryParam param = new FunctionListQueryParam();
        param.setOntologySpaceId(ontologySpaceId);
        param.setDisplayName(displayName);
        param.setTypes(parseFunctionTypes(types, "types"));
        param.setExcludeTypes(parseFunctionTypes(excludeTypes, "excludeTypes"));
        param.setPublishStatus(publishStatus);
        param.setStartDate(startDate);
        param.setEndDate(endDate);
        int num = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 10 : pageSize;
        if (num < 1) {
            throw new IllegalArgumentException("pageNum 必须大于 0");
        }
        if (size < 1 || size > 1000) {
            throw new IllegalArgumentException("pageSize 必须在 1-1000 之间");
        }
        param.setPageNum(num);
        param.setPageSize(size);
        return functionService.getFunctions(param);
    }

    @Tool(description = """
            创建函数算子（对应 REST 端点 POST /function）。
            当用户需要新建自定义脚本、外部API、基础查询、函数编排或容器算子时使用。
            必填参数：functionApi（创建后不可修改）、version（格式 x.y.z，同一 api 下不可重复且必须大于已有最大版本号；
            容器算子恒为 1.0.0）、displayName（函数名称，创建后不可修改）、type（函数类型：CUSTOMIZE/EXTERNAL/
            BASIC_QUERY/COMPOSITE/CONTAINER）、ontologySpaceId（所属对象空间 id，算子只在空间内唯一）。
            可选参数：
            - description：描述；
            - language：脚本语言，仅 CUSTOMIZE 有效，可选 GROOVY/JAVA/PYTHON/TYPESCRIPT，不传默认 GROOVY，创建后不可修改；
            - model：函数模型，可选 PYTORCH/TENSORFLOW/OTHER/BASIC，不传按 type 自动推断（BASIC_QUERY→BASIC，其他→OTHER）；
            - code：自定义函数源码（CUSTOMIZE）；referenceName：外部函数全限定名（EXTERNAL 引用内部方法时）；
            - queryConfigJson：基础查询算子配置 JSON（仅 BASIC_QUERY 必填），形如
              {"aggFunc":"COUNT","targetProperty":"变量名","filters":{...}}，aggFunc 可选 SUM/COUNT/AVG/MAX/MIN，
              不传为 query 模式（无聚合）；targetProperty/filters 中的属性名均为变量名，执行时经 variableBindings 映射；
            - parametersJson：参数定义 JSON 数组（CUSTOMIZE 推荐传入，前端 IDE 解析 handle 签名所得），元素形如
              {"paramName":"...","paramType":"STRING","category":"INPUT","paramOrder":1,"description":"...","fields":[...]}，
              category 取 INPUT（入参）/OUTPUT（出参），paramType 取 OBJECT/STRING/INTEGER/LONG/FLOAT/DOUBLE/BOOL/BYTE/SHORT/CHAR/LIST/DATE；
              Groovy 不传可回退后端反射解析；
            - compositeConfigJson：函数编排定义 JSON（仅 COMPOSITE 必填），含 kind/nodes/edges/inputMappings/
              outputMappings/inputParameters/outputParameters/graph，可先用 validateCompositeFunction 校验；
            - apiConfigJson：外部API算子配置 JSON（仅 EXTERNAL 必填），形如 {"endpoint":"http://host/api/{id}",
              "method":"GET","authType":"none","inputs":[{"name":"id","location":"path"}],
              "outputs":[{"name":"data.amount"}]}，method 取 GET/POST/PUT/DELETE（默认 POST），authType 取 none/apiKey，
              inputs.location 取 path/query/header/body（缺省 body）；
            - containerConfigJson：容器算子配置 JSON（仅 CONTAINER 必填），含 image（上传镜像返回的镜像信息）、
              interfaceDoc（接口文档信息）、runCommand（完整 docker run 命令）、selectedInterfaces（勾选的接口列表，非空）；
            - basedOnOldVersion：true 表示基于旧版本创建（允许相同 api 但 api+version 不重复）；false/不传为全新创建（api 不能与任何已有函数重复）；
            - rateLimitTemplateId：限流配置模板 id，不传用系统默认模板，创建后不可修改。
            副作用：写入函数表与参数表；容器类型会生成 N 条接口算子 + 1 条运行实例。成功返回中文结果说明。
            """)
    public String createFunction(
            @ToolParam(description = "函数 api（创建后不可修改）") String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z（如 1.0.0），容器算子恒为 1.0.0") String version,
            @ToolParam(description = "函数名称（创建后不可修改）") String displayName,
            @ToolParam(description = "函数类型：CUSTOMIZE/EXTERNAL/BASIC_QUERY/COMPOSITE/CONTAINER") String type,
            @ToolParam(description = "所属对象空间 id") Integer ontologySpaceId,
            @ToolParam(description = "函数描述", required = false) String description,
            @ToolParam(description = "脚本语言（仅 CUSTOMIZE 有效）：GROOVY/JAVA/PYTHON/TYPESCRIPT，不传默认 GROOVY", required = false) String language,
            @ToolParam(description = "函数模型：PYTORCH/TENSORFLOW/OTHER/BASIC，不传按 type 自动推断", required = false) String model,
            @ToolParam(description = "自定义函数源码（CUSTOMIZE 类型）", required = false) String code,
            @ToolParam(description = "外部函数全限定名（EXTERNAL 引用内部方法时）", required = false) String referenceName,
            @ToolParam(description = "基础查询算子配置 JSON 对象字符串（仅 BASIC_QUERY 必填）", required = false) String queryConfigJson,
            @ToolParam(description = "参数定义 JSON 数组字符串（CUSTOMIZE 推荐传入），元素字段见工具描述", required = false) String parametersJson,
            @ToolParam(description = "函数编排定义 JSON 对象字符串（仅 COMPOSITE 必填）", required = false) String compositeConfigJson,
            @ToolParam(description = "外部API算子配置 JSON 对象字符串（仅 EXTERNAL 必填）", required = false) String apiConfigJson,
            @ToolParam(description = "容器算子配置 JSON 对象字符串（仅 CONTAINER 必填）", required = false) String containerConfigJson,
            @ToolParam(description = "是否基于旧版本创建：true 允许相同 api 但 api+version 不重复；false/不传为全新创建", required = false) Boolean basedOnOldVersion,
            @ToolParam(description = "限流配置模板 id，不传用默认模板，创建后不可修改", required = false) Long rateLimitTemplateId) {
        requireText(functionApi, "functionApi");
        requireVersion(version);
        requireText(displayName, "displayName");
        requireText(type, "type");
        requireSpaceId(ontologySpaceId);
        FunctionCreateParam param = new FunctionCreateParam();
        param.setFunctionApi(functionApi);
        param.setVersion(version);
        param.setDisplayName(displayName);
        param.setDescription(description);
        param.setType(parseFunctionType(type));
        param.setLanguage(parseLanguage(language));
        param.setModel(parseModel(model));
        param.setCode(code);
        param.setReferenceName(referenceName);
        param.setOntologySpaceId(ontologySpaceId);
        param.setQueryConfig(parseJson(queryConfigJson, BasicQueryConfig.class, "queryConfigJson"));
        param.setParameters(parseParamDefs(parametersJson));
        param.setCompositeConfig(parseJson(compositeConfigJson, CompositeConfig.class, "compositeConfigJson"));
        param.setApiConfig(parseJson(apiConfigJson, ExternalApiConfig.class, "apiConfigJson"));
        param.setContainerConfig(parseJson(containerConfigJson, ContainerConfig.class, "containerConfigJson"));
        param.setBasedOnOldVersion(basedOnOldVersion);
        param.setRateLimitTemplateId(rateLimitTemplateId);
        functionService.createFunction(param);
        return "函数创建成功：" + displayName + "（functionApi=" + functionApi + "，version=" + version + "）";
    }

    @Tool(description = """
            更新函数算子（对应 REST 端点 PUT /function）。
            当需要修改某个未发布版本的描述、代码、参数定义或类型专属配置时使用。
            必填参数：functionApi、version（格式 x.y.z，仅用于定位要修改的未发布版本，修改不会变更版本号，
            需要新版本请走 createFunction）、ontologySpaceId（对象空间 id）。
            可选参数：description 描述；displayName 传入会被忽略（函数名称不可变更）；code 自定义函数源码；
            referenceName 外部函数全限定名；model 函数模型（PYTORCH/TENSORFLOW/OTHER/BASIC，不传保留原值）；
            queryConfigJson 基础查询配置（BASIC_QUERY）；parametersJson 参数定义 JSON 数组（结构同 createFunction，
            非 Groovy 语言依赖此字段重建参数表）；compositeConfigJson 编排定义（COMPOSITE）；
            apiConfigJson 外部API配置（EXTERNAL，未重新提交时沿用库中已存配置，避免被清空）；
            rateLimitTemplateId 限流模板 id（不传不修改；修改后同一 api 的所有版本同步更新）。
            注意：函数 api、名称、类型、语言创建后不可修改；已发布版本不允许直接修改。
            副作用：更新函数表并重建参数定义。成功返回中文结果说明。
            """)
    public String updateFunction(
            @ToolParam(description = "函数 api（空间内唯一）") String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z，定位要修改的未发布版本") String version,
            @ToolParam(description = "对象空间 id") Integer ontologySpaceId,
            @ToolParam(description = "函数描述", required = false) String description,
            @ToolParam(description = "函数名称（修改时不可变更，此字段忽略）", required = false) String displayName,
            @ToolParam(description = "自定义函数源码（CUSTOMIZE 类型）", required = false) String code,
            @ToolParam(description = "外部函数全限定名（EXTERNAL 引用内部方法时）", required = false) String referenceName,
            @ToolParam(description = "函数模型：PYTORCH/TENSORFLOW/OTHER/BASIC，不传保留原值", required = false) String model,
            @ToolParam(description = "基础查询算子配置 JSON 对象字符串（BASIC_QUERY 类型）", required = false) String queryConfigJson,
            @ToolParam(description = "参数定义 JSON 数组字符串，元素字段见 createFunction 描述", required = false) String parametersJson,
            @ToolParam(description = "函数编排定义 JSON 对象字符串（COMPOSITE 类型）", required = false) String compositeConfigJson,
            @ToolParam(description = "外部API算子配置 JSON 对象字符串（EXTERNAL 类型），不传沿用库中已存配置", required = false) String apiConfigJson,
            @ToolParam(description = "限流配置模板 id，不传不修改；修改后同一 api 所有版本同步更新", required = false) Long rateLimitTemplateId) {
        requireText(functionApi, "functionApi");
        requireVersion(version);
        requireSpaceId(ontologySpaceId);
        FunctionUpdateParam param = new FunctionUpdateParam();
        param.setFunctionApi(functionApi);
        param.setVersion(version);
        param.setOntologySpaceId(ontologySpaceId);
        param.setDescription(description);
        param.setDisplayName(displayName);
        param.setCode(code);
        param.setReferenceName(referenceName);
        param.setModel(parseModel(model));
        param.setQueryConfig(parseJson(queryConfigJson, BasicQueryConfig.class, "queryConfigJson"));
        param.setParameters(parseParamDefs(parametersJson));
        param.setCompositeConfig(parseJson(compositeConfigJson, CompositeConfig.class, "compositeConfigJson"));
        param.setApiConfig(parseJson(apiConfigJson, ExternalApiConfig.class, "apiConfigJson"));
        param.setRateLimitTemplateId(rateLimitTemplateId);
        functionService.updateFunction(param);
        return "函数更新成功：functionApi=" + functionApi + "，version=" + version;
    }

    @Tool(description = """
            查询函数详情（对应 REST 端点 GET /function/detail）。只读操作。
            当需要查看某个函数的完整定义（参数列表、源码、类型专属配置）时使用，也是执行/测试/修改函数前的推荐取数入口。
            参数：spaceId 对象空间 id（必传，算子 api 只在空间内唯一）；functionApi 函数 api；version 版本号（格式 x.y.z）。
            返回 FunctionDetailVO：在列表字段（functionApi、version、publishStatus、displayName、description、model、
            type、language、ontologySpaceId、createTime、updateTime、runningStatus）基础上附带 params 参数定义列表、
            code 源码（CUSTOMIZE）、referenceName 全限定名（EXTERNAL）、queryConfig 基础查询配置（BASIC_QUERY）、
            compositeConfig 编排配置（COMPOSITE）、apiConfig 外部API配置（EXTERNAL）、dockerConfig 容器配置（CONTAINER）。
            函数不存在时抛错。
            """)
    public FunctionDetailVO getFunctionDetail(
            @ToolParam(description = "对象空间 id（必传，算子 api 只在空间内唯一）") Integer spaceId,
            @ToolParam(description = "函数 api") String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z（如 1.0.0）") String version) {
        requireSpaceId(spaceId);
        requireText(functionApi, "functionApi");
        requireVersion(version);
        return functionService.getFunctionDetailByApi(spaceId, functionApi, version);
    }

    @Tool(description = """
            根据函数 api 查询版本号列表（对应 REST 端点 GET /function/versions）。只读操作。
            当需要知道某个函数有哪些版本（如选择要执行/查看/删除的版本）时使用。
            参数：spaceId 对象空间 id（必传，算子 api 只在空间内唯一）；functionApi 函数 api。
            返回版本号字符串列表（格式 x.y.z）；函数不存在或没有版本时返回空列表。
            """)
    public List<String> getFunctionVersions(
            @ToolParam(description = "对象空间 id（必传，算子 api 只在空间内唯一）") Integer spaceId,
            @ToolParam(description = "函数 api") String functionApi) {
        requireSpaceId(spaceId);
        requireText(functionApi, "functionApi");
        return functionService.listVersionsByApi(spaceId, functionApi);
    }

    @Tool(description = """
            删除函数（对应 REST 端点 DELETE /function/delete）。
            当需要移除某个函数版本时使用。
            参数：spaceId 对象空间 id（必传，算子 api 只在空间内唯一）；functionApi 函数 api；version 版本号（格式 x.y.z）。
            限制：会校验函数有没有被对象行为使用到，被行为引用的函数不能删除，需先解除行为引用；
            容器算子删除会联动清理容器文件与运行实例。
            副作用：物理删除该版本函数及其参数定义。成功返回中文结果说明。
            """)
    public String deleteFunction(
            @ToolParam(description = "对象空间 id（必传，算子 api 只在空间内唯一）") Integer spaceId,
            @ToolParam(description = "函数 api") String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z（如 1.0.0）") String version) {
        requireSpaceId(spaceId);
        requireText(functionApi, "functionApi");
        requireVersion(version);
        functionService.deleteByApi(spaceId, functionApi, version);
        return "函数删除成功：functionApi=" + functionApi + "，version=" + version;
    }

    @Tool(description = """
            发布函数（对应 REST 端点 POST /function/publish）。
            当函数创建/修改并测试通过、需要正式对外可用（可被行为绑定与执行）时使用。
            参数：spaceId 对象空间 id（必传，算子 api 只在空间内唯一）；functionApi 函数 api；version 版本号（格式 x.y.z）。
            副作用：把该版本的发布状态置为已发布（publishStatus=1）。成功返回中文结果说明。
            """)
    public String publishFunction(
            @ToolParam(description = "对象空间 id（必传，算子 api 只在空间内唯一）") Integer spaceId,
            @ToolParam(description = "函数 api") String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z（如 1.0.0）") String version) {
        requireSpaceId(spaceId);
        requireText(functionApi, "functionApi");
        requireVersion(version);
        functionService.publishFunction(spaceId, functionApi, version);
        return "函数发布成功：functionApi=" + functionApi + "，version=" + version;
    }

    @Tool(description = """
            下线函数（对应 REST 端点 POST /function/unpublish）。
            当需要把已发布的函数版本停用（不再对外提供执行）时使用。
            参数：spaceId 对象空间 id（必传，算子 api 只在空间内唯一）；functionApi 函数 api；version 版本号（格式 x.y.z）。
            限制：会校验本空间内是否有行为正在使用该函数版本，被引用时不能下线。
            副作用：把该版本的发布状态置为未发布（publishStatus=0）。成功返回中文结果说明。
            """)
    public String unpublishFunction(
            @ToolParam(description = "对象空间 id（必传，算子 api 只在空间内唯一）") Integer spaceId,
            @ToolParam(description = "函数 api") String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z（如 1.0.0）") String version) {
        requireSpaceId(spaceId);
        requireText(functionApi, "functionApi");
        requireVersion(version);
        functionService.unpublishFunction(spaceId, functionApi, version);
        return "函数下线成功：functionApi=" + functionApi + "，version=" + version;
    }

    @Tool(description = """
            解析脚本输入/输出参数（对应 REST 端点 POST /function/parse_params）。只读操作，不落库。
            当用户给了一段脚本源码、需要在创建/更新 CUSTOMIZE 函数前自动提取 handle 签名的入参/出参定义时使用。
            后端 AST 解析：Java/Groovy 在主程序进程内解析，Python/TypeScript 走独立语言 runner。
            参数：
            - language：必填，脚本语言，可选 GROOVY/JAVA/PYTHON/TYPESCRIPT；
            - code：必填，脚本源码，最长 200000 字符。
            返回 ScriptParseResultVO：status 解析状态（OK 成功 / SYNTAX_ERROR 语法错误 / NO_HANDLE 缺 handle 入口 /
            RUNNER_NOT_CONFIGURED runner 未部署 / RUNNER_ERROR runner 报错 / PARSE_EXCEPTION 解析异常）、
            parsed 是否成功定位到参数（false 时需手动声明参数）、parameters 解析出的参数定义列表
            （与 createFunction/updateFunction 的 parametersJson 结构一致，入参在前出参在后，可直接提交保存）、
            message 解析提示（失败原因或动态写法提示）。
            """)
    public ScriptParseResultVO parseFunctionParams(
            @ToolParam(description = "脚本语言：GROOVY/JAVA/PYTHON/TYPESCRIPT") String language,
            @ToolParam(description = "脚本源码（最长 200000 字符）") String code) {
        requireText(language, "language");
        requireText(code, "code");
        if (code.length() > 200000) {
            throw new IllegalArgumentException("code 长度超过上限（200000 字符）");
        }
        ScriptParseParam param = new ScriptParseParam();
        param.setLanguage(parseLanguageRequired(language));
        param.setCode(code);
        return scriptParamParseService.parseParams(param);
    }

    @Tool(description = """
            异步函数执行结果回调（对应 REST 端点 POST /function/callback）。
            供函数执行回调，一般不由对话直接调用：这是异步函数（如容器/runner 执行完毕后）由执行器机器回写结果的入口，
            正常对话中查询异步结果请用 getFunctionExecuteResult。
            参数 resultJson：必填，函数执行结果 JSON 对象字符串（FunctionResultVO 结构），字段：
            - taskId：异步任务 id（必填，用于关联执行记录）；
            - data：结果数据体（任意 JSON）；
            - startTime / endTime：未来最近可见窗口起止时间（毫秒时间戳）；
            - timeWindows：一段时间范围内所有可见窗口列表；
            - description：描述；其余自定义键原样透传。
            副作用：按 taskId 回写异步执行结果与任务状态。成功返回中文结果说明。
            """)
    public String functionCallback(
            @ToolParam(description = "函数执行结果 JSON 对象字符串（FunctionResultVO 结构，taskId 必填）") String resultJson) {
        requireText(resultJson, "resultJson");
        FunctionResultVO result = parseJson(resultJson, FunctionResultVO.class, "resultJson");
        if (result == null) {
            throw new IllegalArgumentException("resultJson 不能为空，应为 FunctionResultVO 结构的 JSON 对象字符串");
        }
        functionService.callback(result);
        return "函数执行结果回调处理成功，taskId=" + result.getTaskId();
    }

    @Tool(description = """
            根据 taskId 查询异步函数执行结果（对应 REST 端点 GET /function/callback_result）。只读操作。
            当 executeFunction 返回了 taskId（异步执行）、需要轮询/查看任务最终结果时使用。
            参数：taskId 必填，异步任务 id（executeFunction 返回的 FunctionResultVO.taskId）。
            返回 FunctionExecuteResultVO：taskId 任务 id、result 执行结果（FunctionResultVO：data 数据体、
            startTime/endTime/timeWindows 可见窗口、description 及脚本自定义透传键）、functionApi 函数 api、
            actionApi 关联行为 api、functionParam 执行入参、taskStatus 任务状态（PENDING 执行中 / COMPLETED 已完成 / FAILED 失败）。
            """)
    public FunctionExecuteResultVO getFunctionExecuteResult(
            @ToolParam(description = "异步任务 id（executeFunction 返回的 taskId）") String taskId) {
        requireText(taskId, "taskId");
        return functionService.getExecuteResult(taskId);
    }

    @Tool(description = """
            函数测试（对应 REST 端点 POST /function/test），支持「已存库测试」和「草稿测试」两种模式。
            当需要在正式发布前验证函数行为时使用。
            模式判定：functionApi 非空 → 已存库测试（按 api+version 查库取定义，须传 version 与 ontologySpaceId）；
            functionApi 为空且 type 非空 → 草稿测试（直接用传入的 code/apiConfig 执行，不落库，type 取 CUSTOMIZE/EXTERNAL）。
            按函数类型使用不同字段：
            - BASIC_QUERY：ontologyIdentifier（测试对象 id，必填）+ variableBindingsJson（变量绑定 JSON 对象，
              key=创建时的变量名，value=实际属性 apiName，如 {"amount":"price"}，必填）+ pageNum/pageSize（query 模式分页，默认 1/10）；
            - CUSTOMIZE/EXTERNAL：parametersJson（执行入参 JSON 数组，结构同 executeFunction，必填）；
              CUSTOMIZE 草稿另需 code（源码）与 language（GROOVY/JAVA/PYTHON/TYPESCRIPT，不传默认 GROOVY）；
              EXTERNAL 草稿另需 apiConfigJson（外部API配置，结构同 createFunction）。
            返回测试结果对象（随函数类型不同：脚本/外部API为 FunctionResultVO 结构，基础查询为查询/聚合结果）。
            """)
    public Object testFunction(
            @ToolParam(description = "函数 api（已存库测试必填；草稿测试不传）", required = false) String functionApi,
            @ToolParam(description = "版本号，格式 x.y.z（已存库测试必填；草稿测试不传）", required = false) String version,
            @ToolParam(description = "对象空间 id（已存库测试必传；草稿测试可不传）", required = false) Integer ontologySpaceId,
            @ToolParam(description = "函数类型（草稿测试必填：CUSTOMIZE/EXTERNAL；已存库测试忽略）", required = false) String type,
            @ToolParam(description = "脚本源码（CUSTOMIZE 草稿测试必填）", required = false) String code,
            @ToolParam(description = "脚本语言（CUSTOMIZE 草稿测试）：GROOVY/JAVA/PYTHON/TYPESCRIPT，不传默认 GROOVY", required = false) String language,
            @ToolParam(description = "外部API配置 JSON 对象字符串（EXTERNAL 草稿测试必填）", required = false) String apiConfigJson,
            @ToolParam(description = "测试对象 id（BASIC_QUERY 类型必填）", required = false) String ontologyIdentifier,
            @ToolParam(description = "变量绑定 JSON 对象字符串，如 {\"amount\":\"price\"}（BASIC_QUERY 类型必填）", required = false) String variableBindingsJson,
            @ToolParam(description = "分页号，默认 1（BASIC_QUERY query 模式有效）", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 10，最大 1000（BASIC_QUERY query 模式有效）", required = false) Integer pageSize,
            @ToolParam(description = "执行入参 JSON 数组字符串，结构同 executeFunction（CUSTOMIZE/EXTERNAL 类型必填）", required = false) String parametersJson) {
        if (StringUtils.isNotBlank(version) && !version.matches(VERSION_PATTERN)) {
            throw new IllegalArgumentException("版本号格式必须为 x.y.z（如 1.0.0）");
        }
        int num = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 10 : pageSize;
        if (num < 1) {
            throw new IllegalArgumentException("pageNum 必须大于 0");
        }
        if (size < 1 || size > 1000) {
            throw new IllegalArgumentException("pageSize 必须在 1-1000 之间");
        }
        FunctionTestParam param = new FunctionTestParam();
        param.setFunctionApi(functionApi);
        param.setVersion(version);
        param.setOntologySpaceId(ontologySpaceId);
        param.setType(StringUtils.isBlank(type) ? null : parseFunctionType(type));
        param.setCode(code);
        param.setLanguage(parseLanguage(language));
        param.setApiConfig(parseJson(apiConfigJson, ExternalApiConfig.class, "apiConfigJson"));
        param.setOntologyIdentifier(ontologyIdentifier);
        if (StringUtils.isNotBlank(variableBindingsJson)) {
            param.setVariableBindings(parseJson(variableBindingsJson,
                    new TypeReference<Map<String, String>>() {
                    }, "variableBindingsJson"));
        }
        param.setPageNum(num);
        param.setPageSize(size);
        param.setParameters(parseParameters(parametersJson));
        return functionService.testFunction(param);
    }

    @Tool(description = """
            校验函数编排定义（对应 REST 端点 POST /function/composite/validate）。只读操作，不落库。
            当用户在画布编辑编排（COMPOSITE 函数）过程中需要实时校验、或发布前复核已落库编排时使用。
            参数（functionApi 与 compositeConfigJson 至少传一个）：
            - compositeConfigJson：待校验的编排定义 JSON 对象字符串（kind/nodes/edges/inputMappings/outputMappings/
              inputParameters/outputParameters/graph，结构同 createFunction）；
            - functionApi：编排函数 api；compositeConfigJson 为空时按它读取库中已落库的定义再校验；
            - ontologySpaceId：对象空间 id，按 functionApi 读库取定义时必传（算子 api 只在空间内唯一）。
            返回 CompositeValidationBO：valid 是否校验通过、errors 校验错误信息列表、warnings 校验告警信息列表。
            校验失败（valid=false）不抛错，按 errors 逐条修正编排即可。
            """)
    public CompositeValidationBO validateCompositeFunction(
            @ToolParam(description = "编排函数 api，与 compositeConfigJson 至少传一个", required = false) String functionApi,
            @ToolParam(description = "待校验的编排定义 JSON 对象字符串，为空时取 functionApi 已落库的定义", required = false) String compositeConfigJson,
            @ToolParam(description = "对象空间 id（按 functionApi 读库取定义时必传）", required = false) Integer ontologySpaceId) {
        CompositeValidateParam param = new CompositeValidateParam();
        param.setFunctionApi(functionApi);
        param.setCompositeConfig(parseJson(compositeConfigJson, CompositeConfig.class, "compositeConfigJson"));
        param.setOntologySpaceId(ontologySpaceId);
        return functionService.validateComposite(param);
    }

    @Tool(description = """
            编译已落库的函数编排定义，返回 LiteFlow 执行链 EL（对应 REST 端点 GET /function/composite/el）。只读操作。
            当需要查看某个编排函数（COMPOSITE）编译后的执行计划（排查编排执行问题、理解节点编排结构）时使用。
            参数：spaceId 对象空间 id（必传，算子 api 只在空间内唯一）；functionApi 编排函数 api。
            返回 CompositePlanBO：chainName 主链名称（函数 api）、el 主链 EL、subChainEls 子链 EL（链名→EL，循环体等）、
            nodes 节点定义（LiteFlow 节点 id→节点运行期定义）、loopRounds 循环逐轮重放登记项、
            inputParameterNames 编排入参名列表、outputParameterNames 编排出参名列表。
            函数不存在、不是编排类型或编排定义为空时抛错。
            """)
    public CompositePlanBO compileCompositeFunction(
            @ToolParam(description = "对象空间 id（必传，算子 api 只在空间内唯一）") Integer spaceId,
            @ToolParam(description = "编排函数 api") String functionApi) {
        requireSpaceId(spaceId);
        requireText(functionApi, "functionApi");
        return functionService.compileComposite(spaceId, functionApi);
    }

    private void requireText(String value, String paramName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
    }

    private void requireSpaceId(Integer spaceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId/ontologySpaceId（对象空间 id）不能为空，算子 api 只在空间内唯一");
        }
    }

    private void requireVersion(String version) {
        requireText(version, "version");
        if (!version.matches(VERSION_PATTERN)) {
            throw new IllegalArgumentException("版本号格式必须为 x.y.z（如 1.0.0）");
        }
    }

    private <T> T parseJson(String json, Class<T> clazz, String paramName) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(paramName + " JSON 解析失败：" + e.getMessage());
        }
    }

    private <T> T parseJson(String json, TypeReference<T> type, String paramName) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(paramName + " JSON 解析失败：" + e.getMessage());
        }
    }

    private List<FunctionParameter> parseParameters(String parametersJson) {
        if (StringUtils.isBlank(parametersJson)) {
            return null;
        }
        return parseJson(parametersJson, new TypeReference<List<FunctionParameter>>() {
        }, "parametersJson");
    }

    private List<FunctionParamDTO> parseParamDefs(String parametersJson) {
        if (StringUtils.isBlank(parametersJson)) {
            return null;
        }
        return parseJson(parametersJson, new TypeReference<List<FunctionParamDTO>>() {
        }, "parametersJson");
    }

    private List<FunctionTypeEnum> parseFunctionTypes(String types, String paramName) {
        if (StringUtils.isBlank(types)) {
            return null;
        }
        List<FunctionTypeEnum> result = new ArrayList<>();
        for (String t : types.split(",")) {
            if (StringUtils.isNotBlank(t)) {
                result.add(parseFunctionType(t.trim()));
            }
        }
        return result.isEmpty() ? null : result;
    }

    private FunctionTypeEnum parseFunctionType(String type) {
        try {
            return FunctionTypeEnum.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的函数类型：" + type
                    + "，可选值：CUSTOMIZE、EXTERNAL、BASIC_QUERY、COMPOSITE、CONTAINER");
        }
    }

    private FunctionLanguageEnum parseLanguage(String language) {
        if (StringUtils.isBlank(language)) {
            return null;
        }
        return parseLanguageRequired(language);
    }

    private FunctionLanguageEnum parseLanguageRequired(String language) {
        try {
            return FunctionLanguageEnum.valueOf(language);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的脚本语言：" + language
                    + "，可选值：GROOVY、JAVA、PYTHON、TYPESCRIPT");
        }
    }

    private FunctionModelEnum parseModel(String model) {
        if (StringUtils.isBlank(model)) {
            return null;
        }
        try {
            return FunctionModelEnum.valueOf(model);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的函数模型：" + model
                    + "，可选值：PYTORCH、TENSORFLOW、OTHER、BASIC");
        }
    }
}