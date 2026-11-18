package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyCreateDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyAssetBuildParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyBatchDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaBriefVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 对象元数据 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明对象元数据的
 * 查询与增删改原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集为 {@code ToolCallbackProvider}，
 * agent-server 端经 {@code tools/list} 动态发现并按需调用。工具方法直接返回既有业务 VO，
 * Spring AI 自动序列化为 MCP 结果；方法委托既有 Service，不含业务逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyMetaMcpTools implements McpTools {

    private final OntologyMetaService ontologyMetaService;
    private final OntologySpaceService ontologySpaceService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            按关键词搜索对象（Ontology）元数据。
            当用户想查找、检索、列出对象，或询问"有哪些对象""某某对象是否存在"时使用。
            返回对象的名称、描述、唯一标识、所属空间及实例/关系/属性数量等信息。
            关键词为空时返回全部对象。
            """)
    public List<OntologyMetaInfoVO> searchOntologyMeta(
            @ToolParam(description = "搜索关键词，可为空；为空时返回全部对象", required = false) String keyword,
            @ToolParam(description = "空间 ID，用于过滤搜索结果", required = false) Integer spaceId) {
        return ontologyMetaService.searchByKeyword(keyword, spaceId);
    }

    @Tool(description = """
            根据对象唯一标识（uniqueIdentifier）查询单个对象的元数据详情。
            当用户已经知道某个对象的唯一标识，想了解该对象的详细信息时使用。
            返回该对象的名称、描述、所属空间及各类数量统计。
            """)
    public OntologyMetaInfoVO getOntologyMetaDetail(
            @ToolParam(description = "对象唯一标识 uniqueIdentifier") String uniqueIdentifier) {
        var meta = ontologyMetaService.getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, uniqueIdentifier));
        if (meta == null) {
            throw new IllegalArgumentException("对象不存在:" + uniqueIdentifier);
        }
        return ontologyMetaService.getMetaByUniqueIdentifier(uniqueIdentifier);
    }

    @Tool(description = """
            按对象空间 id（spaceId）列出该空间下已存在的对象（启用中）。
            当用户在创建新对象时需要推导它与同空间已有对象的关系（功能四）时使用，
            返回包含 displayName、apiName、uniqueIdentifier、description 等字段，不含实体与属性详情。
            空间内无已有对象时返回空集合，调用方应据此短路关系推导。
            """)
    public List<OntologyMetaInfoVO> listOntologyMetaBySpace(
            @ToolParam(description = "对象空间 id（ontology_space 主键）") Integer spaceId) {
        return ontologyMetaService.listBySpaceId(spaceId);
    }

    @Tool(description = """
            按空间名称 + 对象名称精确查询对象。
            当用户已知空间名称和对象名称（apiName 或 displayName），需要查找该对象的详细信息时使用。
            先按空间名定位空间，再在该空间内按对象名精确匹配，返回匹配的对象列表。
            无匹配时返回空列表。
            """)
    public List<OntologyMetaInfoVO> queryOntologyMetaBySpace(
            @ToolParam(description = "空间名称（apiName 或 displayName）") String spaceName,
            @ToolParam(description = "对象名称（apiName 或 displayName）") String objectName) {
        return ontologyMetaService.queryMetaBySpaceAndName(spaceName, objectName);
    }

    @Tool(description = """
            创建新对象（Ontology）。
            当用户需要新建一个对象、添加对象到对象空间时使用。
            返回新创建对象的唯一标识（uniqueIdentifier）。
            """)
    public String createOntology(
            @ToolParam(description = "对象空间 ID") Integer spaceId,
            @ToolParam(description = "对象显示名称") String displayName,
            @ToolParam(description = "对象 API 名称（代码中使用）") String apiName,
            @ToolParam(description = "对象描述，可为空", required = false) String description,
            @ToolParam(description = "分组 ID 列表，可为空", required = false) Set<String> groupIds,
            @ToolParam(description = "继承的父对象唯一标识，可为空", required = false) String parentOntologyUniqueIdentifier,
            @ToolParam(description = "分类 ID，可为空", required = false) Integer categoryId) {
        OntologyMetaCreateParam param = new OntologyMetaCreateParam();
        param.setSpaceId(spaceId);
        param.setDisplayName(displayName);
        param.setApiName(apiName);
        param.setDescription(description);
        param.setGroupIds(groupIds);
        param.setParentOntologyUniqueIdentifier(parentOntologyUniqueIdentifier);
        param.setCategoryId(categoryId);
        return ontologyMetaService.createOntology(param);
    }

    @Tool(description = """
            批量导入对象（从 JSON 字符串）。
            当用户需要从导出的 JSON 数据批量导入对象时使用。
            JSON 格式应与导出模板一致，包含对象的元数据、属性分类、属性 schema、属性列表和关系列表。
            返回导入失败的对象显示名称列表；空列表表示全部成功。
            """)
    public List<String> importOntologiesFromJson(
            @ToolParam(description = "对象导入 JSON 字符串（数组格式）") String jsonContent) {
        return ontologyMetaService.importOntologiesFromJson(jsonContent);
    }

    @Tool(description = """
            批量导入对象到指定空间（从 JSON 字符串）。
            当用户需要将对象导入到特定空间时使用，会自动处理空间 ID 和名称唯一性。
            JSON 格式应与导出模板一致。
            返回导入失败的对象显示名称列表；空列表表示全部成功。
            """)
    public List<String> importOntologiesToSpaceFromJson(
            @ToolParam(description = "目标空间 ID") Integer spaceId,
            @ToolParam(description = "对象导入 JSON 字符串（数组格式）") String jsonContent) {
        return ontologyMetaService.importOntologiesToSpaceFromJson(spaceId, jsonContent);
    }

    @Tool(description = """
            导出对象（对应 REST 端点 GET /meta/export），返回与导入模板对齐的完整 JSON 结构列表。
            当用户需要备份对象、把对象迁移到其他空间/环境，或查看对象的完整定义（元数据、属性分类、
            元数据 schema、属性、对象级关系，可选实例数据）时使用。
            参数说明：
            - uniqueIdentifier：必填，对象唯一标识，支持一次导出多个对象，多个用英文逗号分隔（如 "uid-1,uid-2"）；
              多个对象必须属于同一空间；不存在或已停用的对象会被跳过，全部取不到时报错。
            - exportType：可选，导出类型枚举，取值 SCHEMA（仅结构定义，不含实例数据）或 INSTANCE（结构 + 全部实例数据，默认）。
            返回 List<OntologyCreateDTO>：每个元素含 metadata（对象元数据）、属性分类、schema、属性列表、relations（对象关联关系，
            端点以 displayName 表达；两端都在本次导出列表内的关系只挂在首个涉及它的对象下）。不附带函数定义（函数为空间级资源，随空间导出给出）。
            注意：INSTANCE 模式会带出全部实例数据，数据量可能很大；仅需结构时请传 SCHEMA。
            导出的 JSON 可直接作为 importOntologiesFromJson / importOntologiesToSpaceFromJson 的入参。
            """)
    public List<OntologyCreateDTO> exportOntology(
            @ToolParam(description = "对象唯一标识 uniqueIdentifier，多个用英文逗号分隔，须属于同一空间") String uniqueIdentifier,
            @ToolParam(description = "导出类型：SCHEMA=仅结构，INSTANCE=含实例数据；不传默认 INSTANCE", required = false) String exportType) {
        requireText(uniqueIdentifier, "uniqueIdentifier");
        return ontologyMetaService.exportOntology(uniqueIdentifier, parseExportType(exportType));
    }

    @Tool(description = """
            资产构建对象（对应 REST 端点 POST /meta/build_object）：一次性创建对象元数据并连带创建其全部属性。
            当用户从数据资产/表结构出发，需要"建对象 + 建属性"一步完成时使用，免去先 createOntology 再逐个 createProperty 的多次调用。
            全程单一事务，任一步失败整单回滚；属性校验口径与单个属性新增接口完全一致（apiName 重复、存储分组与对象 apiName 同名等都会报错回滚）。
            参数说明：
            - spaceId：必填，对象空间 id（ontology_space 主键），空间必须存在。
            - displayName：必填，对象显示名称。
            - apiName：必填，对象 API 名称（代码中使用）。
            - description：可选，对象描述。
            - groupIdsJson：可选，分组 id 的 JSON 数组字符串（如 ["123","456"]）。
            - parentOntologyUniqueIdentifier：可选，继承的父对象唯一标识。
            - categoryId：可选，对象分类 id；不传时自动挂到本空间对象分类根节点「全部」。
            - propertiesJson：可选，属性创建对象 JSON 数组字符串，元素字段同属性新增接口：
              displayName/apiName/dataType/isPrimaryKey/isTitleKey/storageGroup 必填，
              description/tag/defaultValue/categoryId/datasource/metadata 可选；
              其中 ontologyIdentifier 无需传，服务端一律用本次新建对象的 uniqueIdentifier 覆盖；
              属性 categoryId 不传时挂到该对象属性分类根节点「全部」。
            属性可带 metadata，服务端会从所有属性的 metadata 自动推导并创建元数据 schema（单根树，枚举值不推断），
            落库时序固定为「对象 → schema → 属性」；同一字段在不同属性 metadata 里既是叶子又是分支（结构冲突）时整单回滚。
            返回新创建对象的唯一标识（uniqueIdentifier）。
            """)
    public String buildObjectWithProperties(
            @ToolParam(description = "对象空间 ID") Integer spaceId,
            @ToolParam(description = "对象显示名称") String displayName,
            @ToolParam(description = "对象 API 名称（代码中使用）") String apiName,
            @ToolParam(description = "对象描述，可为空", required = false) String description,
            @ToolParam(description = "分组 id JSON 数组字符串（如 [\"123\",\"456\"]），可为空", required = false) String groupIdsJson,
            @ToolParam(description = "继承的父对象唯一标识，可为空", required = false) String parentOntologyUniqueIdentifier,
            @ToolParam(description = "对象分类 ID，可为空；不传挂到本空间分类根节点「全部」", required = false) Integer categoryId,
            @ToolParam(description = "属性创建对象 JSON 数组字符串，字段见工具描述；ontologyIdentifier 无需传", required = false) String propertiesJson) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        requireText(displayName, "displayName");
        requireText(apiName, "apiName");
        OntologyAssetBuildParam param = new OntologyAssetBuildParam();
        param.setSpaceId(spaceId);
        param.setDisplayName(displayName);
        param.setApiName(apiName);
        param.setDescription(description);
        if (StringUtils.isNotBlank(groupIdsJson)) {
            param.setGroupIds(parseJson(groupIdsJson, new TypeReference<Set<String>>() {
            }, "groupIdsJson"));
        }
        param.setParentOntologyUniqueIdentifier(parentOntologyUniqueIdentifier);
        param.setCategoryId(categoryId);
        if (StringUtils.isNotBlank(propertiesJson)) {
            param.setProperties(parseJson(propertiesJson, new TypeReference<List<OntologyPropertyCreateParam>>() {
            }, "propertiesJson"));
        }
        return ontologyMetaService.createOntologyWithProperties(param);
    }

    @Tool(description = """
            删除单个对象（对应 REST 端点 DELETE /meta/{ontologyIdentifier}）。
            当用户明确要求删除某个对象时使用。
            参数 ontologyIdentifier：必填，对象唯一标识（uniqueIdentifier），对象必须存在，否则报错。
            副作用：级联删除对象元数据、属性分类、属性元数据 schema、全部属性、关联关系、行为（含参数/规则/任务）
            以及图数据库中的实体节点和边。
            警告：本操作不可逆，删除后对象及其全部关联数据无法恢复，调用前务必向用户确认；
            如需保留定义请先用 exportOntology 导出备份。成功返回中文结果说明。
            """)
    public String deleteOntology(
            @ToolParam(description = "对象唯一标识 uniqueIdentifier") String ontologyIdentifier) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        ontologyMetaService.deleteOntology(ontologyIdentifier);
        return "对象删除成功：" + ontologyIdentifier;
    }

    @Tool(description = """
            批量删除对象及其关联数据（对应 REST 端点 DELETE /meta/batch）。
            当用户需要一次性删除同一空间下的多个对象时使用。
            参数说明：
            - spaceId：必填，对象空间 id；所有待删对象必须属于该空间。
            - ontologyIdentifiersJson：必填，对象唯一标识的 JSON 数组字符串（如 ["uid-1","uid-2"]），不能为空数组；
              任一对象不存在或不属于该空间时整批报错、不删除。
            副作用：级联删除每个对象的属性、关系、分类树、行为等全部关联数据，并删除数据湖中的实例数据。
            警告：本操作不可逆，删除后无法恢复，调用前务必向用户确认清单；如需保留定义请先用 exportOntology 导出备份。
            成功返回中文结果说明（含删除数量）。
            """)
    public String batchDeleteOntologies(
            @ToolParam(description = "对象空间 ID，所有待删对象须属于该空间") Integer spaceId,
            @ToolParam(description = "对象唯一标识 JSON 数组字符串，如 [\"uid-1\",\"uid-2\"]，不能为空") String ontologyIdentifiersJson) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        requireText(ontologyIdentifiersJson, "ontologyIdentifiersJson");
        List<String> identifiers = parseJson(ontologyIdentifiersJson, new TypeReference<List<String>>() {
        }, "ontologyIdentifiersJson");
        if (CollectionUtils.isEmpty(identifiers)) {
            throw new IllegalArgumentException("ontologyIdentifiersJson 不能为空数组");
        }
        OntologyBatchDeleteParam param = new OntologyBatchDeleteParam();
        param.setSpaceId(spaceId);
        param.setOntologyIdentifiers(identifiers);
        ontologyMetaService.batchDeleteOntologies(param);
        return "批量删除对象成功，共 " + identifiers.size() + " 个对象";
    }

    @Tool(description = """
            修改对象元数据（对应 REST 端点 PUT /meta）。
            当用户需要修改对象的显示名称、描述、图标、分组、分类或 API 名称时使用。
            参数说明：
            - ontologyIdentifier：必填，对象唯一标识（uniqueIdentifier），对象必须存在，否则报错。
            - displayName：必填，对象显示名称。
            - groupIdsJson：必填，分组 id 的 JSON 数组字符串（如 ["123","456"]），不能为空数组（整体覆盖原有分组）。
            - apiName：可选，对象 API 名称（代码中使用）。不传或传空则保持原值不变；
              传入新值时会在同空间内查重，与其他对象 apiName 重名会报错（400），请确保空间内唯一。
            - description：可选，对象描述；本接口为整体覆盖式更新，不传会清空原描述。
            - icon：可选，对象图标 URL；不传会清空原图标。
            - categoryId：可选，对象分类 id；不传会清空原分类。
            注意：除 apiName 外，未传的可选字段（description、icon、categoryId）会被置空，
            修改前建议先用 getOntologyMetaDetail 查出当前值再整体回填。
            成功返回中文结果说明。
            """)
    public String updateOntology(
            @ToolParam(description = "对象唯一标识 uniqueIdentifier") String ontologyIdentifier,
            @ToolParam(description = "对象显示名称") String displayName,
            @ToolParam(description = "分组 id JSON 数组字符串，如 [\"123\",\"456\"]，不能为空，整体覆盖原有分组") String groupIdsJson,
            @ToolParam(description = "对象 API 名称（代码中使用），可选；不传保持原值，传新值时同空间内重名会报错", required = false) String apiName,
            @ToolParam(description = "对象描述，不传则清空", required = false) String description,
            @ToolParam(description = "对象图标 URL，不传则清空", required = false) String icon,
            @ToolParam(description = "对象分类 ID，不传则清空分类", required = false) Integer categoryId) {
        requireText(ontologyIdentifier, "ontologyIdentifier");
        requireText(displayName, "displayName");
        requireText(groupIdsJson, "groupIdsJson");
        List<String> groupIds = parseJson(groupIdsJson, new TypeReference<List<String>>() {
        }, "groupIdsJson");
        if (CollectionUtils.isEmpty(groupIds)) {
            throw new IllegalArgumentException("groupIdsJson 不能为空数组");
        }
        OntologyUpdateParam param = new OntologyUpdateParam();
        param.setOntologyIdentifier(ontologyIdentifier);
        param.setDisplayName(displayName);
        param.setGroupIds(groupIds);
        param.setApiName(apiName);
        param.setDescription(description);
        param.setIcon(icon);
        param.setCategoryId(categoryId);
        ontologyMetaService.updateMeta(param);
        return "对象元数据更新成功：" + displayName + "（uniqueIdentifier=" + ontologyIdentifier + "）";
    }

    @Tool(description = """
            根据本地对象 id（ontology_meta 表主键）查询对象简要信息（对应 REST 端点 GET /meta/{id}）。
            当用户持有的是数据库自增 id（而非 uniqueIdentifier），需要反查对象名称、所属空间或 uniqueIdentifier 时使用。
            参数 id：必填，本地对象 id，数字字符串（如 "123"）。
            返回 OntologyMetaBriefVO：含 id、对象名称、空间名称、uniqueIdentifier；对象不存在时返回 null。
            如需完整元数据详情，请改用 getOntologyMetaDetail（按 uniqueIdentifier 查询）。
            """)
    public OntologyMetaBriefVO getOntologyMetaById(
            @ToolParam(description = "本地对象 id（ontology_meta 主键），数字字符串") String id) {
        requireText(id, "id");
        long metaId;
        try {
            metaId = Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("id 必须是数字（ontology_meta 主键），实际传入：" + id);
        }
        return ontologyMetaService.getMetaById(metaId);
    }

    @Tool(description = """
            统计对象关联的核心资源数量（对应 REST 端点 GET /meta/statistic）。
            当用户询问"某对象有多少实例/属性/关系/行为"，或删除对象前需要评估影响面时使用。
            参数 uniqueIdentifier：必填，对象唯一标识；对象必须存在且启用中，否则报错。
            返回 OntologyMetaStatisticVO：实例数、属性数、关系数（按对象所属空间裁剪的有效关系）、行为数等统计。
            """)
    public OntologyMetaStatisticVO getOntologyStatistic(
            @ToolParam(description = "对象唯一标识 uniqueIdentifier") String uniqueIdentifier) {
        requireText(uniqueIdentifier, "uniqueIdentifier");
        return ontologyMetaService.getStatistic(uniqueIdentifier);
    }

    @Tool(description = """
            根据对象分类 id 查询该分类下的对象列表（对应 REST 端点 GET /meta/category）。
            当用户按分类浏览对象，或询问"某分类下有哪些对象"时使用。
            参数 categoryId：可选，对象分类 id（ontology_category 主键）；不传则查询全部对象（跨空间，数据量可能很大，建议尽量传分类 id）。
            返回 OntologyMetaInfoVO 列表：含对象名称、描述、uniqueIdentifier、所属空间及实例/关系/属性数量统计；无匹配时返回空列表。
            """)
    public List<OntologyMetaInfoVO> listOntologyMetaByCategory(
            @ToolParam(description = "对象分类 id，不传则查询全部对象（跨空间）", required = false) Integer categoryId) {
        return ontologyMetaService.getByCategoryId(categoryId);
    }

    @Tool(description = """
            根据分组 id 查询组内对象（对应 REST 端点 GET /meta/group），支持排序。
            当用户按分组浏览对象，或询问"某分组下有哪些对象"时使用。
            参数说明：
            - groupId：可选，分组 id；不传则查询全部对象。
            - orderBy：可选，排序字段枚举，取值 NAME（按显示名称）、UPDATE_TIME（按更新时间）、QUERY_TIME（按最近查询时间）。
            - sort：可选，排序方向枚举，取值 ASC（升序）、DESC（降序）。
            返回 OntologyGroupMetaVO 列表（含对象元数据及分组信息）；无匹配时返回空列表。
            """)
    public List<OntologyGroupMetaVO> listOntologyMetaByGroup(
            @ToolParam(description = "分组 id，不传则查询全部对象", required = false) String groupId,
            @ToolParam(description = "排序字段：NAME=显示名称，UPDATE_TIME=更新时间，QUERY_TIME=最近查询时间", required = false) String orderBy,
            @ToolParam(description = "排序方向：ASC=升序，DESC=降序", required = false) String sort) {
        OntologyOrderByEnum orderByEnum = null;
        if (StringUtils.isNotBlank(orderBy)) {
            try {
                orderByEnum = OntologyOrderByEnum.valueOf(orderBy.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("无效的 orderBy：" + orderBy + "，可选值：NAME、UPDATE_TIME、QUERY_TIME");
            }
        }
        QuerySortEnum sortEnum = null;
        if (StringUtils.isNotBlank(sort)) {
            try {
                sortEnum = QuerySortEnum.valueOf(sort.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("无效的 sort：" + sort + "，可选值：ASC、DESC");
            }
        }
        return ontologyMetaService.getByGroupId(groupId, orderByEnum, sortEnum);
    }

    @Tool(description = """
            根据分组 id 查询组内对象树（对应 REST 端点 GET /meta/group/tree）。
            当用户需要以树形结构查看某分组下的对象（含继承/层级组织）时使用。
            参数 groupId：可选，分组 id；不传则返回全部对象的树。
            返回 OntologyMetaNodeVO 树形节点列表（含子节点 children）；无匹配时返回空列表。
            如需空间内按继承关系组织的层级树，请改用 getOntologyInheritanceTree。
            """)
    public List<OntologyMetaNodeVO> getOntologyTreeByGroup(
            @ToolParam(description = "分组 id，不传则返回全部对象的树", required = false) String groupId) {
        return ontologyMetaService.getOntologyTreeByByGroupId(groupId);
    }

    @Tool(description = """
            查询空间内对象继承层级树（对应 REST 端点 GET /meta/inheritance_tree）。
            当用户需要了解某空间内对象的父子继承关系、查看对象层级结构时使用。
            参数 spaceId：必填，对象空间 id（ontology_space 主键），空间必须存在，否则报错。
            基于对象的 parentUniqueIdentifier 字段构建树形结构，返回所有根节点（无父对象的节点）及其子孙，
            节点为 OntologyMetaNodeVO（含对象名称、uniqueIdentifier、children 子节点列表）。
            空间内无对象时返回空列表。
            """)
    public List<OntologyMetaNodeVO> getOntologyInheritanceTree(
            @ToolParam(description = "对象空间 id（ontology_space 主键）") Integer spaceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        if (ontologySpaceService.getById(spaceId) == null) {
            throw new IllegalArgumentException("无效的对象空间：" + spaceId);
        }
        return ontologyMetaService.getInheritanceTree(spaceId);
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

    private OntologyExportTypeEnum parseExportType(String exportType) {
        if (StringUtils.isBlank(exportType)) {
            return OntologyExportTypeEnum.INSTANCE;
        }
        try {
            return OntologyExportTypeEnum.valueOf(exportType.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的 exportType：" + exportType + "，可选值：SCHEMA（仅结构）、INSTANCE（含实例数据）");
        }
    }
}
