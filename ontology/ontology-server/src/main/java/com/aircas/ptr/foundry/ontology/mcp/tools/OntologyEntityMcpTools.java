package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.ActionInstanceTargetParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityGenerateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityIdsQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityInstanceDataQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityLinkBetweenParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyGenericQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyInsertParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyRowPageQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyRowQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyValueParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityRelationUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.FilterGroupParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySelectPropertyParam;
import com.aircas.ptr.foundry.ontology.model.param.OrderByParam;
import com.aircas.ptr.foundry.ontology.model.vo.EntityIdsQueryVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityLinkPropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityLinksVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyGenericQueryVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyRowDetailVO;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 对象实体 MCP 工具。
 *
 * <p>覆盖 {@code OntologyEntityController}（/entity）的全部 REST 端点：实体分页/搜索/详情/行详情/行分页查询、
 * 单实体与多实体关系查询、两实体间关系查询、行为执行、实体批量创建、属性插入/更新/删除、
 * 图节点创建/补全/删除、实体关系更新、实体初始化生成、属性通用查询与实例数据属性查询。
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 *
 * <p>工具直接调用 {@link EntityService}，不经过控制器的 {@code @Valid} Bean 校验切面，
 * 因此控制器依赖的必填/范围校验在此手动补齐（抛出带中文消息的 {@link IllegalArgumentException}）。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyEntityMcpTools implements McpTools {

    private final EntityService entityService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            根据对象唯一标识分页查询该对象下的实体列表（只返回实体主属性信息）。
            当用户需要查看某个对象（本体）包含哪些实例数据、按页浏览实体时使用。
            不做可见性过滤，返回该对象下的全部实体。无数据时返回空分页。
            """)
    public Page<EntityInfoVO> listEntities(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "页码，从 1 开始；不传默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页数量；不传默认 10", required = false) Integer pageSize) {
        if (StringUtils.isEmpty(ontologyUniqueIdentifier)) {
            return new Page<>();
        }
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return entityService.getEntities(ontologyUniqueIdentifier, "", "", currentPage, size, false);
    }

    @Tool(description = """
            根据对象唯一标识 + 实体主键值，按行返回该实体的全部属性。
            当用户需要查看单个实体的完整属性明细（每类动态属性默认展示前 100 条）时使用。
            sort 控制排序方向：ASC 升序 / DESC 降序，不传默认 DESC。
            """)
    public EntityPropertyRowDetailVO getEntityPropertyRowDetail(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "实体主键值（entityPrimaryKey）；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey,
            @ToolParam(description = "排序方向，可选值：ASC（升序）、DESC（降序）；不传默认 DESC", required = false) String sort) {
        if (StringUtils.isEmpty(ontologyUniqueIdentifier) || StringUtils.isEmpty(entityPrimaryKey)) {
            return null;
        }
        QuerySortEnum sortEnum;
        try {
            sortEnum = StringUtils.isEmpty(sort) ? QuerySortEnum.DESC : QuerySortEnum.valueOf(sort.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            sortEnum = QuerySortEnum.DESC;
        }
        EntityPropertyRowQueryParam param = EntityPropertyRowQueryParam.builder()
                .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                .entityPrimaryKey(coercePrimaryKey(entityPrimaryKey))
                .sort(sortEnum)
                .build();
        return entityService.getEntityPropertyRowDetail(param);
    }

    @Tool(description = """
            根据多个对象下的多个实体主键批量查询实体主属性信息（对应 REST 端点 POST /entity/query）。
            当已知若干对象及其实例主键、需要一次性取回这些实例的主属性（主表字段）时使用，可跨对象混合查询。
            queriesJson 为 JSON 数组字符串，每个元素形如 {"ontologyUniqueIdentifier":"对象唯一标识","entityPrimaryKeys":[1,2,3]}：
            - ontologyUniqueIdentifier：必填，对象唯一标识；
            - entityPrimaryKeys：实体主键值数组，数字主键写数字（如 1），文本主键写字符串（如 "abc"）；不传或空数组时返回该对象下全部实体。
            返回 EntityIdsQueryVO 列表：ontologyUniqueIdentifier（对象唯一标识）、entityList（EntityInfoVO 列表，
            含 primaryKey、displayName、properties 主属性列表、ontologyName）。无匹配时对应对象的 entityList 为空。
            """)
    public List<EntityIdsQueryVO> getEntitiesByIds(
            @ToolParam(description = "查询条件 JSON 数组字符串，元素形如 {\"ontologyUniqueIdentifier\":\"obj_a\",\"entityPrimaryKeys\":[1,2]}") String queriesJson) {
        requireText(queriesJson, "queriesJson");
        List<EntityIdsQueryParam> params = parseJson(queriesJson,
                new TypeReference<List<EntityIdsQueryParam>>() {
                }, "queriesJson");
        if (CollectionUtils.isEmpty(params)) {
            throw new IllegalArgumentException("queriesJson 不能为空数组");
        }
        for (EntityIdsQueryParam p : params) {
            if (p == null || StringUtils.isBlank(p.getOntologyUniqueIdentifier())) {
                throw new IllegalArgumentException("queriesJson 每个元素的 ontologyUniqueIdentifier 不能为空");
            }
        }
        return entityService.getByEntityIds(params);
    }

    @Tool(description = """
            实体搜索：按对象唯一标识 + 属性显示名称 + 属性值分页搜索实体（对应 REST 端点 POST /entity/search）。
            当用户需要按某个主属性的取值筛选实例（如按名称找某条实例）时使用。
            与 listEntities 的区别：本工具做可见性过滤（只返回可见的主属性），且支持按属性值精确匹配。
            propertyName 匹配属性的 displayName（显示名称，不是 apiName），该属性必须已关联数据源，否则报错；
            propertyValue 会按属性定义的数据类型自动转换（数字属性传数字字符串即可）。
            propertyName 与 propertyValue 都不传时等同分页浏览全部实体。
            返回 Page<EntityInfoVO>：primaryKey、displayName、properties（可见主属性列表）、ontologyName。
            """)
    public Page<EntityInfoVO> searchEntities(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "属性显示名称（displayName），须已关联数据源；不传则不按属性过滤", required = false) String propertyName,
            @ToolParam(description = "属性值（按属性类型自动转换，数字属性传数字字符串如 \"18\"）；不传则不按属性过滤", required = false) String propertyValue,
            @ToolParam(description = "页码，从 1 开始；不传默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页数量；不传默认 10", required = false) Integer pageSize) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return entityService.getEntities(ontologyUniqueIdentifier, propertyName,
                StringUtils.isEmpty(propertyValue) ? null : propertyValue, currentPage, size, true);
    }

    @Tool(description = """
            根据对象唯一标识 + 实体主键查询该实体的所有属性（按列返回，对应 REST 端点 POST /entity/detail）。
            当需要以「每个属性一列、属性值列表」的形式查看实例完整属性时使用；
            动态属性（非 main 存储分组）默认按 id 倒序展示前 10 条。需要按行组织、每组前 100 条时改用 getEntityPropertyRowDetail。
            返回 EntityPropertyDetailVO 列表：propertyUniqIdentifier、propertyApiName、propertyDisplayName、
            propertyValues（属性值列表）、primaryKey（实体主键）、categoryId、metadata。
            对象没有主键属性或主键未绑定数据源时返回空列表。
            """)
    public List<EntityPropertyDetailVO> getEntityDetail(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        return entityService.getEntityDetail(ontologyUniqueIdentifier, coercePrimaryKey(entityPrimaryKey));
    }

    @Tool(description = """
            根据实体主键和存储分组分页查询属性行（对应 REST 端点 POST /entity/row_detail/page）。
            当 getEntityPropertyRowDetail 返回的某个动态属性分组行数超过默认展示条数、需要翻页查看该分组更多行时使用。
            只查指定分组，返回该组当前页数据与总行数。
            storageGroup 与 row_detail 返回的 propertyGroups[].storageGroup 一致（主属性分组为 main）。
            sort 控制行排序方向：ASC 升序 / DESC 降序，不传默认 DESC。
            pageNum 从 1 开始（默认 1）；pageSize 默认 10，最大 500。
            返回 Page<EntityPropertyRowDetailVO.PropertyGroupData>：每行含 dataPrimaryKey（行数据主键）与
            props（propertyUniqIdentifier、propertyApiName、propertyDisplayName、propertyValue、categoryId 等）。
            """)
    public Page<EntityPropertyRowDetailVO.PropertyGroupData> getEntityPropertyRowDetailPage(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey,
            @ToolParam(description = "属性存储分组名称，与 row_detail 返回的 propertyGroups[].storageGroup 一致，如 main") String storageGroup,
            @ToolParam(description = "排序方向，可选值：ASC（升序）、DESC（降序）；不传默认 DESC", required = false) String sort,
            @ToolParam(description = "页码，从 1 开始；不传默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数；不传默认 10，最大 500", required = false) Integer pageSize) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        requireText(storageGroup, "storageGroup");
        int currentPage = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 10 : pageSize;
        if (currentPage < 1) {
            throw new IllegalArgumentException("pageNum 必须大于 0");
        }
        if (size < 1 || size > 500) {
            throw new IllegalArgumentException("pageSize 必须在 1 到 500 之间");
        }
        QuerySortEnum sortEnum;
        try {
            sortEnum = StringUtils.isEmpty(sort) ? QuerySortEnum.DESC : QuerySortEnum.valueOf(sort.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            sortEnum = QuerySortEnum.DESC;
        }
        EntityPropertyRowPageQueryParam param = EntityPropertyRowPageQueryParam.builder()
                .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                .entityPrimaryKey(coercePrimaryKey(entityPrimaryKey))
                .sort(sortEnum)
                .storageGroup(storageGroup)
                .pageNum(currentPage)
                .pageSize(size)
                .build();
        return entityService.getEntityPropertyRowDetailPage(param);
    }

    @Tool(description = """
            查询单对象单个实体的所有关联关系（对应 REST 端点 POST /entity/link）。
            当需要查看某个实例在图库中与其他实例的全部关系边（正向与反向）时使用。
            返回 EntityLinkPropertyVO 列表：linkName（关系名称）、linkType（关系类型）、ontologyFrom/ontologyTo（两端对象唯一标识）、
            entityPrimaryKeyFrom/entityPrimaryKeyTo（两端实体主键）、displayNameFrom/displayNameTo（两端实体展示名）、
            startTime/endTime（最近可见窗口）、visibilityWindows（全部可见窗口）、status（关系状态 ENABLE/DISABLE/DELETE）。
            实体无图节点或没有关系时返回空列表。
            """)
    public List<EntityLinkPropertyVO> getEntityLinksByPrimaryKey(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        return entityService.getEntityLinksByPrimaryKey(ontologyUniqueIdentifier, coercePrimaryKey(entityPrimaryKey));
    }

    @Tool(description = """
            查询多对象下多个实体的所有关联关系（对应 REST 端点 POST /entity/all_link）。
            当需要批量获取若干实例（可跨对象）各自的关系边时使用，按实体分组返回。
            queriesJson 为 JSON 数组字符串，每个元素形如 {"ontologyUniqueIdentifier":"对象唯一标识","entityPrimaryKeys":[1,2]}：
            - ontologyUniqueIdentifier：必填，对象唯一标识；
            - entityPrimaryKeys：实体主键值数组，数字主键写数字、文本主键写字符串；不传或空数组时查询该对象下全部实体的关系。
            返回 EntityLinksVO 列表：ontologyUniqueIdentifier、entityPrimaryKey（实体主键）、links（该实体的全部关系边，
            字段同 getEntityLinksByPrimaryKey）。没有关系的实体不出现在结果中。
            """)
    public List<EntityLinksVO> getAllLinksByEntityIds(
            @ToolParam(description = "查询条件 JSON 数组字符串，元素形如 {\"ontologyUniqueIdentifier\":\"obj_a\",\"entityPrimaryKeys\":[1,2]}；entityPrimaryKeys 为空时查该对象全部实体") String queriesJson) {
        requireText(queriesJson, "queriesJson");
        List<EntityIdsQueryParam> params = parseJson(queriesJson,
                new TypeReference<List<EntityIdsQueryParam>>() {
                }, "queriesJson");
        if (CollectionUtils.isEmpty(params)) {
            throw new IllegalArgumentException("queriesJson 不能为空数组");
        }
        for (EntityIdsQueryParam p : params) {
            if (p == null || StringUtils.isBlank(p.getOntologyUniqueIdentifier())) {
                throw new IllegalArgumentException("queriesJson 每个元素的 ontologyUniqueIdentifier 不能为空");
            }
        }
        return entityService.getAllLinksByEntityIds(params);
    }

    @Tool(description = """
            查询两个实体之间的关联关系，含正向与反向边（对应 REST 端点 POST /entity/link_between）。
            当需要判断/查看两个指定实例（可属于不同对象）之间是否存在关系、关系状态与可见时段时使用。
            四个参数都必填：起点对象唯一标识 + 起点实体主键、终点对象唯一标识 + 终点实体主键。
            返回 EntityLinkPropertyVO 列表（字段同 getEntityLinksByPrimaryKey）；任一实例无图节点或两者间无边时返回空列表。
            """)
    public List<EntityLinkPropertyVO> getEntityLinksBetween(
            @ToolParam(description = "起点对象唯一标识（ontologyUniqueIdentifierFrom）") String ontologyUniqueIdentifierFrom,
            @ToolParam(description = "起点实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKeyFrom,
            @ToolParam(description = "终点对象唯一标识（ontologyUniqueIdentifierTo）") String ontologyUniqueIdentifierTo,
            @ToolParam(description = "终点实体主键值；数字主键传数字字符串如 \"2\"，文本主键直接传文本") String entityPrimaryKeyTo) {
        requireText(ontologyUniqueIdentifierFrom, "ontologyUniqueIdentifierFrom");
        requireText(entityPrimaryKeyFrom, "entityPrimaryKeyFrom");
        requireText(ontologyUniqueIdentifierTo, "ontologyUniqueIdentifierTo");
        requireText(entityPrimaryKeyTo, "entityPrimaryKeyTo");
        EntityLinkBetweenParam param = EntityLinkBetweenParam.builder()
                .ontologyUniqueIdentifierFrom(ontologyUniqueIdentifierFrom)
                .entityPrimaryKeyFrom(coercePrimaryKey(entityPrimaryKeyFrom))
                .ontologyUniqueIdentifierTo(ontologyUniqueIdentifierTo)
                .entityPrimaryKeyTo(coercePrimaryKey(entityPrimaryKeyTo))
                .build();
        return entityService.getEntityLinksBetween(
                param.getOntologyUniqueIdentifierFrom(), param.getEntityPrimaryKeyFrom(),
                param.getOntologyUniqueIdentifierTo(), param.getEntityPrimaryKeyTo());
    }

    @Tool(description = """
            执行实体挂载的行为（action），对应 REST 端点 POST /entity/action/execute。
            当用户需要对某个实例触发已发布的行为（计算、属性值修改/删除、实例关系操作等）时使用。
            一次请求记一条手动（MANUAL）执行日志。行为定义可用 queryActions/getActionByApi 查询。
            参数说明：
            - ontologyUniqueIdentifier / entityPrimaryKey：必填，行为挂载的对象及目标实例主键；
            - ontologySpaceId：必填，对象所属空间 id（整数）；
            - actionApi：必填，行为的 api 名称；
            - dataPrimaryKey：可选，存储分组行的数据主键；属性值行为的修改/删除需要它定位到具体一行，main 分组可不传（等同实体主键），绑算子的行为忽略该字段；
            - entityPrimaryKeysJson：可选 JSON 对象，对象id -> 实例主键，如 {"obj_b":2}；应用行为的入参/出参可能跨对象，映射涉及的对象都要提供；单对象行为可只传 entityPrimaryKey；
            - inputParamsJson：可选 JSON 对象，入参名 -> {"entityPrimaryKey":实例主键,"dataPrimaryKeys":{"分组名":行主键}}；同一对象的两个属性入参要读不同实例时用它，命中时优先于 entityPrimaryKeysJson，只影响该入参读取；关系对端与不绑算子的行为不支持；
            - outputParamsJson：可选 JSON 对象，key -> {"entityPrimaryKey":实例主键,"dataPrimaryKeys":{"分组名":行主键}}；key 优先级：属性唯一标识 > 出参字段名 > 顶层算子出参名；同一算子出参的几个属性写到不同实例时逐条点名，只影响该行写回；不绑算子的行为不支持；
            - linkedOntologyUniqueIdentifier / linkedEntityPrimaryKey：可选，关系行为对端对象及其实例主键；不传对端主键时遍历对端全部实例。
            返回行为执行的字符串结果。注意：行为可能修改实例属性或关系数据，执行前确认目标实例与行为语义。
            """)
    public String executeEntityAction(
            @ToolParam(description = "行为挂载对象的唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "目标实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey,
            @ToolParam(description = "对象所属空间 id（整数）") Integer ontologySpaceId,
            @ToolParam(description = "行为 api 名称（actionApi）") String actionApi,
            @ToolParam(description = "存储分组行的数据主键；属性值行为修改/删除定位具体行用，main 分组可不传", required = false) String dataPrimaryKey,
            @ToolParam(description = "各对象选中实例主键 JSON 对象字符串：对象id -> 实例主键，如 {\"obj_b\":2}", required = false) String entityPrimaryKeysJson,
            @ToolParam(description = "按算子入参指定数据位置的 JSON 对象字符串：入参名 -> {\"entityPrimaryKey\":1,\"dataPrimaryKeys\":{\"分组名\":行主键}}", required = false) String inputParamsJson,
            @ToolParam(description = "按出参绑定指定写回位置的 JSON 对象字符串：key -> {\"entityPrimaryKey\":1,\"dataPrimaryKeys\":{\"分组名\":行主键}}", required = false) String outputParamsJson,
            @ToolParam(description = "关系行为对端对象唯一标识", required = false) String linkedOntologyUniqueIdentifier,
            @ToolParam(description = "关系行为对端实体主键值；数字主键传数字字符串", required = false) String linkedEntityPrimaryKey) throws Exception {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        if (ontologySpaceId == null) {
            throw new IllegalArgumentException("ontologySpaceId 不能为空");
        }
        requireText(actionApi, "actionApi");
        EntityActionExecuteParam param = new EntityActionExecuteParam();
        param.setOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        param.setEntityPrimaryKey(coercePrimaryKey(entityPrimaryKey));
        param.setOntologySpaceId(ontologySpaceId);
        param.setActionApi(actionApi);
        if (StringUtils.isNotBlank(dataPrimaryKey)) {
            param.setDataPrimaryKey(coercePrimaryKey(dataPrimaryKey));
        }
        if (StringUtils.isNotBlank(entityPrimaryKeysJson)) {
            Map<String, Object> entityPrimaryKeys = parseJson(entityPrimaryKeysJson,
                    new TypeReference<Map<String, Object>>() {
                    }, "entityPrimaryKeysJson");
            entityPrimaryKeys.replaceAll((k, v) -> v instanceof String s ? coercePrimaryKey(s) : v);
            param.setEntityPrimaryKeys(entityPrimaryKeys);
        }
        if (StringUtils.isNotBlank(inputParamsJson)) {
            param.setInputParams(parseJson(inputParamsJson,
                    new TypeReference<Map<String, ActionInstanceTargetParam>>() {
                    }, "inputParamsJson"));
        }
        if (StringUtils.isNotBlank(outputParamsJson)) {
            param.setOutputParams(parseJson(outputParamsJson,
                    new TypeReference<Map<String, ActionInstanceTargetParam>>() {
                    }, "outputParamsJson"));
        }
        param.setLinkedOntologyUniqueIdentifier(linkedOntologyUniqueIdentifier);
        if (StringUtils.isNotBlank(linkedEntityPrimaryKey)) {
            param.setLinkedEntityPrimaryKey(coercePrimaryKey(linkedEntityPrimaryKey));
        }
        return entityService.executeAction(param);
    }

    @Tool(description = """
            批量新增实体，返回新建实体的主键列表（对应 REST 端点 POST /entity/create）。
            当需要给对象写入一条或多条实例数据时使用。对象必须已有主键属性且主键已绑定数据源，否则报错。
            entityListJson 为 JSON 数组字符串，每个元素是一个实体，形如：
            {"entityProperties":[{"storageGroup":"main","props":[[{"propertyApiName":"name","propertyValue":"实例A"},{"propertyApiName":"age","propertyValue":18}]]},
            {"storageGroup":"扩展分组名","props":[[{"propertyApiName":"字段1","propertyValue":"值1"}],[{"propertyApiName":"字段1","propertyValue":"值2"}]]}]}
            结构约定：
            - 每个实体必须包含 storageGroup=main 的主属性分组，main 分组只取 props 的第一行作为主属性；
            - props 是二维数组：外层每个元素是一行，内层是该行的属性赋值列表；非 main 分组每行插入一条关联表数据；
            - 每个属性赋值：propertyApiName 必填（属性 api 名称，须已关联数据源），propertyValue 必填（按属性类型给值，数字给数字、文本给字符串）；
            - 非 main 存储分组必须已绑定数据源。
            返回 List<Object>：按传入顺序的新实体主键列表。注意：只写数据湖实体表，不自动创建图节点（需要时用 createEntityNodes/completeEntityNode 同步）。
            """)
    public List<Object> createEntities(
            @ToolParam(description = "对象唯一标识（ontologyIdentifier）") String ontologyIdentifier,
            @ToolParam(description = "实体列表 JSON 数组字符串，元素结构见工具描述（entityProperties -> storageGroup + props 二维数组）") String entityListJson) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        requireText(entityListJson, "entityListJson");
        List<EntityCreateParam.Entity> entityList = parseJson(entityListJson,
                new TypeReference<List<EntityCreateParam.Entity>>() {
                }, "entityListJson");
        if (CollectionUtils.isEmpty(entityList)) {
            throw new IllegalArgumentException("entityListJson 不能为空数组，实体列表为空");
        }
        for (EntityCreateParam.Entity entity : entityList) {
            if (entity == null || CollectionUtils.isEmpty(entity.getEntityProperties())) {
                throw new IllegalArgumentException("每个实体的 entityProperties 不能为空，实体属性列表为空");
            }
            validateEntityPropertyGroups(entity.getEntityProperties());
            boolean hasMain = entity.getEntityProperties().stream()
                    .anyMatch(g -> "main".equals(g.getStorageGroup()));
            if (!hasMain) {
                throw new IllegalArgumentException("每个实体的 entityProperties 必须包含 storageGroup=main 的主属性分组");
            }
        }
        EntityCreateParam param = new EntityCreateParam();
        param.setOntologyIdentifier(ontologyIdentifier);
        param.setEntityList(entityList);
        return entityService.createEntities(param);
    }

    @Tool(description = """
            给已存在的实体批量插入关联属性行（对应 REST 端点 POST /entity/insert_property）。
            当需要给某个实例的非 main 存储分组（一对多扩展表）追加多行属性数据时使用。
            实体必须已存在（按实体主键校验），对象主键属性必须已绑定数据源。
            entityPropertiesJson 为 JSON 数组字符串，每个元素是一个存储分组，形如：
            [{"storageGroup":"扩展分组名","props":[[{"propertyApiName":"字段1","propertyValue":"值1"}],[{"propertyApiName":"字段1","propertyValue":"值2"}]]}]
            - storageGroup：必填，存储分组名称，必须已绑定数据源；main 分组会被忽略（主属性不能通过本工具插入）；
            - props：必填二维数组，外层每个元素是一行，内层是该行属性赋值列表；propertyApiName 必填且须已关联数据源，propertyValue 必填。
            副作用：向各分组对应的关联表批量插入行。成功返回中文结果说明。
            """)
    public String insertEntityProperty(
            @ToolParam(description = "对象唯一标识（ontologyIdentifier）") String ontologyIdentifier,
            @ToolParam(description = "实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey,
            @ToolParam(description = "存储分组列表 JSON 数组字符串，元素形如 {\"storageGroup\":\"分组名\",\"props\":[[{\"propertyApiName\":\"name\",\"propertyValue\":\"值\"}]]}") String entityPropertiesJson) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        requireText(entityPropertiesJson, "entityPropertiesJson");
        List<EntityCreateParam.EntityProperty> entityProperties = parseJson(entityPropertiesJson,
                new TypeReference<List<EntityCreateParam.EntityProperty>>() {
                }, "entityPropertiesJson");
        if (CollectionUtils.isEmpty(entityProperties)) {
            throw new IllegalArgumentException("entityPropertiesJson 不能为空数组，实体属性列表为空");
        }
        validateEntityPropertyGroups(entityProperties);
        EntityPropertyInsertParam param = new EntityPropertyInsertParam();
        param.setOntologyIdentifier(ontologyIdentifier);
        param.setEntityPrimaryKey(coercePrimaryKey(entityPrimaryKey));
        param.setEntityProperties(entityProperties);
        entityService.insertEntityProperty(param);
        return "实体属性插入成功，实体主键=" + entityPrimaryKey + "，共 " + entityProperties.size() + " 个存储分组";
    }

    @Tool(description = """
            更新实体某存储分组中一行的属性值（对应 REST 端点 PUT /entity/update_property）。
            当需要修改实例主属性（main 分组）或扩展分组某一行属性时使用。
            定位方式：实体主键 + 存储分组 + 行数据主键（dataPrimaryKey，可从 getEntityPropertyRowDetail 返回的 dataPrimaryKey 获取）。
            校验：实体必须存在；分组必须已绑定数据源；main 分组的 dataPrimaryKey 必须等于实体主键；
            非 main 分组的行必须属于该实体（不允许改其他实体的数据）；主键属性本身不能修改；属性必须已关联数据源。
            propsJson 为 JSON 数组字符串（一维），形如 [{"propertyApiName":"name","propertyValue":"新值"}]，
            propertyApiName 必填，propertyValue 必填（按属性类型给值）。只更新给出的属性，未给出的列保持原值。
            成功返回中文结果说明。
            """)
    public String updateEntityProperty(
            @ToolParam(description = "对象唯一标识（ontologyIdentifier）") String ontologyIdentifier,
            @ToolParam(description = "实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey,
            @ToolParam(description = "属性存储分组名称，主属性为 main") String storageGroup,
            @ToolParam(description = "行数据主键（dataPrimaryKey）；main 分组须与实体主键一致，扩展分组传要修改的行主键") String dataPrimaryKey,
            @ToolParam(description = "属性赋值 JSON 数组字符串，形如 [{\"propertyApiName\":\"name\",\"propertyValue\":\"新值\"}]") String propsJson) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        requireText(storageGroup, "storageGroup");
        requireText(dataPrimaryKey, "dataPrimaryKey");
        requireText(propsJson, "propsJson");
        List<EntityCreateParam.PropertyInfo> props = parseJson(propsJson,
                new TypeReference<List<EntityCreateParam.PropertyInfo>>() {
                }, "propsJson");
        validatePropertyInfos(props);
        EntityPropertyUpdateParam param = new EntityPropertyUpdateParam();
        param.setOntologyIdentifier(ontologyIdentifier);
        param.setEntityPrimaryKey(coercePrimaryKey(entityPrimaryKey));
        param.setStorageGroup(storageGroup);
        param.setDataPrimaryKey(coercePrimaryKey(dataPrimaryKey));
        param.setProps(props);
        entityService.updateEntityProperty(param);
        return "实体属性更新成功，实体主键=" + entityPrimaryKey + "，存储分组=" + storageGroup + "，行数据主键=" + dataPrimaryKey;
    }

    @Tool(description = """
            删除实体或实体的某行属性数据（对应 REST 端点 DELETE /entity/delete，body 参数已平铺）。
            storageGroup=main 时删除整个实体：主表行及所有关联分组表中属于该实体的行全部删除（此时 dataPrimaryKey 必须等于实体主键）；
            storageGroup 为扩展分组时只删除该分组中指定的一行（dataPrimaryKey 传行主键，行必须属于该实体）。
            校验：实体必须存在；分组必须已绑定数据源；不允许删除其他实体的属性数据。
            注意：删除不可逆，数据从数据湖物理删除且无法恢复；删除整个实体前建议先确认目标主键。
            本工具不删除图节点和关系，需要一并清理时用 deleteEntityNodeAndRelations。成功返回中文结果说明。
            """)
    public String deleteEntityProperty(
            @ToolParam(description = "对象唯一标识（ontologyIdentifier）") String ontologyIdentifier,
            @ToolParam(description = "实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey,
            @ToolParam(description = "属性存储分组名称；main 表示删除整个实体及其全部关联属性行，扩展分组名表示只删该分组一行") String storageGroup,
            @ToolParam(description = "行数据主键（dataPrimaryKey）；main 分组须与实体主键一致，扩展分组传要删除的行主键") String dataPrimaryKey) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        requireText(storageGroup, "storageGroup");
        requireText(dataPrimaryKey, "dataPrimaryKey");
        EntityPropertyDeleteParam param = new EntityPropertyDeleteParam();
        param.setOntologyIdentifier(ontologyIdentifier);
        param.setEntityPrimaryKey(coercePrimaryKey(entityPrimaryKey));
        param.setStorageGroup(storageGroup);
        param.setDataPrimaryKey(coercePrimaryKey(dataPrimaryKey));
        entityService.deleteEntityProperty(param);
        return "main".equals(storageGroup)
                ? "实体删除成功（含全部关联属性行），实体主键=" + entityPrimaryKey
                : "实体属性行删除成功，实体主键=" + entityPrimaryKey + "，存储分组=" + storageGroup + "，行数据主键=" + dataPrimaryKey;
    }

    @Tool(description = """
            创建对象下所有实体的图节点（对应 REST 端点 POST /entity/node）。
            当对象实例数据已写入数据湖、需要把全部实例同步为图库节点（供关系查询/图谱展示）时使用。
            按对象主键属性绑定的数据源读取全部实例行，逐个建立/更新图节点。
            对象未绑定主键数据源时不做处理。副作用：批量写入图库节点。成功返回中文结果说明。
            """)
    public String createEntityNodes(
            @ToolParam(description = "对象唯一标识（ontologyIdentifier）") String ontologyIdentifier) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        entityService.createEntityNodes(ontologyIdentifier);
        return "对象实体节点创建成功，ontologyIdentifier=" + ontologyIdentifier;
    }

    @Tool(description = """
            补全单个实体的图节点（对应 REST 端点 POST /entity/complete_node_and_relations）。
            当单条实例数据写入/变更后，需要把该实例同步（upsert）到图库时使用；按「对象 + 归一化主键」定位已存在节点，仅名称变化时覆盖。
            entityPropertyMapJson 为 JSON 对象字符串，key 是数据源列名（不是属性 apiName），value 是该列的值，
            必须包含主键属性绑定的数据源列（如 {"id":1,"name":"实例A"}）；主键列取值为空时静默跳过。
            展示名取标题键列值（标题键与主键同数据源时），否则回退主键值。
            对象未绑定主键数据源时静默不处理。成功返回中文结果说明。
            """)
    public String completeEntityNode(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "实体单行数据 JSON 对象字符串：数据源列名 -> 列值，须包含主键列，如 {\"id\":1,\"name\":\"实例A\"}") String entityPropertyMapJson) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(entityPropertyMapJson, "entityPropertyMapJson");
        Map<String, Object> entityPropertyMap = parseJson(entityPropertyMapJson,
                new TypeReference<Map<String, Object>>() {
                }, "entityPropertyMapJson");
        if (entityPropertyMap == null || entityPropertyMap.isEmpty()) {
            throw new IllegalArgumentException("entityPropertyMapJson 不能为空对象，须包含主键列的取值");
        }
        entityService.completeEntityNode(ontologyUniqueIdentifier, entityPropertyMap);
        return "实体节点补全成功，ontologyUniqueIdentifier=" + ontologyUniqueIdentifier;
    }

    @Tool(description = """
            删除单个实体的图节点及其全部关系边（对应 REST 端点 DELETE /entity/delete_node_and_relations，body 参数已平铺）。
            当实例已从数据湖删除、需要清理图库中对应节点和相邻关系时使用。
            先删除该实体的所有关系边，再删除节点本身；节点/边不存在时静默跳过（幂等）。
            注意：删除不可逆，图库中的节点与关系边被物理删除；只删图库数据，不删数据湖实体表数据
            （删实体表数据用 deleteEntityProperty）。成功返回中文结果说明。
            """)
    public String deleteEntityNodeAndRelations(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKey) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(entityPrimaryKey, "entityPrimaryKey");
        entityService.deleteEntityNodeAndRelations(ontologyUniqueIdentifier, coercePrimaryKey(entityPrimaryKey));
        return "实体节点及关系删除成功，ontologyUniqueIdentifier=" + ontologyUniqueIdentifier + "，实体主键=" + entityPrimaryKey;
    }

    @Tool(description = """
            更新（或创建）两个实例之间的一条关系边（对应 REST 端点 PUT /entity/update_relation）。
            当需要点亮/修改/停用某条实例关系时使用。关系定义（linkUniqIdentifier）必须已存在。
            边已存在时更新其状态与可见时段；边不存在时自动补建两端图节点并新建边。
            参数说明：
            - linkUniqIdentifier：必填，关系定义唯一标识；
            - entityPrimaryKeyFrom / entityPrimaryKeyTo：必填，关系两端实体主键（对象由关系定义决定）；
            - status：必填，关系状态，可选值 ENABLE（启用）、DISABLE（停用）、DELETE（删除）；
            - visibilityWindowsJson：可选 JSON 数组，可见时间窗列表，元素形如 {"startTime":1769585745344,"endTime":1769672145344}，
              时间为毫秒时间戳；边上的一级起止时间自动取「最近的一个存续窗口」；
            - partialUpdate：可选布尔；true 时状态、时段里没填的字段保留边上原值（实例关系行为的「修改」语义）；不传按全量覆盖（未传时段会清空原时段）。
            副作用：写入/更新图库关系边。成功返回中文结果说明。
            """)
    public String updateEntityRelation(
            @ToolParam(description = "关系定义唯一标识（linkUniqIdentifier）") String linkUniqIdentifier,
            @ToolParam(description = "起点实体主键值；数字主键传数字字符串如 \"1\"，文本主键直接传文本") String entityPrimaryKeyFrom,
            @ToolParam(description = "终点实体主键值；数字主键传数字字符串如 \"2\"，文本主键直接传文本") String entityPrimaryKeyTo,
            @ToolParam(description = "关系状态，可选值：ENABLE（启用）、DISABLE（停用）、DELETE（删除）") String status,
            @ToolParam(description = "可见时间窗 JSON 数组字符串，元素形如 {\"startTime\":毫秒时间戳,\"endTime\":毫秒时间戳}，可为空", required = false) String visibilityWindowsJson,
            @ToolParam(description = "是否部分更新：true 时未填的状态/时段保留边上原值；不传按全量覆盖", required = false) Boolean partialUpdate) {
        requireText(linkUniqIdentifier, "linkUniqIdentifier");
        requireText(entityPrimaryKeyFrom, "entityPrimaryKeyFrom");
        requireText(entityPrimaryKeyTo, "entityPrimaryKeyTo");
        requireText(status, "status");
        Status statusEnum;
        try {
            statusEnum = Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的 status：" + status + "，可选值：ENABLE（启用）、DISABLE（停用）、DELETE（删除）");
        }
        EntityRelationUpdateParam param = new EntityRelationUpdateParam();
        param.setLinkUniqIdentifier(linkUniqIdentifier);
        param.setEntityPrimaryKeyFrom(coercePrimaryKey(entityPrimaryKeyFrom));
        param.setEntityPrimaryKeyTo(coercePrimaryKey(entityPrimaryKeyTo));
        param.setStatus(statusEnum);
        if (StringUtils.isNotBlank(visibilityWindowsJson)) {
            param.setVisibilityWindows(parseJson(visibilityWindowsJson,
                    new TypeReference<List<VisibilityWindow>>() {
                    }, "visibilityWindowsJson"));
        }
        param.setPartialUpdate(partialUpdate);
        entityService.updateEntityRelation(param);
        return "实体关系更新成功，linkUniqIdentifier=" + linkUniqIdentifier
                + "，from=" + entityPrimaryKeyFrom + "，to=" + entityPrimaryKeyTo + "，status=" + statusEnum.name();
    }

    @Tool(description = """
            初始化生成实体数据（对应 REST 端点 POST /entity/generate）。
            当对象允许生成实体（canGenerateEntity）、需要按给定属性初始值批量造数时使用。
            注意：本操作会先清空该对象已有的全部实体数据再重新生成，删除不可逆，调用前务必确认对象下现有实例可以被覆盖！
            参数说明：
            - ontologyIdentifier：必填，对象唯一标识；对象必须允许生成实体，否则报错；
            - count：必填，生成数量，1 到 100 之间；
            - propertyValuesJson：必填 JSON 数组，属性初始值列表，元素形如 {"propertyApiName":"name","propertyValue":"初始值"}，
              propertyApiName 必填（属性 api 名称），propertyValue 必填（按属性类型给值，会做类型转换）；
              每条生成的实例都使用这组相同的初始值，主键由数据库自动生成。
            对象主键不存在或未绑定数据源时静默不处理。副作用：清空并重建数据湖实体主表数据。成功返回中文结果说明。
            """)
    public String generateEntities(
            @ToolParam(description = "对象唯一标识（ontologyIdentifier）") String ontologyIdentifier,
            @ToolParam(description = "生成数量，1 到 100 之间") Integer count,
            @ToolParam(description = "属性初始值 JSON 数组字符串，元素形如 {\"propertyApiName\":\"name\",\"propertyValue\":\"初始值\"}") String propertyValuesJson) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        if (count == null) {
            throw new IllegalArgumentException("count 不能为空");
        }
        if (count < 1 || count > 100) {
            throw new IllegalArgumentException("count 必须在 1 到 100 之间");
        }
        requireText(propertyValuesJson, "propertyValuesJson");
        List<EntityPropertyValueParam> propertyValues = parseJson(propertyValuesJson,
                new TypeReference<List<EntityPropertyValueParam>>() {
                }, "propertyValuesJson");
        if (CollectionUtils.isEmpty(propertyValues)) {
            throw new IllegalArgumentException("propertyValuesJson 不能为空数组，propertyValues 为空");
        }
        for (EntityPropertyValueParam pv : propertyValues) {
            if (pv == null || StringUtils.isBlank(pv.getPropertyApiName())) {
                throw new IllegalArgumentException("propertyValuesJson 每个元素的 propertyApiName 不能为空");
            }
            if (pv.getPropertyValue() == null) {
                throw new IllegalArgumentException("属性 " + pv.getPropertyApiName() + " 的 propertyValue 不能为空");
            }
        }
        EntityGenerateParam param = new EntityGenerateParam();
        param.setOntologyIdentifier(ontologyIdentifier);
        param.setCount(count);
        param.setPropertyValues(propertyValues);
        entityService.generateEntities(param);
        return "实体数据初始化生成成功，ontologyIdentifier=" + ontologyIdentifier + "，生成数量=" + count;
    }

    @Tool(description = """
            实体属性通用查询：支持选择返回属性（含聚合函数）、过滤树、分组、排序、分页（对应 REST 端点 POST /entity/generic_query）。
            当需要对实例数据做统计聚合（求和/计数/平均/最大/最小/去重）、条件过滤或分组汇总时使用；
            只查指定属性列的简单场景可用 queryInstancePropertyData。
            参数说明：
            - ontologyIdentifier：必填，对象唯一标识；对象主键属性必须已绑定数据源；
            - selectPropertiesJson：必填 JSON 数组，返回属性列表，元素形如 {"propertyApiName":"id","aggFunc":"COUNT","alias":"cnt"}；
              propertyApiName 必填且须已关联数据源；aggFunc 可选，取值 SUM、COUNT、AVG、MAX、MIN、DISTINCT（不传表示普通列）；alias 可选别名；
            - entityPrimaryKey：可选，实例主键；非空时查询范围限定到该实例，与 filters 是并列的 AND 条件；
            - filtersJson：可选过滤树 JSON 对象，形如 {"logic":"AND","children":[{"type":"FILTER","filter":{"propertyApiName":"age","op":"GT","value":18}},{"type":"GROUP","group":{"logic":"OR","children":[...]}}]}；
              logic 取值 AND/OR（默认 AND）；子节点 type 取值 FILTER（单条件，填 filter）或 GROUP（嵌套组，填 group）；
              filter 的 op 取值 EQ、NE、LIKE、LIKE_LEFT、LIKE_RIGHT、IN、NOT_IN、BETWEEN、NOT_BETWEEN、GT、GE、LT、LE、IS_NULL、IS_NOT_NULL（默认 EQ）；
              单值条件用 value，IN/BETWEEN 用 values 数组（BETWEEN 约定 [下界,上界]）；
            - groupByJson：可选 JSON 字符串数组，分组属性 apiName 列表，如 ["name"]；
            - orderByJson：可选 JSON 数组，排序列表，元素形如 {"propertyApiName":"id","sort":"DESC"}，sort 取值 ASC/DESC（默认 ASC）；
            - pageNum/pageSize：分页，默认 1/10。
            返回 Page<List<EntityPropertyGenericQueryVO>>：每行是属性值列表，元素含 propertyApiName、propertyDisplayName、
            aggFunc、alias、value（返回值）。
            """)
    public Page<List<EntityPropertyGenericQueryVO>> genericQueryEntityProperties(
            @ToolParam(description = "对象唯一标识（ontologyIdentifier）") String ontologyIdentifier,
            @ToolParam(description = "返回属性 JSON 数组字符串，元素形如 {\"propertyApiName\":\"id\",\"aggFunc\":\"COUNT\",\"alias\":\"cnt\"}；aggFunc 可选：SUM/COUNT/AVG/MAX/MIN/DISTINCT") String selectPropertiesJson,
            @ToolParam(description = "实例主键值，非空时限定到该实例；数字主键传数字字符串", required = false) String entityPrimaryKey,
            @ToolParam(description = "过滤树 JSON 对象字符串，结构见工具描述（logic + children，FILTER/GROUP 嵌套）", required = false) String filtersJson,
            @ToolParam(description = "分组属性 apiName JSON 字符串数组，如 [\"name\"]", required = false) String groupByJson,
            @ToolParam(description = "排序 JSON 数组字符串，元素形如 {\"propertyApiName\":\"id\",\"sort\":\"DESC\"}，sort：ASC/DESC", required = false) String orderByJson,
            @ToolParam(description = "页码，从 1 开始；不传默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数；不传默认 10", required = false) Integer pageSize) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        requireText(selectPropertiesJson, "selectPropertiesJson");
        List<OntologySelectPropertyParam> selectProperties = parseJson(selectPropertiesJson,
                new TypeReference<List<OntologySelectPropertyParam>>() {
                }, "selectPropertiesJson");
        if (CollectionUtils.isEmpty(selectProperties)) {
            throw new IllegalArgumentException("selectPropertiesJson 不能为空数组，至少选择一个属性返回");
        }
        for (var sp : selectProperties) {
            if (sp == null || StringUtils.isBlank(sp.getPropertyApiName())) {
                throw new IllegalArgumentException("selectPropertiesJson 每个元素的 propertyApiName 不能为空");
            }
        }
        EntityPropertyGenericQueryParam param = new EntityPropertyGenericQueryParam();
        param.setOntologyIdentifier(ontologyIdentifier);
        param.setSelectProperties(selectProperties);
        if (StringUtils.isNotBlank(entityPrimaryKey)) {
            param.setEntityPrimaryKey(coercePrimaryKey(entityPrimaryKey));
        }
        if (StringUtils.isNotBlank(filtersJson)) {
            param.setFilters(parseJson(filtersJson, new TypeReference<FilterGroupParam>() {
            }, "filtersJson"));
        }
        if (StringUtils.isNotBlank(groupByJson)) {
            param.setGroupBy(parseJson(groupByJson, new TypeReference<List<String>>() {
            }, "groupByJson"));
        }
        if (StringUtils.isNotBlank(orderByJson)) {
            param.setOrderBy(parseJson(orderByJson, new TypeReference<List<OrderByParam>>() {
            }, "orderByJson"));
        }
        param.setPageNum(pageNum == null || pageNum < 1 ? 1 : pageNum);
        param.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);
        return entityService.genericQuery(param);
    }

    @Tool(description = """
            实例数据属性查询：按空间 + 对象查询指定属性列的值，支持可选过滤条件（对应 REST 端点 POST /entity/instance_data/query）。
            当需要按属性 apiName 列表取实例数据（不做聚合）时使用；过滤条件在执行前做完整校验：
            条件中的属性必须属于该对象且已关联数据源，值类型须与属性定义类型兼容（LIKE 类操作只适用于字符串属性）。
            参数说明：
            - spaceId：必填，对象空间 id（整数）；
            - ontologyUniqueIdentifier：必填，对象唯一标识；
            - propertyApiNamesJson：必填 JSON 字符串数组，要查询的属性 apiName 列表，如 ["name","age","time"]，属性必须存在且已关联数据源；
            - filtersJson：可选过滤树 JSON 对象，形如 {"logic":"AND","children":[{"type":"FILTER","filter":{"propertyApiName":"age","op":"GE","value":18}}]}；
              logic 取值 AND/OR（默认 AND）；子节点 type 取值 FILTER（填 filter）或 GROUP（填 group，可嵌套）；
              filter 的 op 取值 EQ、NE、LIKE、LIKE_LEFT、LIKE_RIGHT、IN、NOT_IN、BETWEEN、NOT_BETWEEN、GT、GE、LT、LE、IS_NULL、IS_NOT_NULL（默认 EQ）；
              单值条件用 value，IN/BETWEEN 用 values 数组（BETWEEN 约定 [下界,上界]）；
            - pageNum/pageSize：分页，默认 1/10。
            返回 Page<List<EntityPropertyGenericQueryVO>>：每行是属性值列表，元素含 propertyApiName、propertyDisplayName、value。
            需要聚合/分组/排序时改用 genericQueryEntityProperties。
            """)
    public Page<List<EntityPropertyGenericQueryVO>> queryInstancePropertyData(
            @ToolParam(description = "对象空间 id（整数）") Integer spaceId,
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "属性 apiName JSON 字符串数组，如 [\"name\",\"age\",\"time\"]") String propertyApiNamesJson,
            @ToolParam(description = "过滤树 JSON 对象字符串，结构见工具描述（logic + children，FILTER/GROUP 嵌套），可为空", required = false) String filtersJson,
            @ToolParam(description = "页码，从 1 开始；不传默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数；不传默认 10", required = false) Integer pageSize) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(propertyApiNamesJson, "propertyApiNamesJson");
        List<String> propertyApiNames = parseJson(propertyApiNamesJson,
                new TypeReference<List<String>>() {
                }, "propertyApiNamesJson");
        if (CollectionUtils.isEmpty(propertyApiNames)) {
            throw new IllegalArgumentException("propertyApiNamesJson 不能为空数组，propertyApiNames 为空");
        }
        EntityInstanceDataQueryParam param = new EntityInstanceDataQueryParam();
        param.setSpaceId(spaceId);
        param.setOntologyUniqueIdentifier(ontologyUniqueIdentifier);
        param.setPropertyApiNames(propertyApiNames);
        if (StringUtils.isNotBlank(filtersJson)) {
            param.setFilters(parseJson(filtersJson, new TypeReference<FilterGroupParam>() {
            }, "filtersJson"));
        }
        param.setPageNum(pageNum == null || pageNum < 1 ? 1 : pageNum);
        param.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);
        return entityService.queryInstancePropertyData(param);
    }

    /**
     * 校验存储分组列表（复刻控制器 @Valid 级联校验：storageGroup 非空、props 非空、每个属性赋值合法）。
     */
    private void validateEntityPropertyGroups(List<EntityCreateParam.EntityProperty> groups) {
        for (EntityCreateParam.EntityProperty group : groups) {
            if (group == null || StringUtils.isBlank(group.getStorageGroup())) {
                throw new IllegalArgumentException("每个属性分组的 storageGroup 不能为空");
            }
            if (CollectionUtils.isEmpty(group.getProps())) {
                throw new IllegalArgumentException("存储分组 " + group.getStorageGroup() + " 的 props 不能为空，分组属性列表为空");
            }
            for (List<EntityCreateParam.PropertyInfo> row : group.getProps()) {
                validatePropertyInfos(row);
            }
        }
    }

    /**
     * 校验属性赋值列表（复刻 PropertyInfo 的 @NotBlank/@NotNull：propertyApiName 非空、propertyValue 非 null）。
     */
    private void validatePropertyInfos(List<EntityCreateParam.PropertyInfo> props) {
        if (CollectionUtils.isEmpty(props)) {
            throw new IllegalArgumentException("props 不能为空，分组属性列表为空");
        }
        for (EntityCreateParam.PropertyInfo prop : props) {
            if (prop == null || StringUtils.isBlank(prop.getPropertyApiName())) {
                throw new IllegalArgumentException("每个属性赋值的 propertyApiName 不能为空");
            }
            if (prop.getPropertyValue() == null) {
                throw new IllegalArgumentException("属性 " + prop.getPropertyApiName() + " 的 propertyValue 不能为空");
            }
        }
    }

    private void requireText(String value, String paramName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
    }

    private <T> T parseJson(String json, TypeReference<T> type, String paramName) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(paramName + " JSON 解析失败：" + e.getMessage());
        }
    }

    /**
     * 把主键字符串还原成合适的 Java 类型：能解析为整数用 {@link Long}，能解析为小数用 {@link BigDecimal}，
     * 否则按文本处理。这样数字主键传给 SQL 仍是数字（{@code WHERE "ID" = 1} 可命中），文本主键仍是文本。
     *
     * <p>之所以 {@code @Tool} 的 {@code entityPrimaryKey} 声明为 {@link String} 而非 {@link Object}：
     * Spring AI 的 {@code MethodToolCallback} 用 {@code parameter.getType().isAssignableFrom(ToolContext.class)}
     * 判断是否向参数注入 ToolContext，而 {@code Object.isAssignableFrom(ToolContext)} 恒为 true，
     * 任何 Object 类型参数都会被误注入 ToolContext，导致 MyBatis 报
     * "Can't infer the SQL type to use for an instance of ToolContext"。</p>
     */
    private Object coercePrimaryKey(String raw) {
        String s = raw.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ignored) {
        }
        return s;
    }
}