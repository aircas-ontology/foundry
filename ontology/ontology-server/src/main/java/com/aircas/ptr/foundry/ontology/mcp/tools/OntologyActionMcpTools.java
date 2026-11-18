package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.TreeBatchExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.TreeExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.ActionCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.ActionTestResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.TreeBatchExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.TreeDefsVO;
import com.aircas.ptr.foundry.ontology.model.vo.TreeExecuteResultVO;
import com.aircas.ptr.foundry.ontology.service.ActionCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对象行为 MCP 工具。
 *
 * <p>提供空间+行为名称的组合精确查询，以及行为/行为树的增删改、发布下线、执行与测试，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyActionMcpTools implements McpTools {

    private final OntologySpaceService ontologySpaceService;
    private final OntologyActionService ontologyActionService;
    private final ActionCategoryService actionCategoryService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            按空间名称 + 行为名称精确查询对象行为。
            当用户已知空间和行为的名称，需要查找行为的详细信息时使用。
            先按空间名定位空间，再在该空间内按行为名精确匹配。
            行为名同时匹配 displayName 和 api（OR 关系）；不传行为名时返回该空间下的全部行为。
            无匹配时返回空列表。
            """)
    public List<OntologyActionInfoVO> queryActions(
            @ToolParam(description = "空间名称（apiName 或 displayName）") String spaceName,
            @ToolParam(description = "行为名称（displayName 或 api），可为空；为空时返回该空间下全部行为", required = false) String actionName) {

        if (StringUtils.isEmpty(spaceName)) {
            return Collections.emptyList();
        }

        var spaces = ontologySpaceService.list(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, spaceName)
                .or().eq(OntologySpace::getDisplayName, spaceName));
        if (CollectionUtils.isEmpty(spaces)) {
            return Collections.emptyList();
        }
        var spaceIds = spaces.stream().map(OntologySpace::getId).collect(Collectors.toList());

        var queryWrapper = new LambdaQueryWrapper<OntologyAction>()
                .eq(OntologyAction::getStatus, ActionStatusEnum.PUBLISHED.getValue())
                .in(OntologyAction::getOntologySpaceId, spaceIds);
        // 行为树不是单个可调用的动作，也没有绑定算子，暴露给 Agent 只会拿到一个无法执行的空工具。
        // 类别为空的存量行为要保留，所以只排掉 TREE，不能写成 ne（SQL 里 NULL 不参与比较）
        queryWrapper.and(w -> w.isNull(OntologyAction::getActionKind)
                .or().ne(OntologyAction::getActionKind, ActionKindEnum.TREE));
        if (StringUtils.isNotEmpty(actionName)) {
            queryWrapper.and(w -> w.eq(OntologyAction::getDisplayName, actionName)
                    .or().eq(OntologyAction::getApi, actionName));
        }

        var actions = ontologyActionService.list(queryWrapper);
        if (CollectionUtils.isEmpty(actions)) {
            return Collections.emptyList();
        }

        return actions.stream()
                .map(a -> OntologyActionInfoVO.builder()
                        .icon(a.getIcon())
                        .ontologyUniqIdentifier(a.ontologyUniqueIdentifierText())
                        .actionApi(a.getApi())
                        .description(a.getDescription())
                        .displayName(a.getDisplayName())
                        .functionApi(a.getFunctionApi())
                        .build())
                .collect(Collectors.toList());
    }

    @Tool(description = """
            根据对象空间 id（spaceId）查询该空间下的行为分类列表（扁平结构，不分页）。
            当用户需要查看空间下所有行为分类的完整列表时使用。
            返回分类 id、名称、父分类 id 等完整信息。
            """)
    public List<ActionCategory> listActionCategories(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        return actionCategoryService.getCategoryList(spaceId);
    }

    @Tool(description = """
            新增对象行为（对应 POST /action）。写操作：真实落库，创建后为草稿状态，需再调用 publishAction 发布后才对智能体和调度可用。
            当用户要为某个对象类型定义可执行的动作（改属性、建关系、调函数）或编排行为树时使用。
            参数 actionJson 为 ActionCreateOrUpdateParam 的 JSON 对象字符串，主要字段：
            - actionApi（必填）：行为 api，仅空间内唯一；displayName（必填）：显示名称；categoryId（必填）：行为分类 id（可先用 listActionCategories 查）。
            - actionKind（必填）：行为类别，可选 BASE 基础行为 / APPLICATION 应用行为 / TREE 行为树。
            - ontologyIdentifier：对象唯一标识（操作范围）；BASE/APPLICATION 必填，TREE 和基础函数行为不传。
            - ontologySpaceId：所属空间 id；行为树没有对象 id 反查空间，必须传。
            - subjectType：actionKind=BASE 时必填，可选 PROPERTY_VALUE 属性值 / LINK 实例关系 / BASIC_FUNCTION 基础函数（BASIC_OPERATOR 已停止新建）。
            - operation：属性值/实例关系行为必填，可选 CREATE 新增 / UPDATE 修改 / DELETE 删除；基础函数不可传。
            - storageGroup：目标存储分组（物理表名，main 主表不允许），仅属性值行为必填。
            - functionApi：绑定的函数 api，应用行为必填（函数须已发布）；属性值/实例关系/基础函数行为不可传。
            - mappingIns：入参映射数组（应用行为用，每行含 outputMode 等）；linkMapping：出参写属性/绑关系的映射，实例关系行为必填。
            - basicFunction：subjectType=BASIC_FUNCTION 时必填的基础函数配置（查询对象、聚合/查询、字段与条件）。
            - treeDefinition：actionKind=TREE 时必填的行为树定义（黑板 + 变量 + 节点树）。
            - icon、description 可选。
            示例（应用行为）：{"ontologyIdentifier":"abcdef","ontologySpaceId":156,"actionApi":"syncSpeed","displayName":"同步速度","categoryId":1,"actionKind":"APPLICATION","functionApi":"calcSpeed"}
            返回创建结果说明字符串；参数缺失或定义不合法时抛错，不落库。
            """)
    public String createAction(
            @ToolParam(description = "ActionCreateOrUpdateParam 的 JSON 对象字符串，字段见工具描述") String actionJson) {
        ActionCreateOrUpdateParam param = parseParamJson(actionJson, ActionCreateOrUpdateParam.class, "actionJson");
        ontologyActionService.createAction(param);
        return "行为创建成功（草稿状态，需发布后生效）：actionApi=" + param.getActionApi() + "，displayName=" + param.getDisplayName();
    }

    @Tool(description = """
            编辑已有对象行为（对应 PUT /action）。写操作：真实落库覆盖行为定义。
            当用户要修改行为的名称、参数映射、函数绑定或行为树定义时使用。
            注意：已被行为调度绑定的行为不可直接编辑，需先调用 stopScheduling 暂停相关调度。
            参数 actionJson 为 ActionCreateOrUpdateParam 的 JSON 对象字符串，字段结构与 createAction 完全一致
            （actionApi 定位要编辑的行为，必填；displayName、categoryId、actionKind 必填；其余按行为类别选填），
            编辑是整体覆盖，未传的可选字段会被清空，建议先用 getActionByApi 取详情后修改再提交。
            返回更新结果说明字符串。
            """)
    public String updateAction(
            @ToolParam(description = "ActionCreateOrUpdateParam 的 JSON 对象字符串，字段见工具描述") String actionJson) {
        ActionCreateOrUpdateParam param = parseParamJson(actionJson, ActionCreateOrUpdateParam.class, "actionJson");
        ontologyActionService.updateAction(param);
        return "行为更新成功：actionApi=" + param.getActionApi() + "，displayName=" + param.getDisplayName();
    }

    @Tool(description = """
            发布行为（对应 PUT /action/publish）。写操作：把行为状态从草稿(2)改为已发布(1)，发布后对智能体和调度可用。
            当用户新建/编辑行为后要求上线生效时使用。
            绑算子的应用行为要求所绑函数已发布，否则报错；行为树同样走这里，发布前会用库里的树定义重新校验整树，不合法则拒绝发布。
            参数：ontologySpaceId 为对象空间 id（行为 api 仅空间内唯一，必须带空间定位）；actionApi 为行为 api。
            返回发布结果说明字符串。
            """)
    public String publishAction(
            @ToolParam(description = "对象空间 id") Integer ontologySpaceId,
            @ToolParam(description = "行为 api（空间内唯一）") String actionApi) {
        if (ontologySpaceId == null || StringUtils.isBlank(actionApi)) {
            throw new IllegalArgumentException("ontologySpaceId 与 actionApi 均不能为空");
        }
        ontologyActionService.publishAction(ontologySpaceId, actionApi);
        return "行为发布成功：actionApi=" + actionApi;
    }

    @Tool(description = """
            撤回发布/下线行为（对应 PUT /action/unpublish）。写操作：把行为状态从已发布(1)退回草稿(2)，智能体和调度不再可用。
            当用户要下线某个行为或行为树时使用。
            注意：被行为树引用的行为不能撤回，需先处理引用它的树；行为树下线同样走这里，会同时失效树的编译缓存。
            参数：ontologySpaceId 为对象空间 id；actionApi 为行为 api。
            返回撤回结果说明字符串。
            """)
    public String unpublishAction(
            @ToolParam(description = "对象空间 id") Integer ontologySpaceId,
            @ToolParam(description = "行为 api（空间内唯一）") String actionApi) {
        if (ontologySpaceId == null || StringUtils.isBlank(actionApi)) {
            throw new IllegalArgumentException("ontologySpaceId 与 actionApi 均不能为空");
        }
        ontologyActionService.unpublishAction(ontologySpaceId, actionApi);
        return "行为已撤回发布（退回草稿）：actionApi=" + actionApi;
    }

    @Tool(description = """
            按 actionApi 删除行为（对应 DELETE /action）。写操作：真实删除行为定义，不可恢复，执行前应与用户确认。
            当用户明确要求删除某个行为或行为树时使用。
            注意：已被行为调度绑定的行为不可直接删除，需先删除/暂停相关调度；被行为树引用的行为也会被拒绝删除。
            参数：ontologySpaceId 为对象空间 id（行为 api 仅空间内唯一）；actionApi 为要删除的行为 api。
            返回删除结果说明字符串。
            """)
    public String deleteActionByApi(
            @ToolParam(description = "对象空间 id") Integer ontologySpaceId,
            @ToolParam(description = "要删除的行为 api（空间内唯一）") String actionApi) {
        if (ontologySpaceId == null || StringUtils.isBlank(actionApi)) {
            throw new IllegalArgumentException("ontologySpaceId 与 actionApi 均不能为空");
        }
        ontologyActionService.deleteActionByApi(ontologySpaceId, actionApi);
        return "行为删除成功：actionApi=" + actionApi;
    }

    @Tool(description = """
            多条件分页查询行为/行为树列表（对应 GET /action/list）。只读操作。
            当用户要按空间、对象、分类、关键词、状态、类别筛选浏览行为时使用；已知空间名+行为名精确查找可用 queryActions。
            所有筛选参数均可选，不传即不过滤：
            - ontologySpaceId：对象空间 id；ontologyId：对象唯一标识（行为的操作范围包含该对象）。
            - categoryId：行为分类 id；keyword：关键词，模糊匹配行为名称、算子 api 或描述。
            - status：行为状态，2 草稿 / 1 已发布，不传查全部。
            - actionKind：单个类别，可选 BASE 基础行为 / APPLICATION 应用行为 / TREE 行为树。
            - actionKinds：类别多选，逗号分隔（如 "BASE,APPLICATION"）；与 actionKind 同时传时合并去重。
            - pageNum 默认 1，pageSize 默认 10。
            返回 MyBatis-Plus Page 分页对象：records 为行为列表（含 actionApi、displayName、description、functionApi、
            ontologyUniqIdentifier、状态、类别等），total 总条数、current 当前页、size 页大小。
            无匹配时 records 为空数组、total=0。结果按创建时间倒序（最新在前）。
            """)
    public Page<OntologyActionInfoVO> pageGetActionByOntologyId(
            @ToolParam(description = "对象空间 id，不传查全部空间", required = false) Integer ontologySpaceId,
            @ToolParam(description = "对象唯一标识，不传不过滤", required = false) String ontologyId,
            @ToolParam(description = "行为分类 id，不传不过滤", required = false) Integer categoryId,
            @ToolParam(description = "关键词：模糊匹配行为名称、算子 api 或描述", required = false) String keyword,
            @ToolParam(description = "行为状态：2 草稿，1 已发布，不传查全部", required = false) Integer status,
            @ToolParam(description = "行为类别单选：BASE/APPLICATION/TREE，不传不过滤", required = false) String actionKind,
            @ToolParam(description = "行为类别多选，逗号分隔，如 BASE,APPLICATION；与 actionKind 合并去重", required = false) String actionKinds,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        // 与 controller 口径一致：单值和多选两个入口合并去重，避免按 actionKind 传时被静默忽略
        Set<ActionKindEnum> kinds = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(actionKind)) {
            kinds.add(parseActionKind(actionKind.trim()));
        }
        if (StringUtils.isNotBlank(actionKinds)) {
            for (String k : actionKinds.split(",")) {
                if (StringUtils.isNotBlank(k)) {
                    kinds.add(parseActionKind(k.trim()));
                }
            }
        }
        return ontologyActionService.pageGetActionByOntologyId(ontologySpaceId, ontologyId, categoryId, keyword, status,
                new ArrayList<>(kinds), pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
    }

    @Tool(description = """
            按 actionApi 查询行为详情（对应 GET /action）。只读操作。
            当用户要查看某个行为/行为树的完整定义（参数映射、关系映射、树定义、基础函数配置、执行所需入参）时使用，
            也是编辑行为（updateAction）或构造执行/测试入参前的推荐取数入口。
            参数：ontologySpaceId 为对象空间 id（行为 api 仅空间内唯一）；actionApi 为行为 api。
            返回 OntologyActionDetailVO：在列表字段基础上附带 linkMapping 关系映射、mappingIns 参数映射、
            functionParams 函数参数、treeDefinition 行为树定义、basicFunction 基础函数配置、executeInputs 执行入参要求等。
            行为不存在时抛错。
            """)
    public OntologyActionDetailVO getActionByApi(
            @ToolParam(description = "对象空间 id") Integer ontologySpaceId,
            @ToolParam(description = "行为 api（空间内唯一）") String actionApi) {
        if (ontologySpaceId == null || StringUtils.isBlank(actionApi)) {
            throw new IllegalArgumentException("ontologySpaceId 与 actionApi 均不能为空");
        }
        return ontologyActionService.getActionByApi(ontologySpaceId, actionApi);
    }

    @Tool(description = """
            手动执行对象行为（对应 POST /action/execute）。写操作：按对象下全部实例真实执行并落库，一次请求记一条 MANUAL 手动执行日志。
            当用户要求对某个对象类型正式触发一次行为（如批量刷新属性、批量调函数）时使用；只想验证单条实例用 testAction。
            行为须已发布。参数 executeParamJson 为 OntologyActionExecuteParam 的 JSON 对象字符串：
            - ontologyUniqueIdentifier（必填）：对象唯一标识，按该对象下全部实例遍历执行。
            - actionApi（必填）：行为 api。
            - entityPrimaryKey：实例主键，仅基础函数行为可用——传了只查该实例，不传按对象全量查；其他类别行为不需要传。
            - shardIndex / shardTotal：分片序号（从 0 开始）与分片总数，可选，用于横向拆分批量执行。
            示例：{"ontologyUniqueIdentifier":"abcdef","actionApi":"syncSpeed"}
            返回本次执行/落库动作的中文说明字符串。执行记录可用 getSchedulingResult（type=MANUAL，id 传行为 id）查询。
            """)
    public String executeAction(
            @ToolParam(description = "OntologyActionExecuteParam 的 JSON 对象字符串，字段见工具描述") String executeParamJson) {
        OntologyActionExecuteParam param = parseParamJson(executeParamJson, OntologyActionExecuteParam.class, "executeParamJson");
        return ontologyActionService.executeAction(param);
    }

    @Tool(description = """
            行为测试（对应 POST /action/test）。注意：这是真实执行并落库的测试，不是干跑！一次请求记一条 MANUAL 手动执行日志。
            当用户要在发布前用真实实例数据验证行为定义是否可用时使用；执行前应告知用户会真实写库。
            参数 testParamJson 为 ActionTestParam 的 JSON 对象字符串：
            - ontologySpaceId（必填）：对象空间 id；actionApi（必填）：行为 api（草稿也可测试）。
            - entityPrimaryKey：行为所属对象的实例主键，只涉及一个对象时用它，不支持数组。
            - entityPrimaryKeys：对象 id -> 实例主键 的映射，映射涉及的对象都要提供；行为绑关系时用保留键 from/to 传关系两端实例。
              值写成数组即批量测试：只选实例时各轴做笛卡尔积，选了非 main 分组的行时改为按下标配对成组，最多 50 组，逐组执行各自落库。
            - inputParams：入参名 -> 数据位置数组（每个元素 {"entityPrimaryKey":..,"dataPrimaryKeys":{分组:行主键}}），
              按算子入参单独指定读哪个实例/哪一行，多元素即批量候选值；命中时优先于 entityPrimaryKeys。
            - outputParams：出参 key -> 写回位置 {"entityPrimaryKey":..,"dataPrimaryKeys":{..}}，
              key 优先级：属性唯一标识 > 出参字段名 > 顶层算子出参名；不支持数组。
            - dataPrimaryKey：分组表行主键，属性值-修改/删除必填；linkEntityPrimaryKey：关系对端实体主键（兼容写法，建议用 from/to）。
            - params：来源为外部输入的参数取值（参数名 -> 值），固定值不传。
            示例：{"ontologySpaceId":156,"actionApi":"syncSpeed","entityPrimaryKey":1,"params":{"factor":2}}
            返回 ActionTestResultVO：actionApi、message 落库动作说明、result 算子返回结果（绑算子时）、
            cases 批量测试时逐组的执行结果与失败原因。
            """)
    public ActionTestResultVO testAction(
            @ToolParam(description = "ActionTestParam 的 JSON 对象字符串，字段见工具描述") String testParamJson) {
        ActionTestParam param = parseParamJson(testParamJson, ActionTestParam.class, "testParamJson");
        return ontologyActionService.testAction(param);
    }

    @Tool(description = """
            执行行为树，单轮单实例（对应 POST /tree/execute）。写操作：节点跑通即真实落库，一次请求记一条 MANUAL 手动执行日志。
            当用户要对一条实例数据正式跑一遍已发布的行为树编排时使用；草稿树试跑用 testTree，按对象全量实例跑用 executeTreeBatch。
            单轮求值不产生 RUNNING 状态；循环、冷却、限时、并行都在单次请求内生效。
            参数 treeExecuteParamJson 为 TreeExecuteParam 的 JSON 对象字符串：
            - ontologySpaceId（必填）：树所属空间 id；treeApi（必填）：行为树 api。
            - entityPrimaryKey：全树共用的实例主键，只有节点确实需要实例时才传（属性值/实例关系行为、入参取属性、出参写属性）。
            - entityPrimaryKeys：对象标识 -> 实例主键，树里多个对象类型各要实例时用，同一对象优先于 entityPrimaryKey。
            - dataPrimaryKey：分组表行主键（属性值-修改/删除定位行），全树共用。
            - inputDataPrimaryKeys / outputDataPrimaryKeys：对象 id -> {存储分组 -> 行主键}，入参按行读 / 出参按行写回；出参留空时新增一行。
            - inputParams / outputParams：按算子入参/出参绑定指定数据位置，写法与 testAction 相同，一次执行每个入参只用一个位置。
            - linkEntityPrimaryKey：实例关系行为的对端实体主键，全树共用。
            - inputs：黑板键取值（键名 -> 值），只允许覆盖来源是外部输入的黑板键；固定值/对象属性来源不可覆盖。
            - nodeExecutes：节点标识 -> 节点执行数据（{"entityPrimaryKey":..,"params":{..}}），
              两个节点绑同一行为各要不同实例/参数时用；params 为该节点行为的外部入参，缺失会在执行前报错；
              节点上已配取值来源的入参不允许在这里再传同名值。
            示例：{"ontologySpaceId":156,"treeApi":"tree.satellite.watch","entityPrimaryKey":1,"inputs":{"threshold":10}}
            返回 TreeExecuteResultVO：treeApi、state 整树状态（SUCCESS/FAILURE）、blackboard 结束时黑板快照、nodes 逐节点结果。
            """)
    public TreeExecuteResultVO executeTree(
            @ToolParam(description = "TreeExecuteParam 的 JSON 对象字符串，字段见工具描述") String treeExecuteParamJson) {
        TreeExecuteParam param = parseParamJson(treeExecuteParamJson, TreeExecuteParam.class, "treeExecuteParamJson");
        return ontologyActionService.executeTree(param);
    }

    @Tool(description = """
            行为树测试（对应 POST /tree/test）。注意：与正式执行一样真实落库，不是干跑！草稿树也可以试跑。
            当用户要在发布前验证行为树编排是否可用时使用；执行前应告知用户会真实写库。
            参数 treeExecuteParamJson 为 TreeExecuteParam 的 JSON 对象字符串，字段结构与 executeTree 完全一致
            （ontologySpaceId、treeApi 必填；entityPrimaryKey(s)、dataPrimaryKey、input/outputDataPrimaryKeys、
            input/outputParams、linkEntityPrimaryKey、inputs、nodeExecutes 按需传，详见 executeTree 描述）。
            返回 TreeExecuteResultVO：treeApi、state 整树状态（SUCCESS/FAILURE）、blackboard 结束时黑板快照、nodes 逐节点结果。
            """)
    public TreeExecuteResultVO testTree(
            @ToolParam(description = "TreeExecuteParam 的 JSON 对象字符串，字段见工具描述") String treeExecuteParamJson) {
        TreeExecuteParam param = parseParamJson(treeExecuteParamJson, TreeExecuteParam.class, "treeExecuteParamJson");
        return ontologyActionService.testTree(param);
    }

    @Tool(description = """
            批量执行行为树（对应 POST /tree/executeBatch）。写操作：按对象类型遍历全部实例，每条实例独立跑一遍整棵树并真实落库。
            当用户要求对某个对象类型的所有实例批量执行行为树时使用；单条实例用 executeTree。
            单条实例失败不中断整批；明细默认只返回失败实例。
            参数 treeBatchExecuteParamJson 为 TreeBatchExecuteParam 的 JSON 对象字符串：
            - ontologySpaceId（必填）：树所属空间 id；treeApi（必填）：行为树 api；
              ontologyUniqueIdentifier（必填）：按哪个对象类型遍历实例。
            - shardIndex / shardTotal：分片序号（从 0 开始）与总数，可选，不传不分片。
            - inputs：黑板键取值（键名 -> 值），所有实例共用，只允许覆盖外部输入来源的键；对象属性来源按每条实例当前值刷新。
            - nodeExecutes：节点标识 -> {"params":{..}}，所有实例共用的节点外部入参；
              批量执行实例由遍历给出，这里只能传 params，定位实例的字段一律拒绝。
            - dataPrimaryKey：分组表行主键，所有实例共用；存在绑定非主属性分组黑板键时必传，否则执行前报错。
            - linkEntityPrimaryKey：实例关系行为的对端实体主键，所有实例共用。
            - includeSuccess：是否返回成功实例明细，默认 false 只返回失败明细。
            示例：{"ontologySpaceId":156,"treeApi":"tree.satellite.watch","ontologyUniqueIdentifier":"satellite"}
            返回 TreeBatchExecuteResultVO：treeApi、total 本次执行实例数、successCount/failCount 成功失败计数、
            results 实例明细（默认仅失败实例，含失败原因）。
            """)
    public TreeBatchExecuteResultVO executeTreeBatch(
            @ToolParam(description = "TreeBatchExecuteParam 的 JSON 对象字符串，字段见工具描述") String treeBatchExecuteParamJson) {
        TreeBatchExecuteParam param = parseParamJson(treeBatchExecuteParamJson, TreeBatchExecuteParam.class, "treeBatchExecuteParamJson");
        return ontologyActionService.executeTreeBatch(param);
    }

    @Tool(description = """
            查询行为树编辑器目录（对应 GET /tree/node-defs）。只读操作。
            当用户要创建/编辑行为树，需要了解可用节点类型和可绑定的行为时使用。
            参数 ontologySpaceId 为对象空间 id，可绑定行为只取该空间内已发布的行为。
            返回 TreeDefsVO：nodes 为节点类型清单（控制节点与行为节点的定义），
            availableActions 为本空间可作为行为节点绑定的已发布行为列表（含 api、名称、入参要求）。
            空间内没有已发布行为时 availableActions 为空数组。
            """)
    public TreeDefsVO getTreeDefs(
            @ToolParam(description = "对象空间 id") Integer ontologySpaceId) {
        if (ontologySpaceId == null) {
            throw new IllegalArgumentException("ontologySpaceId 不能为空");
        }
        return ontologyActionService.getTreeDefs(ontologySpaceId);
    }

    /**
     * MCP 无参数校验切面，复杂入参统一走 JSON 字符串 + Jackson 反序列化，空白与格式错误在这里防护。
     */
    private <T> T parseParamJson(String json, Class<T> clazz, String paramName) {
        if (StringUtils.isBlank(json)) {
            throw new IllegalArgumentException(paramName + " 不能为空，应为 JSON 对象字符串");
        }
        try {
            return jsonMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(paramName + " 解析失败，应为合法的 JSON 对象字符串：" + e.getMessage());
        }
    }

    private ActionKindEnum parseActionKind(String value) {
        try {
            return ActionKindEnum.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("行为类别不合法：" + value + "，可选值 BASE/APPLICATION/TREE");
        }
    }
}
