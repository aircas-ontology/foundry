package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.SpaceTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySubspaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceCanvasCreateVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceStatisticVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySubspaceCreateVO;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 对象空间 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} 声明对象空间的查询与增删改原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。方法委托既有 Service，
 * 直接返回自有业务 VO，Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologySpaceMcpTools implements McpTools {

    private final OntologySpaceService ontologySpaceService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 对象/属性 apiName 格式（与画布参数 Bean 校验一致：NotBlank + Pattern）。 */
    private static final String API_NAME_PATTERN = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$";

    @Tool(description = """
            查询平台上所有对象空间（Ontology Space）列表。
            当用户需要选择对象空间、查看有哪些可用空间，或询问"有哪些空间""列一下空间""选个空间"，
            或在新建对象流程起点（第0步）尚未确定 spaceId 时使用。
            返回每个空间的 spaceId、名称（displayName）、apiName、描述及对象/属性/关系数量统计；
            其中 spaceId 是后续所有需要空间 id 的操作（查询数据源、新建对象、最终落库等）的必填上下文。
            无可用空间时返回空集合。
            """)
    public List<OntologySpaceVO> listOntologySpaces() {
        return ontologySpaceService.querySpace(SpaceTypeEnum.SPACE.getValue());
    }

    @Tool(description = """
            按名称精确查询对象空间。
            当用户已知空间名称（apiName 或 displayName），需要查找该空间的详细信息时使用。
            同时匹配 apiName 和 displayName（OR 关系），返回匹配的空间列表（含 spaceId、名称、描述及统计信息）。
            无匹配时返回空列表。
            """)
    public List<OntologySpaceVO> queryOntologySpace(
            @ToolParam(description = "空间名称，可以是 apiName 或 displayName") String name) {
        return ontologySpaceService.querySpaceByName(name);
    }

    @Tool(description = """
            按类型查询对象空间/主题列表（对应 REST 端点 GET /space）。
            当用户需要列出空间或主题，或在新建对象流程起点尚未确定 spaceId 时使用。
            参数 type：可选，类型枚举值（整数）：0=空间（默认，按配置文件绑定的空间过滤），1=主题（按创建用户名过滤）；不传按空间处理。
            返回每个空间/主题的 spaceId、名称（displayName）、apiName、描述及对象/属性/关系数量统计；
            其中 spaceId 是后续所有需要空间 id 的操作的必填上下文。无可用空间时返回空集合。
            仅需空间列表时也可用 listOntologySpaces（等价于 type=0）。
            """)
    public List<OntologySpaceVO> listOntologySpacesByType(
            @ToolParam(description = "类型：0=空间（默认），1=主题；不传按空间处理", required = false) Integer type) {
        return ontologySpaceService.querySpace(type == null ? SpaceTypeEnum.SPACE.getValue() : type);
    }

    @Tool(description = """
            导出对象空间（对应 REST 端点 GET /space/export），返回完整空间结构 JSON。
            当用户需要备份整个空间、把空间迁移到其他环境，或查看空间完整定义（空间元数据、分类树、
            全部对象 schema、关系、函数，可选实例数据）时使用。
            参数说明：
            - spaceId：必填，对象空间 id（ontology_space 主键），空间必须存在。
            - exportType：可选，导出类型枚举，取值 SCHEMA（仅结构定义，不含实例数据）或 INSTANCE（结构 + 全部实例数据，默认）。
            返回 OntologySpaceCreateDTO：含 ontologySpace（空间元数据）、分类树、ontologies（全部对象导出结构，
            每个对象含元数据、属性分类、schema、属性、关系）及空间级函数定义。
            注意：INSTANCE 模式会带出全部实例数据，数据量可能很大；仅需结构时请传 SCHEMA。
            导出的 JSON 可直接作为 importOntologySpace / importOntologySpaceToTarget 的入参。
            """)
    public OntologySpaceCreateDTO exportOntologySpace(
            @ToolParam(description = "对象空间 id（ontology_space 主键）") Integer spaceId,
            @ToolParam(description = "导出类型：SCHEMA=仅结构，INSTANCE=含实例数据；不传默认 INSTANCE", required = false) String exportType) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        return ontologySpaceService.exportOntologySpace(spaceId, parseExportType(exportType));
    }

    @Tool(description = """
            对象空间导入创建（对应 REST 端点 POST /space/import）：从空间导出 JSON 新建一个完整空间。
            当用户拿到 exportOntologySpace 导出的 JSON（或按导出模板手写的 JSON），需要恢复/复制出一个新空间时使用。
            参数 jsonContent：必填，完整空间结构 JSON 字符串（OntologySpaceCreateDTO 结构，含 ontologySpace、分类树、
            ontologies 对象列表、关系、函数等），会按 JSON 中的空间名称新建空间并导入全部对象。
            返回导入失败的对象 displayName 列表；空列表表示全部成功。
            注意：本工具是"新建空间"式导入；如要把数据导入已存在的空间（覆盖同名对象），请改用 importOntologySpaceToTarget。
            """)
    public List<String> importOntologySpace(
            @ToolParam(description = "完整空间结构 JSON 字符串（与 exportOntologySpace 导出结构一致）") String jsonContent) {
        requireText(jsonContent, "jsonContent");
        return ontologySpaceService.importOntologySpace(toMultipartFile(jsonContent));
    }

    @Tool(description = """
            将空间数据导入到已存在的目标空间（对应 REST 端点 POST /space/import_to_space）。
            当用户需要把导出的空间 JSON 合并进一个已有空间时使用：不创建新空间，覆盖目标空间中同名/同 API 的对象，
            保留目标空间的分类树结构。
            参数说明：
            - spaceId：必填，目标空间 id（ontology_space 主键），空间必须已存在。
            - jsonContent：必填，完整空间结构 JSON 字符串（与 exportOntologySpace 导出结构一致）。
            返回导入失败的对象 displayName 列表；空列表表示全部成功。
            警告：同名/同 API 对象会被覆盖，覆盖不可逆，调用前务必向用户确认；如需保留目标空间现状请先用 exportOntologySpace 备份。
            """)
    public List<String> importOntologySpaceToTarget(
            @ToolParam(description = "目标空间 id（ontology_space 主键），须已存在") Integer spaceId,
            @ToolParam(description = "完整空间结构 JSON 字符串（与 exportOntologySpace 导出结构一致）") String jsonContent) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        requireText(jsonContent, "jsonContent");
        return ontologySpaceService.importOntologySpaceToTarget(spaceId, toMultipartFile(jsonContent));
    }

    @Tool(description = """
            创建对象空间（对应 REST 端点 POST /space）。
            当用户需要新建一个对象空间来组织对象、属性、关系时使用。
            参数说明：
            - displayName：必填，空间显示名称（如 "xxx战场"）。
            - apiName：必填，空间 API 名称（代码中使用），格式 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$：
              以字母、下划线或 $ 开头，只能包含字母、数字、下划线、$，长度不超过 63。
            - description：可选，空间描述。
            - iconUrl：可选，空间图标 URL。
            返回新创建空间的 spaceId（整数），是后续建对象、导数据等操作的必填上下文。
            """)
    public Integer createOntologySpace(
            @ToolParam(description = "空间显示名称") String displayName,
            @ToolParam(description = "空间 API 名称，格式 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$") String apiName,
            @ToolParam(description = "空间描述，可为空", required = false) String description,
            @ToolParam(description = "空间图标 URL，可为空", required = false) String iconUrl) {
        requireText(displayName, "displayName");
        requireText(apiName, "apiName");
        if (!apiName.matches(API_NAME_PATTERN)) {
            throw new IllegalArgumentException("空间 apiName 格式不合法：" + apiName
                    + "，需以字母、下划线或 $ 开头，只能包含字母、数字、下划线、$，长度不超过 63");
        }
        OntologySpaceCreateParam param = new OntologySpaceCreateParam();
        param.setDisplayName(displayName);
        param.setApiName(apiName);
        param.setDescription(description);
        param.setIconUrl(iconUrl);
        return ontologySpaceService.createSpace(param);
    }

    @Tool(description = """
            保存空间画布（对应 REST 端点 POST /space/canvas）：把编辑后的完整画布与加载快照（base）和数据库现状做三段对比，字段级合并后落库。
            当用户在画布上批量新增/修改/删除对象、属性、关系后需要一次性保存时使用。
            参数 canvasJson：必填，画布内容 JSON 字符串（OntologySpaceCanvasCreateParam 结构，与 getSpaceCanvasContent 返回体同构），字段如下：
            - spaceId：必填，对象空间 id。
            - base：可选，加载画布时 getSpaceCanvasContent 返回的原始快照（{"ontologies":[...],"links":[...]} 原样回传，不要修改）；
              不传 base 时为纯新增模式，此时所有项都不能带 uniqueIdentifier。
            - ontologies：编辑后的完整对象列表（删除对象即从列表中移除），每个对象含：
              uniqueIdentifier（已有对象回传原值，新增不传）、displayName（必填）、apiName（必填，格式 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$）、
              description/iconUrl（可选）、properties（属性列表，每个属性含 uniqueIdentifier/displayName/apiName/dataType/storageGroup/
              description/isPrimaryKey/isTitleKey/defaultValue，dataType 不传默认 String，主键/名称键不传默认 false）。
            - links：编辑后的完整关系列表（删除关系即从列表中移除），每个关系含：
              uniqueIdentifier（已有关系回传原值，新增不传）、name（必填）、apiName/description（可选）、
              fromOntologyApiName/toOntologyApiName（必填，源/目标对象 apiName，也兼容传对象显示名称）。
            冲突处理（与 REST 端点一致）：
            - 字段级冲突（reason=FIELD_CONFLICT/DELETED_BY_OTHERS/DELETE_CONFLICT）不覆盖他人修改，其它字段继续保存，committed=true；
            - 结构级冲突（重名 NAME_CONFLICT、改 apiName API_NAME_IMMUTABLE、改关系端点 ENDPOINT_IMMUTABLE、非法 base INVALID_BASE、
              属性删除受限 PROPERTY_DELETE_FORBIDDEN、父对象被删 PARENT_DELETED 等）整单不写，committed=false；
              其中 NAME_CONFLICT/PROPERTY_DELETE_FORBIDDEN 属"与现状冲突"（刷新后重试可解），其余属"请求本身不合法"（需修正请求）；
            - 提交时撞唯一索引（他人同时改名）会转为 NAME_CONFLICT 冲突返回，而不是报 500。
            返回 OntologySpaceCanvasCreateVO：spaceId、committed（false=整单未写入任何数据）、saved（各类新建/更新/删除数量）、
            conflicts（冲突明细，含 type/uniqueIdentifier/reason/message/fields 字段级对比）、canvas（保存后的最新画布，可直接作为下次保存的 base）。
            注意：committed=false 时本次未写入任何数据，须根据 conflicts 修正后重试；删除对象/属性/关系不可逆，调用前务必向用户确认。
            """)
    public OntologySpaceCanvasCreateVO saveSpaceCanvas(
            @ToolParam(description = "画布内容 JSON 字符串（spaceId + base 快照 + ontologies + links），字段见工具描述") String canvasJson) {
        requireText(canvasJson, "canvasJson");
        OntologySpaceCanvasCreateParam param = parseJson(canvasJson, OntologySpaceCanvasCreateParam.class, "canvasJson");
        validateCanvasParam(param);
        try {
            return ontologySpaceService.createSpaceWithCanvasContent(param);
        } catch (DataIntegrityViolationException e) {
            // 极小概率：提交时撞上唯一索引（通常是别人同时改了名），转成冲突而不是 500（与 REST 端点一致）
            var conflict = OntologySpaceCanvasCreateVO.CanvasConflict.builder()
                    .reason("NAME_CONFLICT")
                    .message("保存时与唯一约束冲突，可能有其他人同时修改了名称，请刷新后重试")
                    .build();
            return OntologySpaceCanvasCreateVO.builder()
                    .spaceId(param.getSpaceId())
                    .committed(false)
                    .saved(OntologySpaceCanvasCreateVO.CanvasSaveSummary.builder().build())
                    .conflicts(List.of(conflict))
                    .canvas(param.getSpaceId() == null ? null
                            : ontologySpaceService.getSpaceCanvasContent(param.getSpaceId()))
                    .build();
        }
    }

    @Tool(description = """
            查询空间画布内容（对应 REST 端点 GET /space/canvas）：返回空间下的全部对象、对象属性、对象间关系。
            当用户需要查看/编辑空间画布，或调用 saveSpaceCanvas 前需要获取当前画布及 base 快照时使用。
            参数 spaceId：必填，对象空间 id（ontology_space 主键），空间必须存在，否则报错。
            返回 OntologySpaceCanvasCreateParam：与 saveSpaceCanvas 的 canvasJson 同一结构
            （spaceId + ontologies 对象列表（含 properties 属性列表）+ links 关系列表），编辑后可直接回传保存；
            保存时请把本次返回的 ontologies、links 原样作为 base 快照回传，用于三段对比合并。
            """)
    public OntologySpaceCanvasCreateParam getSpaceCanvasContent(
            @ToolParam(description = "对象空间 id（ontology_space 主键）") Integer spaceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        if (ontologySpaceService.getById(spaceId) == null) {
            throw new IllegalArgumentException("无效的对象空间：" + spaceId);
        }
        return ontologySpaceService.getSpaceCanvasContent(spaceId);
    }

    @Tool(description = """
            基于父空间创建子空间（对应 REST 端点 POST /space/subspace）：从源空间勾选对象、实例（可按属性筛选）、关系，生成新的子空间。
            当用户需要从一个已有空间派生出聚焦特定对象/实例范围的子空间时使用。
            参数 paramJson：必填，子空间创建参数 JSON 字符串（OntologySubspaceCreateParam 结构），字段如下：
            - parentSpaceId：必填整数，父空间（源空间）id，空间必须存在。
            - displayName：必填，子空间显示名称；apiName：必填，子空间 API 名称。
            - description/iconUrl：可选，子空间描述、图标 URL。
            - selectedOntologies：必填非空数组，选中的对象配置，每个元素含：
              sourceOntologyUniqueIdentifier（必填，源空间中被选对象的 uniqueIdentifier）、
              selectedInstancePrimaryKeys（可选，显式勾选的实例主键列表；为空表示按属性筛选条件导入全部匹配实例）、
              selectedProperties（必填非空数组，选中的属性及筛选条件，每个元素含
              sourcePropertyUniqueIdentifier（必填，源属性 uniqueIdentifier）和 filter（可选筛选条件对象，
              含 op（必填操作符，如 EQ/LIKE/GT/IN/BETWEEN）、value（单值 op 传标量，多值 op 传数组，BETWEEN 约定 [lower, upper]）、
              dataType（必填属性数据类型，如 String/Int/Long/Double/Date 等）；不筛选时 filter 传 null））。
            - selectedLinks：可选数组，选中的关系配置，每个元素含 sourceLinkUniqueIdentifier（必填，源关系 uniqueIdentifier）、
              name/apiName/type/description（可选，新关系名称/apiName/类型/描述，为空则沿用源关系或自动生成）。
            返回 OntologySubspaceCreateVO：spaceId（新建子空间 id）、ontologyMapping（源对象→新对象 uniqueIdentifier 映射）、
            linkMapping（源关系→新关系 uniqueIdentifier 映射）。
            """)
    public OntologySubspaceCreateVO createSubspace(
            @ToolParam(description = "子空间创建参数 JSON 字符串（父空间 + 名称 + 选中对象/属性/实例筛选/关系），字段见工具描述") String paramJson) {
        requireText(paramJson, "paramJson");
        OntologySubspaceCreateParam param = parseJson(paramJson, OntologySubspaceCreateParam.class, "paramJson");
        validateSubspaceParam(param);
        return ontologySpaceService.createSubspace(param);
    }

    @Tool(description = """
            修改对象空间（对应 REST 端点 PUT /space）。
            当用户需要修改空间的显示名称、描述或图标时使用。
            参数说明：
            - spaceId：必填，对象空间 id（ontology_space 主键），空间必须存在。
            - displayName：必填，空间显示名称。
            - description：可选，空间描述；本接口为整体覆盖式更新，不传会清空原描述。
            - iconUrl：可选，空间图标 URL；不传会清空原图标。
            注意：apiName 不可修改；未传的可选字段会被置空，修改前建议先用 queryOntologySpace 查出当前值再整体回填。
            成功返回中文结果说明。
            """)
    public String updateOntologySpace(
            @ToolParam(description = "对象空间 id（ontology_space 主键）") Integer spaceId,
            @ToolParam(description = "空间显示名称") String displayName,
            @ToolParam(description = "空间描述，不传则清空", required = false) String description,
            @ToolParam(description = "空间图标 URL，不传则清空", required = false) String iconUrl) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        requireText(displayName, "displayName");
        OntologySpaceUpdateParam param = new OntologySpaceUpdateParam();
        param.setSpaceId(spaceId);
        param.setDisplayName(displayName);
        param.setDescription(description);
        param.setIconUrl(iconUrl);
        ontologySpaceService.updateSpace(param);
        return "对象空间更新成功：" + displayName + "（spaceId=" + spaceId + "）";
    }

    @Tool(description = """
            查询对象空间资源统计（对应 REST 端点 GET /space/statistic）。
            当用户询问"某空间有多少对象/关系/属性/函数/行为/实例"，或删除空间前需要评估影响面时使用。
            参数 spaceId：必填，对象空间 id（ontology_space 主键），空间必须存在，否则报错。
            返回 OntologySpaceStatisticVO：空间内对象数、关系数、函数算子数、行为数（行为树不计入）等核心资源数量统计。
            """)
    public OntologySpaceStatisticVO getOntologySpaceStatistic(
            @ToolParam(description = "对象空间 id（ontology_space 主键）") Integer spaceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        return ontologySpaceService.getStatistic(spaceId);
    }

    @Tool(description = """
            删除对象空间（对应 REST 端点 DELETE /space/{spaceId}）。
            当用户明确要求删除某个对象空间时使用。
            参数 spaceId：必填，对象空间 id（ontology_space 主键），空间必须存在。
            前置条件：空间下必须没有任何对象，否则报错"该空间下存在对象，不能删除"（需先删除空间内全部对象）。
            副作用：级联删除空间的对象分类树、全部关系及关系分类。
            警告：本操作不可逆，删除后空间及其分类、关系无法恢复，调用前务必向用户确认；
            如需保留定义请先用 exportOntologySpace 导出备份。成功返回中文结果说明。
            """)
    public String deleteOntologySpace(
            @ToolParam(description = "对象空间 id（ontology_space 主键）") Integer spaceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        ontologySpaceService.deleteSpace(spaceId);
        return "对象空间删除成功：spaceId=" + spaceId;
    }

    private void requireText(String value, String paramName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
    }

    private <T> T parseJson(String json, Class<T> type, String paramName) {
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

    /**
     * 复刻 REST 端点对画布请求体的 @Valid 级联必填校验（MCP 不走 Bean 校验）。
     */
    private void validateCanvasParam(OntologySpaceCanvasCreateParam param) {
        if (param.getOntologies() != null) {
            for (var ontology : param.getOntologies()) {
                requireCanvasText(ontology.getDisplayName(), "对象名称不能为空（displayName）");
                requireCanvasText(ontology.getApiName(), "对象 apiName 不能为空（apiName）");
                requireApiNamePattern(ontology.getApiName(), "对象");
                if (ontology.getProperties() != null) {
                    for (var property : ontology.getProperties()) {
                        requireCanvasText(property.getDisplayName(), "属性名称不能为空（displayName）");
                        requireCanvasText(property.getApiName(), "属性 apiName 不能为空（apiName）");
                        requireApiNamePattern(property.getApiName(), "属性");
                    }
                }
            }
        }
        if (param.getLinks() != null) {
            for (var link : param.getLinks()) {
                requireCanvasText(link.getName(), "关系名称不能为空（name）");
                requireCanvasText(link.getFromOntologyApiName(), "关系起点对象不能为空（fromOntologyApiName）");
                requireCanvasText(link.getToOntologyApiName(), "关系终点对象不能为空（toOntologyApiName）");
            }
        }
    }

    private void requireCanvasText(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireApiNamePattern(String apiName, String kind) {
        if (!apiName.matches(API_NAME_PATTERN)) {
            throw new IllegalArgumentException(kind + " apiName 格式不合法（apiName）：需以字母、下划线或 $ 开头，"
                    + "只能包含字母、数字、下划线、$，长度不超过 63，实际传入：" + apiName);
        }
    }

    /**
     * 复刻 REST 端点对子空间请求体的 @Valid 级联必填校验（MCP 不走 Bean 校验）。
     */
    private void validateSubspaceParam(OntologySubspaceCreateParam param) {
        if (param.getParentSpaceId() == null) {
            throw new IllegalArgumentException("parentSpaceId 不能为空");
        }
        if (ontologySpaceService.getById(param.getParentSpaceId()) == null) {
            throw new IllegalArgumentException("无效的对象空间：" + param.getParentSpaceId());
        }
        requireText(param.getDisplayName(), "displayName");
        requireText(param.getApiName(), "apiName");
        if (CollectionUtils.isEmpty(param.getSelectedOntologies())) {
            throw new IllegalArgumentException("selectedOntologies 不能为空");
        }
        for (var ontology : param.getSelectedOntologies()) {
            requireText(ontology.getSourceOntologyUniqueIdentifier(), "sourceOntologyUniqueIdentifier");
            if (CollectionUtils.isEmpty(ontology.getSelectedProperties())) {
                throw new IllegalArgumentException("selectedProperties 不能为空（对象 "
                        + ontology.getSourceOntologyUniqueIdentifier() + "）");
            }
            for (var property : ontology.getSelectedProperties()) {
                requireText(property.getSourcePropertyUniqueIdentifier(), "sourcePropertyUniqueIdentifier");
                var filter = property.getFilter();
                if (filter != null) {
                    if (filter.getOp() == null) {
                        throw new IllegalArgumentException("筛选条件 op 不能为空（属性 "
                                + property.getSourcePropertyUniqueIdentifier() + "）");
                    }
                    if (filter.getDataType() == null) {
                        throw new IllegalArgumentException("筛选条件 dataType 不能为空（属性 "
                                + property.getSourcePropertyUniqueIdentifier() + "）");
                    }
                }
            }
        }
        if (param.getSelectedLinks() != null) {
            for (var link : param.getSelectedLinks()) {
                requireText(link.getSourceLinkUniqueIdentifier(), "sourceLinkUniqueIdentifier");
            }
        }
    }

    /**
     * MCP 无文件上传通道，把 JSON 字符串包装为内存 MultipartFile，复用既有文件导入 Service。
     */
    private MultipartFile toMultipartFile(String jsonContent) {
        return new JsonMultipartFile(jsonContent.getBytes(StandardCharsets.UTF_8));
    }

    private static final class JsonMultipartFile implements MultipartFile {

        private final byte[] bytes;

        private JsonMultipartFile(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "import.json";
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), bytes);
        }
    }
}