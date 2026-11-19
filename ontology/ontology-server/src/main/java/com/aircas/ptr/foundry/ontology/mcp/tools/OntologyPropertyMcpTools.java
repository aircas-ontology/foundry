package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.MetadataValueTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.CategoryNode;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyVisibilityUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyCategoryCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyCategoryDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyCategoryUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyMetadataSchemaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyMetadataSchemaDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyMetadataSchemaUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.po.PropertyCategory;
import com.aircas.ptr.foundry.ontology.model.vo.AutoBindDatasourceResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVisibilityVO;
import com.aircas.ptr.foundry.ontology.model.vo.PropertyCategoryVO;
import com.aircas.ptr.foundry.ontology.model.vo.PropertyMetadataSchemaVO;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对象属性 MCP 工具。
 *
 * <p>提供空间+对象+属性名称的组合精确查询，由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyPropertyMcpTools implements McpTools {

    private final OntologySpaceService ontologySpaceService;
    private final OntologyMetaService ontologyMetaService;
    private final OntologyPropertyService ontologyPropertyService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            按空间名称 + 对象名称 + 属性名称精确查询对象属性。
            当用户已知空间、对象和属性名称，需要查找属性的详细信息时使用。
            先按空间名定位空间，再按对象名定位对象，最后按属性名过滤（可选）。
            属性名同时匹配 apiName 和 displayName（OR 关系）；不传属性名时返回该对象下的全部属性。
            无匹配时返回空列表。
            """)
    public List<OntologyPropertyInfoVO> queryProperties(
            @ToolParam(description = "空间名称（apiName 或 displayName）") String spaceName,
            @ToolParam(description = "对象名称（apiName 或 displayName）") String objectName,
            @ToolParam(description = "属性名称（apiName 或 displayName），可为空；为空时返回该对象下全部属性", required = false) String propertyName) {

        if (StringUtils.isEmpty(spaceName) || StringUtils.isEmpty(objectName)) {
            return Collections.emptyList();
        }

        var spaces = ontologySpaceService.list(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, spaceName)
                .or().eq(OntologySpace::getDisplayName, spaceName));
        if (CollectionUtils.isEmpty(spaces)) {
            return Collections.emptyList();
        }
        var spaceIds = spaces.stream().map(OntologySpace::getId).collect(Collectors.toList());

        var metas = ontologyMetaService.list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .in(OntologyMeta::getOntologySpaceId, spaceIds)
                .and(w -> w.eq(OntologyMeta::getApiName, objectName)
                        .or().eq(OntologyMeta::getDisplayName, objectName)));
        if (CollectionUtils.isEmpty(metas)) {
            return Collections.emptyList();
        }
        var ontologyUids = metas.stream().map(OntologyMeta::getUniqueIdentifier).collect(Collectors.toList());

        var queryWrapper = new LambdaQueryWrapper<OntologyProperty>()
                .eq(OntologyProperty::getStatus, Status.ENABLE.getValue())
                .in(OntologyProperty::getOntologyUniqueIdentifier, ontologyUids);
        if (StringUtils.isNotEmpty(propertyName)) {
            queryWrapper.and(w -> w.eq(OntologyProperty::getApiName, propertyName)
                    .or().eq(OntologyProperty::getDisplayName, propertyName));
        }

        var properties = ontologyPropertyService.list(queryWrapper);
        if (CollectionUtils.isEmpty(properties)) {
            return Collections.emptyList();
        }
        return properties.stream()
                .map(DataConverter::convertToPropertyInfoVO)
                .collect(Collectors.toList());
    }

    @Tool(description = """
            根据对象唯一标识（ontologyUniqueIdentifier）查询该对象下的属性分类列表（扁平结构，不分页）。
            当用户需要查看对象下所有属性分类的完整列表时使用。
            返回分类 id、名称、父分类 id 等完整信息。
            """)
    public List<PropertyCategory> listPropertyCategories(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        return ontologyPropertyService.getPropertyCategoryList(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            为对象创建单个新属性（写操作：新增属性元数据记录）。
            当用户需要给对象添加一个字段、定义对象的字段结构时使用；一次建多个字段请用 batchCreateProperties（整批同事务校验）。
            参数说明：
            - ontologyUniqueIdentifier：对象唯一标识，必填，可先用 searchOntologyMeta / listOntologyMetaBySpace 查到；
            - displayName：属性显示名称，必填；
            - apiName：代码中使用的属性名，必填，格式 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$，不能与该对象已有属性重复；
            - dataType：数据类型，必填，取值 Bool、Int、Long、Float、Short、Byte、Double、Decimal、String、Date、Array、Map、Vector、Timestamp、MediaReference、TimeSeries、Attachment、Geohash、Geoshape、Cipher、Ontology；
            - isPrimaryKey / isTitleKey：必填布尔值；一个对象最多一个主键、一个标题键，主键必须绑定数据源（datasourceJson）；
            - storageGroup：存储分组名，必填，格式同 apiName，且不能与对象 apiName 相同；main 表示主表分组；
            - description / tag / defaultValue：可选，描述、自定义标签、默认值；
            - categoryId：可选，必须是该对象下已存在的属性分类 id（listPropertyCategories 可查）；
            - datasourceJson：可选，数据源绑定 JSON 对象字符串 {datasourceId, datasourceSchema, datasourceColumnName}，主键属性必传；
            - metadataJson：可选，属性元数据字符串（约定为 JSON 对象字符串）；能解析为 JSON 对象时须符合该对象的元数据 schema（getMetadataSchema 可查），否则原样存储。
            只建元数据，不建物理列；数据湖物理表/列由 autoBindDatasource 统一生成。
            校验失败（重名、主键/标题键冲突、分类不存在等）抛业务异常，不落库。
            """)
    public void createProperty(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "属性显示名称") String displayName,
            @ToolParam(description = "属性 API 名称（代码中使用，格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$）") String apiName,
            @ToolParam(description = "数据类型，可选值：Bool、Int、Long、Float、Short、Byte、Double、Decimal、String、Date、Array、Map、Vector、Timestamp、MediaReference、TimeSeries、Attachment、Geohash、Geoshape、Cipher、Ontology") String dataType,
            @ToolParam(description = "是否为主键") Boolean isPrimaryKey,
            @ToolParam(description = "是否为标题键") Boolean isTitleKey,
            @ToolParam(description = "存储分组名称（格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$）") String storageGroup,
            @ToolParam(description = "属性描述，可为空", required = false) String description,
            @ToolParam(description = "属性标签，可为空", required = false) String tag,
            @ToolParam(description = "默认值，可为空", required = false) String defaultValue,
            @ToolParam(description = "属性分类 ID，可为空", required = false) Integer categoryId,
            @ToolParam(description = "数据源配置（JSON 格式），可为空", required = false) String datasourceJson,
            @ToolParam(description = "元数据字符串（约定为 JSON 对象字符串），可为空", required = false) String metadataJson) {
        OntologyPropertyCreateParam param = new OntologyPropertyCreateParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setDisplayName(displayName);
        param.setApiName(apiName);
        param.setDataType(OntologyDataTypeEnum.valueOf(dataType));
        param.setIsPrimaryKey(isPrimaryKey);
        param.setIsTitleKey(isTitleKey);
        param.setStorageGroup(storageGroup);
        param.setDescription(description);
        param.setTag(tag);
        param.setDefaultValue(defaultValue);
        param.setCategoryId(categoryId);
        param.setMetadata(metadataJson);
        // 如果有数据源配置，解析 JSON
        if (StringUtils.isNotEmpty(datasourceJson)) {
            try {
                PropertyDatasourceParam datasource = jsonMapper.readValue(datasourceJson, PropertyDatasourceParam.class);
                param.setDatasource(datasource);
            } catch (Exception e) {
                throw new IllegalArgumentException("数据源配置 JSON 格式错误: " + e.getMessage());
            }
        }
        ontologyPropertyService.createProperty(param);
    }

    @Tool(description = """
            为对象批量创建属性（对应 REST 端点 POST /property/batch）。
            当需要一次性给对象定义多个字段结构时使用，整批在同一事务内校验与落库，比逐个调用 createProperty 更高效。
            propertiesJson 为 JSON 数组字符串，每个元素是一个属性创建对象，字段如下：
            - ontologyIdentifier：必填，对象唯一标识，所有元素必须属于同一个对象；
            - displayName：必填，属性显示名称；
            - apiName：必填，代码中使用的属性名，格式 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$，不能与对象已有属性或本批其他元素重复；
            - dataType：必填，取值 Bool、Int、Long、Float、Short、Byte、Double、Decimal、String、Date、Array、Map、Vector、Timestamp、MediaReference、TimeSeries、Attachment、Geohash、Geoshape、Cipher、Ontology；
            - isPrimaryKey / isTitleKey：必填布尔值；一个对象最多一个主键、一个标题键，主键必须绑定数据源；
            - storageGroup：必填，存储分组名，格式同 apiName，且不能与对象 apiName 相同；
            - description / tag / defaultValue：可选字符串，描述、自定义标签、默认值；
            - categoryId：可选整数，必须是该对象下已存在的属性分类 id；
            - datasource：可选对象 {"schemaName":"public","datasourceId":"表名","datasourceColumnName":"列名"}，同一数据源列不能绑定到多个属性；
            - metadata：可选，JSON 对象字符串（键=schema节点名称逐层嵌套）或 JSON 对象；能解析为对象时须符合对象已定义的元数据 schema，对象未定义 schema 时会被拒绝；解析不了的字符串原样存储。
            副作用：写入属性表并重建对象实体节点；任一元素校验失败则整批失败、不落库。成功返回中文结果说明。
            """)
    public String batchCreateProperties(
            @ToolParam(description = "属性创建对象 JSON 数组字符串，元素字段见工具描述") String propertiesJson) {
        requireText(propertiesJson, "propertiesJson");
        List<OntologyPropertyCreateParam> properties = parseJson(propertiesJson,
                new TypeReference<List<OntologyPropertyCreateParam>>() {
                }, "propertiesJson");
        if (CollectionUtils.isEmpty(properties)) {
            throw new IllegalArgumentException("propertiesJson 不能为空数组");
        }
        ontologyPropertyService.batchCreateProperties(properties);
        return "批量创建属性成功，共 " + properties.size() + " 个属性";
    }

    @Tool(description = """
            更新单个对象属性（对应 REST 端点 PUT /property）。
            当需要修改属性的显示名、类型、主键/标题键、存储分组、默认值、分类、数据源绑定或元数据时使用。
            注意：本操作是整体覆盖式更新，未传的可选字段（description、defaultValue、categoryId、datasourceJson、metadataJson）会被置空，
            修改前建议先用 getPropertyDetailById 查出当前值再整体回填。apiName 不可修改。
            校验限制：属性必须存在；storageGroup 不能与对象 apiName 相同；一个对象最多一个主键、一个标题键，主键必须绑定数据源；
            同一数据源列不能绑定到多个属性；categoryId 必须是该对象下已有分类；metadata 能解析为 JSON 对象时须符合对象元数据 schema（未定义 schema 时不能设置），否则原样存储。
            副作用：更新属性表并重建对象实体节点。成功返回中文结果说明。
            """)
    public String updateProperty(
            @ToolParam(description = "属性唯一标识（uniqueIdentifier）") String uniqueIdentifier,
            @ToolParam(description = "属性显示名称") String displayName,
            @ToolParam(description = "数据类型，可选值：Bool、Int、Long、Float、Short、Byte、Double、Decimal、String、Date、Array、Map、Vector、Timestamp、MediaReference、TimeSeries、Attachment、Geohash、Geoshape、Cipher、Ontology") String dataType,
            @ToolParam(description = "是否为主键（一个对象最多一个主键，主键必须绑定数据源）") Boolean isPrimaryKey,
            @ToolParam(description = "是否为标题键（一个对象最多一个标题键）") Boolean isTitleKey,
            @ToolParam(description = "存储分组名称（格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$，不能与对象 apiName 相同）") String storageGroup,
            @ToolParam(description = "属性描述，不传则清空", required = false) String description,
            @ToolParam(description = "属性默认值，不传则清空", required = false) String defaultValue,
            @ToolParam(description = "属性分类 id（该对象下已有分类），不传则清空分类", required = false) Integer categoryId,
            @ToolParam(description = "数据源绑定 JSON 对象字符串 {\"schemaName\":\"public\",\"datasourceId\":\"表名\",\"datasourceColumnName\":\"列名\"}，不传则解绑数据源", required = false) String datasourceJson,
            @ToolParam(description = "元数据字符串（约定为 JSON 对象字符串，须符合对象元数据 schema），不传则清空元数据", required = false) String metadataJson) {
        requireText(uniqueIdentifier, "uniqueIdentifier");
        requireText(displayName, "displayName");
        requireText(dataType, "dataType");
        requireText(storageGroup, "storageGroup");
        if (isPrimaryKey == null || isTitleKey == null) {
            throw new IllegalArgumentException("isPrimaryKey 和 isTitleKey 不能为空");
        }
        OntologyPropertyUpdateParam param = new OntologyPropertyUpdateParam();
        param.setUniqueIdentifier(uniqueIdentifier);
        param.setDisplayName(displayName);
        param.setDataType(parseDataType(dataType));
        param.setIsPrimaryKey(isPrimaryKey);
        param.setIsTitleKey(isTitleKey);
        param.setStorageGroup(storageGroup);
        param.setDescription(description);
        param.setDefaultValue(defaultValue);
        param.setCategoryId(categoryId);
        param.setDatasource(parseDatasource(datasourceJson));
        param.setMetadata(metadataJson);
        ontologyPropertyService.updateProperty(param);
        return "属性更新成功：" + displayName + "（uniqueIdentifier=" + uniqueIdentifier + "）";
    }

    @Tool(description = """
            批量更新对象属性（对应 REST 端点 PUT /property/batch）。
            当需要一次性修改同一对象下多个属性时使用，整批在同一事务内校验与更新。
            propertiesJson 为 JSON 数组字符串，每个元素是一个属性更新对象，字段如下：
            - uniqueIdentifier：必填，属性唯一标识，所有元素必须属于同一个对象；
            - displayName：必填，属性显示名称；
            - dataType：必填，取值 Bool、Int、Long、Float、Short、Byte、Double、Decimal、String、Date、Array、Map、Vector、Timestamp、MediaReference、TimeSeries、Attachment、Geohash、Geoshape、Cipher、Ontology；
            - isPrimaryKey / isTitleKey：必填布尔值，整批加上对象其余属性合计最多一个主键、一个标题键，主键必须绑定数据源；
            - storageGroup：必填，格式 ^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$，不能与对象 apiName 相同；
            - description / defaultValue：可选字符串；categoryId：可选整数（该对象下已有分类 id）；
            - datasource：可选对象 {"schemaName":"public","datasourceId":"表名","datasourceColumnName":"列名"}；
            - metadata：可选，JSON 对象字符串（键=schema节点名称逐层嵌套）或 JSON 对象；能解析为对象时须符合对象元数据 schema，否则原样存储。
            注意：每个元素是整体覆盖式更新，未传的可选字段会被置空；apiName 不可修改。
            副作用：批量更新属性表并重建对象实体节点；任一元素校验失败则整批失败。成功返回中文结果说明。
            """)
    public String batchUpdateProperties(
            @ToolParam(description = "属性更新对象 JSON 数组字符串，元素字段见工具描述") String propertiesJson) {
        requireText(propertiesJson, "propertiesJson");
        List<OntologyPropertyUpdateParam> params = parseJson(propertiesJson,
                new TypeReference<List<OntologyPropertyUpdateParam>>() {
                }, "propertiesJson");
        if (CollectionUtils.isEmpty(params)) {
            throw new IllegalArgumentException("propertiesJson 不能为空数组");
        }
        ontologyPropertyService.batchUpdateProperties(params);
        return "批量更新属性成功，共 " + params.size() + " 个属性";
    }

    @Tool(description = """
            删除对象属性（对应 REST 端点 DELETE /property/{propertyUniqueIdentifier}）。
            当需要从对象结构中移除某个字段时使用。
            限制：属性必须存在；主键属性和标题键属性不能删除；被对象行为（action 入参映射）引用的属性不能删除，需先解除行为引用。
            副作用：从属性表物理删除该属性。成功返回中文结果说明。
            """)
    public String deleteProperty(
            @ToolParam(description = "属性唯一标识（propertyUniqueIdentifier）") String propertyUniqueIdentifier) {
        requireText(propertyUniqueIdentifier, "propertyUniqueIdentifier");
        ontologyPropertyService.deleteProperty(propertyUniqueIdentifier);
        return "属性删除成功，propertyUniqueIdentifier=" + propertyUniqueIdentifier;
    }

    @Tool(description = """
            根据对象唯一标识查询该对象下的属性列表（展示用，对应 REST 端点 GET /property/info）。
            当需要浏览对象有哪些字段时使用；需要数据源绑定等编辑用完整信息时改用 getPropertyDetailByOntologyId。
            返回 OntologyPropertyInfoVO 列表：uniqueIdentifier、apiName、displayName、description、propertyType（数据类型）、
            isPrimaryKey、isTitleKey、defaultValue、storageGroup、categoryId、metadata、ontologyUniqueIdentifier。
            对象不存在或没有属性时返回空列表。
            """)
    public List<OntologyPropertyInfoVO> getPropertyInfoByOntologyId(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier)) {
            return Collections.emptyList();
        }
        return ontologyPropertyService.getPropertyInfoByOntologyId(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            根据对象唯一标识查询该对象下的属性详细列表（编辑用，对应 REST 端点 GET /property/detail）。
            当需要修改属性前查看完整配置（含数据源绑定 datasourceId、datasourceColumnName、datasourceDescription）时使用。
            返回 OntologyPropertyDetailVO 列表：在展示字段（uniqueIdentifier、apiName、displayName、propertyType、isPrimaryKey、
            isTitleKey、defaultValue、storageGroup、categoryId、metadata 等）基础上附带数据源绑定信息。
            对象不存在或没有属性时返回空列表。
            """)
    public List<OntologyPropertyDetailVO> getPropertyDetailByOntologyId(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier)) {
            return Collections.emptyList();
        }
        return ontologyPropertyService.getPropertyDetailByOntologyId(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            根据属性唯一标识查询单个属性的详细信息（对应 REST 端点 GET /property?uniqueIdentifier=）。
            当已知属性 uniqueIdentifier、需要查看或修改前回填其完整配置时使用。
            返回 OntologyPropertyDetailVO：uniqueIdentifier、apiName、displayName、description、propertyType、isPrimaryKey、
            isTitleKey、defaultValue、storageGroup、categoryId、metadata、datasourceId、datasourceColumnName、datasourceDescription 等。
            属性不存在时返回 null。
            """)
    public OntologyPropertyDetailVO getPropertyDetailById(
            @ToolParam(description = "属性唯一标识（uniqueIdentifier）") String uniqueIdentifier) {
        requireText(uniqueIdentifier, "uniqueIdentifier");
        return ontologyPropertyService.getPropertyDetailById(uniqueIdentifier);
    }

    @Tool(description = """
            根据属性唯一标识列表批量查询属性详细信息（对应 REST 端点 POST /property/query_list）。
            当已知多个属性 uniqueIdentifier、需要一次性取回完整配置时使用。
            uniqueIdentifiersJson 为 JSON 字符串数组，如 ["uid1","uid2"]。
            返回 OntologyPropertyDetailVO 列表（字段同 getPropertyDetailById）；不存在的 id 会被忽略，全部不存在时返回空列表。
            """)
    public List<OntologyPropertyDetailVO> getPropertiesDetailById(
            @ToolParam(description = "属性唯一标识 JSON 字符串数组，如 [\"uid1\",\"uid2\"]") String uniqueIdentifiersJson) {
        requireText(uniqueIdentifiersJson, "uniqueIdentifiersJson");
        List<String> uniqueIdentifiers = parseJson(uniqueIdentifiersJson,
                new TypeReference<List<String>>() {
                }, "uniqueIdentifiersJson");
        if (CollectionUtils.isEmpty(uniqueIdentifiers)) {
            return Collections.emptyList();
        }
        return ontologyPropertyService.getPropertiesDetailById(uniqueIdentifiers);
    }

    @Tool(description = """
            保存对象属性可见性（对应 REST 端点 POST /property/visibility）。
            当需要控制对象属性在实体数据展示中是否可见时使用。
            propertyVisibilityJson 为 JSON 数组字符串，元素形如 {"propertyApiName":"属性apiName","visibility":1}，
            visibility 取值：0 不可见、1 可见。只更新 apiName 能匹配到该对象属性的元素，匹配不到的忽略。
            限制：主键和标题键不能设置为不可见（visibility=0），否则整体报错。
            副作用：批量更新属性的可见性字段。成功返回中文结果说明。
            """)
    public String updatePropertyVisibility(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "属性可见性 JSON 数组字符串，元素形如 {\"propertyApiName\":\"name\",\"visibility\":1}，visibility：0 不可见 1 可见") String propertyVisibilityJson) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(propertyVisibilityJson, "propertyVisibilityJson");
        List<OntologyPropertyVisibilityUpdateParam.PropertyVisibility> visibilityList = parseJson(propertyVisibilityJson,
                new TypeReference<List<OntologyPropertyVisibilityUpdateParam.PropertyVisibility>>() {
                }, "propertyVisibilityJson");
        if (CollectionUtils.isEmpty(visibilityList)) {
            throw new IllegalArgumentException("propertyVisibilityJson 不能为空数组");
        }
        OntologyPropertyVisibilityUpdateParam param = new OntologyPropertyVisibilityUpdateParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setPropertyVisibility(visibilityList);
        ontologyPropertyService.updatePropertyVisibility(param);
        return "属性可见性保存成功，共 " + visibilityList.size() + " 条设置";
    }

    @Tool(description = """
            查询对象属性可见性（对应 REST 端点 GET /property/visibility）。
            当需要查看对象各属性当前是否可见时使用。
            返回 OntologyPropertyVisibilityVO 列表：propertyApiName、propertyDisplayName、visibility（0 不可见 1 可见）。
            注意：只返回与主键属性同一数据源的属性；对象没有主键、或主键未绑定数据源列时返回空列表。
            """)
    public List<OntologyPropertyVisibilityVO> getPropertyVisibility(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier)) {
            return Collections.emptyList();
        }
        return ontologyPropertyService.getPropertyVisibility(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            对象属性自动关联数据源（对应 REST 端点 POST /property/auto_bind_datasource）。
            当对象属性尚未绑定数据源、希望按属性 apiName 与实体表列名自动匹配绑定时使用。
            按存储分组定位实体表，列类型与属性类型冲突且实体表已有实例数据的属性不绑定、不改动该列；
            主键 id 列冲突时整单失败。
            返回 AutoBindDatasourceResultVO：boundCount（本次新关联属性数）、totalBoundCount（对象已关联数据源属性总数）、
            blockedItems（未关联明细：propertyApiName、propertyDisplayName、storageGroup、tableName、columnName、
            actualColumnType、expectedColumnType、reason）、message（可直接展示的结果提示）。
            副作用：为匹配成功的属性写入数据源绑定。
            """)
    public AutoBindDatasourceResultVO autoBindDatasource(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        return ontologyPropertyService.autoBindDatasource(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            通知数据层构建对象的数据管道（对应 REST 端点 POST /property/notify_build_pipeline）。
            当对象属性已关联数据源、需要触发数据组织层按绑定关系构建/刷新数据管道时使用，通常在 autoBindDatasource 之后调用。
            前置条件：对象必须存在 apiName 为 id 的主键属性且其存储分组为 main，否则报错。
            对象没有属性、或所有属性都未关联数据源时静默不处理（仍返回成功说明）。
            副作用：向数据组织层发送管道构建通知。成功返回中文结果说明。
            """)
    public String notifyBuildPipeline(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        ontologyPropertyService.notifyBuildPipeline(ontologyUniqueIdentifier);
        return "已通知数据层构建数据管道，ontologyUniqueIdentifier=" + ontologyUniqueIdentifier;
    }

    @Tool(description = """
            查询对象的属性存储分组列表（对应 REST 端点 GET /property/storage_group）。
            当需要知道对象下属性分布在哪些存储分组（对应实体表）时使用，例如新增属性时选择 storageGroup。
            返回去重后的存储分组名称字符串列表；对象没有属性时返回空列表。
            """)
    public List<String> getStorageGroup(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier)) {
            return Collections.emptyList();
        }
        return ontologyPropertyService.getStorageGroup(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            创建对象的属性分类树节点（对应 REST 端点 POST /property/category）。
            当需要为对象属性建立分类体系（如"平台属性/载荷属性"）时使用，可携带子分类一次建出子树。
            parentId 传 0 表示创建根节点（仅当该对象还没有属性分类时允许）；传已有分类 id 则在其下创建子分类。
            childrenJson 为可选的子分类 JSON 数组，元素形如 {"name":"子分类","children":[...]}，可递归嵌套。
            副作用：按层级批量写入属性分类表。成功返回中文结果说明。
            """)
    public String createPropertyCategory(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "父分类 id；0 表示创建根节点（仅当对象还没有属性分类时允许）") Integer parentId,
            @ToolParam(description = "属性分类名称") String name,
            @ToolParam(description = "子分类 JSON 数组字符串，元素形如 {\"name\":\"子分类\",\"children\":[...]}，可为空", required = false) String childrenJson) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(name, "name");
        if (parentId == null) {
            throw new IllegalArgumentException("parentId 不能为空，0 表示创建根节点");
        }
        PropertyCategoryCreateParam param = new PropertyCategoryCreateParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setParentId(parentId);
        param.setName(name);
        if (StringUtils.isNotBlank(childrenJson)) {
            param.setChildren(parseJson(childrenJson, new TypeReference<List<CategoryNode>>() {
            }, "childrenJson"));
        }
        ontologyPropertyService.createCategory(param);
        return "属性分类创建成功：" + name;
    }

    @Tool(description = """
            修改对象属性分类的名称或父节点（对应 REST 端点 PUT /property/category）。
            当需要重命名属性分类、或把分类移动到其他分类下时使用。
            parentId 不传表示不移动；不能移动到自身或自身的下级节点下（会成环），也不能传 0（属性分类树只支持一个根节点）。
            副作用：更新分类名称，并同步重写该分类及其全部子孙节点的路径。成功返回中文结果说明。
            """)
    public String updatePropertyCategory(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "要更新的属性分类 id") Integer categoryId,
            @ToolParam(description = "新的分类名称") String name,
            @ToolParam(description = "新的父分类 id，不传表示不移动；不能是自身或自身的下级节点") Integer parentId) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireText(name, "name");
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId 不能为空");
        }
        PropertyCategoryUpdateParam param = new PropertyCategoryUpdateParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setCategoryId(categoryId);
        param.setName(name);
        param.setParentId(parentId);
        ontologyPropertyService.updateCategory(param);
        return "属性分类更新成功：" + name + "（categoryId=" + categoryId + "）";
    }

    @Tool(description = """
            删除对象属性分类树节点（对应 REST 端点 DELETE /property/category，body 参数已平铺）。
            当需要移除属性分类时使用，连同其全部子孙分类一起删除。
            限制：该节点或其子孙分类下仍关联属性时不允许删除，需先把属性移到其他分类或清空分类。
            副作用：物理删除该分类及其子孙分类。成功返回中文结果说明。
            """)
    public String deletePropertyCategory(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "要删除的属性分类 id") Integer categoryId) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId 不能为空");
        }
        PropertyCategoryDeleteParam param = new PropertyCategoryDeleteParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setCategoryId(categoryId);
        ontologyPropertyService.deleteCategory(param);
        return "属性分类删除成功，categoryId=" + categoryId;
    }

    @Tool(description = """
            查询对象的属性分类体系树（对应 REST 端点 GET /property/category）。
            当需要查看属性分类的完整树形结构及各分类下的属性时使用；只需扁平分类列表时用 listPropertyCategories。
            返回 PropertyCategoryVO 树：categoryId、name、propertyInfos（该分类下的属性列表，未分类属性挂在根节点）、children（子分类）。
            对象还没有属性分类时返回 null。
            """)
    public PropertyCategoryVO getPropertyCategoryTree(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier)) {
            return null;
        }
        return ontologyPropertyService.getCategory(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            创建对象的属性元数据 schema 节点（对应 REST 端点 POST /property/metadata_schema）。
            元数据 schema 定义属性 metadata 的取值结构，可携带子节点一次建出子树。
            parentId 传 0 表示创建根节点（仅当该对象还没有元数据 schema 时允许）；传已有 schema 节点 id 则在其下创建子节点。
            valueType 为取值类型：INPUT 输入值（自由填写）/ ENUM 枚举值 / RANGE 范围值；不传默认 INPUT。
            value 为取值内容字符串：ENUM 填逗号分隔候选项如 "高,中,低"（前端下拉提示，赋值时不强制校验成员），
            RANGE 填 "最小值,最大值" 如 "0,100"，INPUT 可空。
            childrenJson 为可选的子节点 JSON 数组，元素形如 {"name":"子节点","valueType":"ENUM","value":"a,b","children":[...]}，可递归嵌套。
            业务锁：对象下只要有任一属性已赋值元数据（metadata 非空），schema 的新增会被拒绝，需先清空属性元数据。
            副作用：按层级批量写入元数据 schema 表。成功返回中文结果说明。
            """)
    public String createMetadataSchema(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "父节点 id；0 表示创建根节点（仅当对象还没有元数据 schema 时允许）") Integer parentId,
            @ToolParam(description = "元数据 schema 节点名称") String name,
            @ToolParam(description = "取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值，不传默认 INPUT", required = false) String valueType,
            @ToolParam(description = "取值内容字符串：ENUM 逗号分隔候选项如\"高,中,低\"，RANGE 如\"0,100\"，INPUT 可空", required = false) String value,
            @ToolParam(description = "子节点 JSON 数组字符串，元素形如 {\"name\":\"子节点\",\"valueType\":\"ENUM\",\"value\":\"a,b\",\"children\":[...]}，可为空", required = false) String childrenJson) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireOntologyExists(ontologyUniqueIdentifier);
        requireText(name, "name");
        if (parentId == null) {
            throw new IllegalArgumentException("parentId 不能为空，0 表示创建根节点");
        }
        PropertyMetadataSchemaCreateParam param = new PropertyMetadataSchemaCreateParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setParentId(parentId);
        param.setName(name);
        param.setValueType(parseValueType(valueType));
        param.setValue(value);
        if (StringUtils.isNotBlank(childrenJson)) {
            param.setChildren(parseJson(childrenJson,
                    new TypeReference<List<PropertyMetadataSchemaCreateParam.MetadataSchemaNode>>() {
                    }, "childrenJson"));
        }
        ontologyPropertyService.createMetadataSchema(param);
        return "属性元数据schema创建成功：" + name;
    }

    @Tool(description = """
            修改对象的属性元数据 schema 节点（对应 REST 端点 PUT /property/metadata_schema）。
            支持修改节点名称、取值类型、取值内容，以及通过 parentId 移动节点到新的父级（不支持增删子节点）。
            parentId 可选：不传保持原父级；传了则移动节点，子孙节点路径同步重写。0 表示根节点（仅节点本身已是根时允许）；不能移动到自身或其子孙节点下；目标父节点下已有同名节点会被拒绝。
            valueType 可选 INPUT 输入值 / ENUM 枚举值 / RANGE 范围值：不传保持原类型；传了则连同 value 一起更新（value 不传清空值）。
            value 为取值内容字符串：ENUM 逗号分隔候选项如 "一级,二级,三级"，RANGE 如 "0,100"，INPUT 可空；只传 value 不传 valueType 时仅更新值。
            业务锁：对象下只要有任一属性已赋值元数据（metadata 非空），schema 的修改会被拒绝，需先清空属性元数据。
            副作用：更新节点名称、取值配置与父级，并同步重写其全部子孙节点的路径。成功返回中文结果说明。
            """)
    public String updateMetadataSchema(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "要修改的元数据 schema 节点 id（metadataSchemaId）") Integer metadataSchemaId,
            @ToolParam(description = "新的节点名称") String name,
            @ToolParam(description = "取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值，不传保持原类型", required = false) String valueType,
            @ToolParam(description = "取值内容字符串：ENUM 逗号分隔候选项，RANGE 如\"0,100\"，INPUT 可空", required = false) String value,
            @ToolParam(description = "新父节点 id：不传保持原父级；0 表示根节点（仅节点本身已是根时允许）；不能移动到自身或子孙节点下", required = false) Integer parentId) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireOntologyExists(ontologyUniqueIdentifier);
        requireText(name, "name");
        if (metadataSchemaId == null) {
            throw new IllegalArgumentException("metadataSchemaId 不能为空");
        }
        PropertyMetadataSchemaUpdateParam param = new PropertyMetadataSchemaUpdateParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setMetadataSchemaId(metadataSchemaId);
        param.setName(name);
        param.setValueType(parseValueType(valueType));
        param.setValue(value);
        param.setParentId(parentId);
        ontologyPropertyService.updateMetadataSchema(param);
        return "属性元数据schema更新成功：" + name + "（metadataSchemaId=" + metadataSchemaId + "）";
    }

    @Tool(description = """
            删除对象的属性元数据 schema 节点（对应 REST 端点 DELETE /property/metadata_schema，body 参数已平铺）。
            连同该节点的全部子孙节点一起删除。
            业务锁：对象下只要有任一属性已赋值元数据（metadata 非空），schema 的删除会被拒绝，需先清空属性元数据。
            副作用：物理删除该节点及其子孙节点。成功返回中文结果说明。
            """)
    public String deleteMetadataSchema(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier,
            @ToolParam(description = "要删除的元数据 schema 节点 id（metadataSchemaId）") Integer metadataSchemaId) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireOntologyExists(ontologyUniqueIdentifier);
        if (metadataSchemaId == null) {
            throw new IllegalArgumentException("metadataSchemaId 不能为空");
        }
        PropertyMetadataSchemaDeleteParam param = new PropertyMetadataSchemaDeleteParam();
        param.setOntologyIdentifier(ontologyUniqueIdentifier);
        param.setMetadataSchemaId(metadataSchemaId);
        ontologyPropertyService.deleteMetadataSchema(param);
        return "属性元数据schema删除成功，metadataSchemaId=" + metadataSchemaId;
    }

    @Tool(description = """
            查询对象的属性元数据 schema（对应 REST 端点 GET /property/metadata_schema）。
            当需要知道属性 metadata 应按什么结构赋值时使用（createProperty/updateProperty 的 metadata 须符合此结构）。
            返回 JSON 对象：按 schema 路径嵌套，叶子节点为取值内容字符串（ENUM 是逗号分隔候选项，RANGE 如 "0,100"，无值时为空字符串 ""），
            非叶子节点为嵌套对象。例如 {"信息等级":"高,中,低","来源":{"渠道":""}}。取值类型（INPUT/ENUM/RANGE）用 getMetadataSchemaTree 查。
            对象未定义元数据 schema 时返回 null。需要带节点 id 的树结构时用 getMetadataSchemaTree。
            """)
    public JsonNode getMetadataSchema(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireOntologyExists(ontologyUniqueIdentifier);
        return ontologyPropertyService.getMetadataSchema(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            查询对象的属性元数据 schema 树（对应 REST 端点 GET /property/metadata_schema/tree）。
            当需要修改/删除 schema 节点、先获取节点 id 时使用。
            返回 PropertyMetadataSchemaVO 树：schemaId（节点 id）、name、valueType（INPUT 输入值/ENUM 枚举值/RANGE 范围值）、
            value（取值内容字符串：ENUM 逗号分隔候选项，RANGE 如 "0,100"）、children（子节点）。
            对象未定义元数据 schema 时返回 null。只需取值结构时用 getMetadataSchema。
            """)
    public PropertyMetadataSchemaVO getMetadataSchemaTree(
            @ToolParam(description = "对象唯一标识（ontologyUniqueIdentifier）") String ontologyUniqueIdentifier) {
        requireText(ontologyUniqueIdentifier, "ontologyUniqueIdentifier");
        requireOntologyExists(ontologyUniqueIdentifier);
        return ontologyPropertyService.getMetadataSchemaTree(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            根据属性分类 id 查询属性列表（对应 REST 端点 GET /property/by_category）。
            当需要查看某个属性分类下挂了哪些属性时使用。
            categoryId 不传则查询全部属性（注意：不传时跨所有对象返回全库属性，数据量可能很大，建议尽量传分类 id）。
            返回 OntologyPropertyInfoVO 列表（字段同 getPropertyInfoByOntologyId），按属性 id 升序；无匹配时返回空列表。
            """)
    public List<OntologyPropertyInfoVO> getPropertiesByCategory(
            @ToolParam(description = "属性分类 id，不传则查询全部属性（跨对象）", required = false) Integer categoryId) {
        return ontologyPropertyService.getByCategoryId(categoryId);
    }

    private void requireText(String value, String paramName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
    }

    /** REST 侧靠 @OntologyIdVerify 拦截无效对象 id，MCP 不走 Bean 校验，手动补上 */
    private void requireOntologyExists(String ontologyIdentifier) {
        var meta = ontologyMetaService.getOne(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getUniqueIdentifier, ontologyIdentifier));
        if (meta == null) {
            throw new IllegalArgumentException("对象不存在:" + ontologyIdentifier);
        }
    }

    private <T> T parseJson(String json, TypeReference<T> type, String paramName) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(paramName + " JSON 解析失败：" + e.getMessage());
        }
    }

    private OntologyDataTypeEnum parseDataType(String dataType) {
        try {
            return OntologyDataTypeEnum.valueOf(dataType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的 dataType：" + dataType
                    + "，可选值：Bool、Int、Long、Float、Short、Byte、Double、Decimal、String、Date、Array、Map、Vector、Timestamp、MediaReference、TimeSeries、Attachment、Geohash、Geoshape、Cipher、Ontology");
        }
    }

    private PropertyDatasourceParam parseDatasource(String datasourceJson) {
        if (StringUtils.isBlank(datasourceJson)) {
            return null;
        }
        return parseJson(datasourceJson, new TypeReference<PropertyDatasourceParam>() {
        }, "datasourceJson");
    }

    private MetadataValueTypeEnum parseValueType(String valueType) {
        if (StringUtils.isBlank(valueType)) {
            return null;
        }
        try {
            return MetadataValueTypeEnum.valueOf(valueType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("valueType 不合法：" + valueType + "，可选值 INPUT/ENUM/RANGE");
        }
    }
}
