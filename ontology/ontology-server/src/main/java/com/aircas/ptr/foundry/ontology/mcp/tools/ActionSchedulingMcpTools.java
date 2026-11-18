package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.ActionSchedulingDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.ActionSchedulingInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.SchedulingResultVO;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 行为调度 MCP 工具。
 *
 * <p>覆盖 {@code OntologyActionController} 的 /action/scheduling 系列端点：
 * 创建/编辑/列表/详情/删除/启动/暂停/执行记录。调度类型 TASK 走 xxl-job cron 周期触发，
 * RULE 走 CDC 增量 + 启用时存量评估；MANUAL 仅用于查询手动执行记录。
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class ActionSchedulingMcpTools implements McpTools {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final OntologyActionService ontologyActionService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            创建行为调度（对应 POST /action/scheduling）。写操作：真实落库，创建后为 STOP 暂停状态，需再调用 startScheduling 启动才会触发执行。
            当用户要为某个已发布行为配置自动触发策略时使用：TASK 定时周期触发（走 xxl-job cron），RULE 条件规则触发（走 CDC 增量评估，启用时还会对绑定实例评估一遍存量数据）。
            限制：调度只能绑定本空间内、且绑定了算子（functionApi 非空）的行为；属性值/实例关系等不绑算子的行为不支持调度。同一行为允许配置多个调度策略。
            参数 schedulingJson 为 ActionSchedulingCreateParam 的 JSON 对象字符串，主要字段：
            - spaceId（必填）：行为所属空间 id；actionApi（必填）：行为 api（空间内唯一）；name（必填）：调度名称；description：调度描述。
            - type（必填）：调度类型，TASK 定时 / RULE 规则（不支持 MANUAL，手动执行走 executeAction/testAction）。
            - ontologyIdentifier：行为挂载的对象唯一标识；应用行为可不传（从行为定义推导），多对象行为可不传。
            - type=TASK 时填 task 对象：taskCronExpression（必填）Quartz cron 表达式，如 "30 * * 1/1 * ? *"；
              periodType 可选 DAILY 每天 / WEEKLY 每周 / MONTHLY 每月 / CRON；
              entityJson 可选，实体执行参数 JSON 字符串（与行为测试 ActionTestParam 同形式），不传则按对象下全部实例遍历执行。
            - type=RULE 时填 rule 对象：judgeRules（必填）规则数组，每条规则独立求值、谁满足跑谁，元素结构：
              {"condition":false,"judgeParams":[...],"actionParam":{...}}；
              condition=true 表示对组合条件整体取反（else 语义，条件不成立才执行）；
              judgeParams 为条件项数组：leftOperate/rightOperate 操作数 + condition 比较符 + 括号与连接符。
              操作数 {"type":"dynamic","ontologyId":"objA","entityId":"3","propertyId":"prop-x","propertyName":"longitude","dataPrimaryKey":"12"}
              取指定对象实例的属性当前值（dynamic 时 ontologyId、entityId 必填，propertyId/propertyName 至少给一个，取值优先 propertyId；
              dataPrimaryKey 可选，属性在扩展存储分组时指定取哪一行，不传按主键倒序取最新行，主表属性忽略）；
              操作数 {"type":"fixed","fixValue":"100"} 用固定值。
              比较符 condition：数字 LT/LE/EQ/NE/GT/GE，字符串 CONTAINS/NOT_CONTAINS/EQ/NE（大小比较仅限数字，不支持 CH）。
              isLeft/isRight 为本条件前左括号/后右括号的个数（Integer，支持嵌套如 (( 传 2，兼容旧布尔 true=1/false=0）；
              linkFlag 为与下一条的连接符 AND/OR（AND 优先于 OR，括号可改变优先级），最后一条不传。
              actionParam 为命中后执行的行为参数：{"action_api":"actionA","ontology_identifier":"objA","entity_identifiler":"3","entity_json":"{...}"}
              （也接受 camelCase 别名 actionApi/ontologyIdentifier/entityIdentifier/entityJson；entity_json 为完整执行参数 JSON 字符串，
              与周期调度 entityJson 同形式、支持数组批量，不传时按行为定义构造最小执行参数）。
              rule.flowDocument 可选：前端规则画布文档的 JSON 字符串（前端 JSON.stringify 产物），后端只直存直返用于回显画布，不参与求值。
            示例（RULE）：{"spaceId":156,"ontologyIdentifier":"abcdef","actionApi":"shipLocation","name":"示例调度","description":"按规则触发","type":"RULE","rule":{"judgeRules":[{"condition":false,"judgeParams":[{"leftOperate":{"type":"dynamic","ontologyId":"abcdef","entityId":"3","propertyName":"longitude"},"rightOperate":{"type":"fixed","fixValue":"20.0"},"condition":"GT"}],"actionParam":{"action_api":"shipLocation"}}]}}
            示例（TASK）：{"spaceId":156,"ontologyIdentifier":"abcdef","actionApi":"shipLocation","name":"示例周期调度","description":"按 cron 周期触发","type":"TASK","task":{"taskCronExpression":"30 * * 1/1 * ? *"}}
            返回新建调度的 id（Long），后续启动/编辑/删除/查执行记录都用它。行为不存在、不绑算子、规则不合法或 xxl-job 创建失败时抛错。
            """)
    public Long createScheduling(
            @ToolParam(description = "ActionSchedulingCreateParam 的 JSON 对象字符串，字段见工具描述") String schedulingJson) {
        ActionSchedulingCreateParam param = parseParamJson(schedulingJson, ActionSchedulingCreateParam.class, "schedulingJson");
        checkSchedulingBaseFields(param.getActionApi(), param.getName(), param.getType());
        if (param.getSpaceId() == null) {
            throw new IllegalArgumentException("spaceId 不能为空，应为行为所属空间 id（调度只能绑定本空间的行为）");
        }
        checkTypePayload(param.getType(), param.getTask() == null ? null : param.getTask().getTaskCronExpression(),
                param.getTask() != null, param.getRule() != null);
        return ontologyActionService.createScheduling(param);
    }

    @Tool(description = """
            编辑行为调度（对应 PUT /action/scheduling）。写操作：整体覆盖调度定义并同步更新 xxl-job 任务（TASK 时）。
            当用户要修改调度的名称、cron 表达式、规则条件或执行参数时使用。
            注意：处于 START 启动状态的调度不可编辑，需先调用 stopScheduling 暂停；只能改绑本空间的行为。
            参数 schedulingJson 为 ActionSchedulingUpdateParam 的 JSON 对象字符串，字段结构与 createScheduling 完全一致
            （见 createScheduling 描述），额外多一个必填字段 id：要编辑的调度 id；spaceId 编辑时可不传（空间归属按调度记录原空间校验）。
            编辑是整体覆盖：task/rule 按 type 二选一重新提交完整内容，未传的可选字段（如 entityJson、flowDocument）会被清空，
            建议先用 getSchedulingDetailById 取详情后修改再提交。
            示例（TASK）：{"id":123,"spaceId":156,"ontologyIdentifier":"abcdef","actionApi":"shipLocation","name":"示例周期调度","description":"按 cron 周期触发","type":"TASK","task":{"taskCronExpression":"30 * * 1/1 * ? *"}}
            返回更新结果说明字符串。调度不存在、已启动或规则不合法时抛错。
            """)
    public String updateScheduling(
            @ToolParam(description = "ActionSchedulingUpdateParam 的 JSON 对象字符串，字段见工具描述（比创建多必填 id）") String schedulingJson) {
        ActionSchedulingUpdateParam param = parseParamJson(schedulingJson, ActionSchedulingUpdateParam.class, "schedulingJson");
        if (param.getId() == null) {
            throw new IllegalArgumentException("id 不能为空，应为要编辑的调度 id");
        }
        checkSchedulingBaseFields(param.getActionApi(), param.getName(), param.getType());
        checkTypePayload(param.getType(), param.getTask() == null ? null : param.getTask().getTaskCronExpression(),
                param.getTask() != null, param.getRule() != null);
        ontologyActionService.updateScheduling(param);
        return "行为调度更新成功：id=" + param.getId() + "，name=" + param.getName();
    }

    @Tool(description = """
            分页查询行为调度列表（对应 GET /action/scheduling/list）。只读操作。
            当用户要浏览/查找已配置的调度（拿调度 id 用于启动、暂停、编辑、删除、查执行记录）时使用。
            所有筛选参数均可选，不传即不过滤：
            - name：调度名称，模糊匹配；type：TASK 定时 / RULE 规则，不传查全部；
            - ontologySpaceId：空间 id，按调度绑定行为所属空间过滤；
            - pageNum 默认 1，pageSize 默认 10。
            返回 MyBatis-Plus Page 分页对象：records 为调度列表（id、schedulingName、description、type、
            status 状态 START/STOP、periodType 周期类型、actionApi、actionName、ontologyIdentifier、ontologyName、
            createTime/updateTime），total 总条数、current 当前页、size 页大小。无匹配时 records 为空数组、total=0。
            """)
    public Page<ActionSchedulingInfoVO> listScheduling(
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize,
            @ToolParam(description = "调度名称，模糊匹配，不传不过滤", required = false) String name,
            @ToolParam(description = "调度类型：TASK 定时 / RULE 规则，不传查全部", required = false) String type,
            @ToolParam(description = "空间 id，按行为所属空间过滤，不传查全部", required = false) Integer ontologySpaceId) {
        ActionSchedulingTypeEnum typeEnum = StringUtils.isBlank(type) ? null : parseSchedulingType(type);
        return ontologyActionService.listScheduling(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize,
                name, typeEnum, ontologySpaceId);
    }

    @Tool(description = """
            查询行为调度详情（对应 GET /action/scheduling/detail）。只读操作。
            当用户要查看某个调度的完整配置（cron/周期/执行参数，或规则条件与画布文档）时使用，也是编辑调度（updateScheduling）前的推荐取数入口。
            参数：id 为调度 id；type 为调度类型，只支持 TASK 定时 / RULE 规则（MANUAL 不是调度，无详情）。
            返回 ActionSchedulingDetailVO：在列表字段（id、schedulingName、description、type、status START/STOP、
            actionApi、actionName、ontologyIdentifier、ontologyName、periodType、createTime/updateTime）基础上，
            type=TASK 时附带 taskVO（cron 表达式、周期类型、entityJson 执行参数）；
            type=RULE 时附带 ruleVO（judgeRules 保存的组合条件+行为参数，以及 flowDocument 画布文档 JSON 字符串，原样返回用于回显）。
            调度不存在或其绑定的行为已被删除时抛错。
            """)
    public ActionSchedulingDetailVO getSchedulingDetailById(
            @ToolParam(description = "调度 id") Long id,
            @ToolParam(description = "调度类型：TASK 定时 / RULE 规则") String type) {
        if (id == null || StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("id 与 type 均不能为空，type 可选 TASK/RULE");
        }
        return ontologyActionService.getSchedulingDetailById(id, parseSchedulingType(type));
    }

    @Tool(description = """
            删除行为调度（对应 DELETE /action/scheduling/{type}/{id}）。写操作：真实删除调度配置，不可恢复，执行前应与用户确认。
            当用户明确要求移除某个调度策略时使用。TASK 会同时删除 xxl-job 上的定时任务，RULE 删除后 CDC 不再触发该规则。
            注意：删除的是调度策略本身，不影响行为定义；历史执行记录不随删除清理。只想暂时停跑请用 stopScheduling。
            参数：type 为调度类型，只支持 TASK 定时 / RULE 规则；id 为要删除的调度 id。
            返回删除结果说明字符串。调度不存在或 xxl-job 删除失败时抛错。
            """)
    public String deleteScheduling(
            @ToolParam(description = "调度类型：TASK 定时 / RULE 规则") String type,
            @ToolParam(description = "要删除的调度 id") Long id) {
        if (id == null || StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("type 与 id 均不能为空，type 可选 TASK/RULE");
        }
        ActionSchedulingTypeEnum typeEnum = parseSchedulingType(type);
        ontologyActionService.removeScheduling(typeEnum, id);
        return "行为调度删除成功：type=" + typeEnum + "，id=" + id;
    }

    @Tool(description = """
            分页查询行为执行记录（对应 GET /action/scheduling/result）。只读操作，默认返回 10 条最新结果。
            当用户要查看调度触发历史、手动执行历史或排查某次执行失败原因时使用。
            参数：
            - id（必填）：type=TASK/RULE 时传调度 id；type=MANUAL 时传行为 id（手动执行日志按行为 id 记录，不是调度 id）。
            - type（必填）：TASK 定时 / RULE 规则 / MANUAL 手动执行（executeAction、testAction、行为树执行/测试产生的记录查 MANUAL）。
            - taskStatus：执行状态过滤，可选 PENDING 执行中 / FAILED 失败 / COMPLETED 完成，不传查全部。
            - beginTime / endTime：触发时间起止，格式 yyyy-MM-dd HH:mm:ss，可选。
            - pageNum 默认 1，pageSize 默认 10。
            返回 MyBatis-Plus Page 分页对象：records 为执行记录列表（id 日志 id、msg 日志消息、executeResult 执行结果、
            requestParam 请求参数、triggerTime 触发时间、executeStartTime 开始执行时间、completeTime 完成时间、
            taskStatus 执行状态、executorIp 执行机 IP、executorHost 执行机主机名），total 总条数、current 当前页、size 页大小。
            """)
    public Page<SchedulingResultVO> getSchedulingResult(
            @ToolParam(description = "TASK/RULE 传调度 id，MANUAL 传行为 id") Long id,
            @ToolParam(description = "记录类型：TASK 定时 / RULE 规则 / MANUAL 手动执行") String type,
            @ToolParam(description = "执行状态过滤：PENDING/FAILED/COMPLETED，不传查全部", required = false) String taskStatus,
            @ToolParam(description = "触发时间起，格式 yyyy-MM-dd HH:mm:ss，可选", required = false) String beginTime,
            @ToolParam(description = "触发时间止，格式 yyyy-MM-dd HH:mm:ss，可选", required = false) String endTime,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        if (id == null || StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("id 与 type 均不能为空；TASK/RULE 传调度 id，MANUAL 传行为 id");
        }
        TaskStatusEnum statusEnum = null;
        if (StringUtils.isNotBlank(taskStatus)) {
            try {
                statusEnum = TaskStatusEnum.valueOf(taskStatus.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("执行状态不合法：" + taskStatus + "，可选值 PENDING/FAILED/COMPLETED");
            }
        }
        return ontologyActionService.getSchedulingResult(id, parseSchedulingType(type), statusEnum,
                parseDate(beginTime, "beginTime"), parseDate(endTime, "endTime"),
                pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
    }

    @Tool(description = """
            启动行为调度（对应 POST /action/scheduling/start/{type}/{id}）。写操作：把调度状态置为 START 并开始自动触发。
            当用户要求启用/上线某个调度时使用。新建的调度默认 STOP，必须启动后才会执行。
            TASK：同步启动 xxl-job 定时任务，之后按 cron 周期真实执行行为并落库；
            RULE：置为 START 后立即异步对条件绑定实例评估一遍存量数据（满足条件就执行，失败只记日志不阻塞启动），之后由 CDC 在条件引用的实例变化时定向评估。
            注意：启动后行为会按策略自动真实执行写库，启动前应与用户确认；执行历史用 getSchedulingResult 查询。
            参数：type 为调度类型，只支持 TASK 定时 / RULE 规则；id 为调度 id。
            返回启动结果说明字符串。调度不存在或 xxl-job 启动失败时抛错。
            """)
    public String startScheduling(
            @ToolParam(description = "调度类型：TASK 定时 / RULE 规则") String type,
            @ToolParam(description = "要启动的调度 id") Long id) {
        if (id == null || StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("type 与 id 均不能为空，type 可选 TASK/RULE");
        }
        ActionSchedulingTypeEnum typeEnum = parseSchedulingType(type);
        ontologyActionService.startScheduling(typeEnum, id);
        return "行为调度已启动：type=" + typeEnum + "，id=" + id
                + (typeEnum == ActionSchedulingTypeEnum.RULE ? "（已触发存量数据异步评估，之后按 CDC 增量触发）" : "（xxl-job 按 cron 周期触发）");
    }

    @Tool(description = """
            暂停行为调度（对应 POST /action/scheduling/stop/{type}/{id}）。写操作：把调度状态置为 STOP，停止自动触发。
            当用户要求暂停/停用某个调度，或编辑/删除行为前需要先解除调度绑定时使用。
            TASK：同步暂停 xxl-job 定时任务，不再按 cron 触发；RULE：置为 STOP 后 CDC 变化不再触发该规则。
            注意：暂停只影响后续触发，不删除调度配置，也不会回滚已执行落库的数据；可随时用 startScheduling 恢复。
            编辑调度（updateScheduling）以及编辑/删除被调度绑定的行为，都要求先暂停调度。
            参数：type 为调度类型，只支持 TASK 定时 / RULE 规则；id 为调度 id。
            返回暂停结果说明字符串。调度不存在或 xxl-job 暂停失败时抛错。
            """)
    public String stopScheduling(
            @ToolParam(description = "调度类型：TASK 定时 / RULE 规则") String type,
            @ToolParam(description = "要暂停的调度 id") Long id) {
        if (id == null || StringUtils.isBlank(type)) {
            throw new IllegalArgumentException("type 与 id 均不能为空，type 可选 TASK/RULE");
        }
        ActionSchedulingTypeEnum typeEnum = parseSchedulingType(type);
        ontologyActionService.stopScheduling(typeEnum, id);
        return "行为调度已暂停：type=" + typeEnum + "，id=" + id;
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

    /** 补齐 @Valid 的顶层必填校验（actionApi/name/type）；spaceId 仅创建时服务端要求，编辑按调度记录原空间校验 */
    private void checkSchedulingBaseFields(String actionApi, String name, ActionSchedulingTypeEnum type) {
        if (StringUtils.isBlank(actionApi)) {
            throw new IllegalArgumentException("actionApi 不能为空，应为要调度的行为 api");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("name 不能为空，应为调度名称");
        }
        if (type == null) {
            throw new IllegalArgumentException("type 不能为空，可选值 TASK 定时 / RULE 规则");
        }
    }

    /** TASK/RULE 载荷二选一：TASK 缺 task/cron 服务端会空指针，这里提前拦截；RULE 的 judgeRules 校验由服务端完成 */
    private void checkTypePayload(ActionSchedulingTypeEnum type, String cron, boolean hasTask, boolean hasRule) {
        if (type == ActionSchedulingTypeEnum.TASK) {
            if (!hasTask) {
                throw new IllegalArgumentException("type=TASK 时 task 对象不能为空，应含 taskCronExpression");
            }
            if (StringUtils.isBlank(cron)) {
                throw new IllegalArgumentException("type=TASK 时 task.taskCronExpression 不能为空，应为 Quartz cron 表达式");
            }
        } else if (type == ActionSchedulingTypeEnum.RULE && !hasRule) {
            throw new IllegalArgumentException("type=RULE 时 rule 对象不能为空，应含 judgeRules 规则数组");
        }
    }

    private ActionSchedulingTypeEnum parseSchedulingType(String value) {
        try {
            return ActionSchedulingTypeEnum.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("调度类型不合法：" + value + "，可选值 TASK/RULE（查手动执行记录时还可传 MANUAL）");
        }
    }

    private Date parseDate(String value, String paramName) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
        format.setLenient(false);
        try {
            return format.parse(value.trim());
        } catch (ParseException e) {
            throw new IllegalArgumentException(paramName + " 格式不合法：" + value + "，应为 " + DATE_PATTERN);
        }
    }
}