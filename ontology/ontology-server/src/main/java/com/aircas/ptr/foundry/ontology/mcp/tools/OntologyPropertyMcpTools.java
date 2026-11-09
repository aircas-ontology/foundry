package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.PropertyDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.po.PropertyCategory;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
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
            为对象创建新属性。
            当用户需要给对象添加属性、定义对象的字段结构时使用。
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
            @ToolParam(description = "元数据（JSON 格式），可为空", required = false) JsonNode metadata) {
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
        param.setMetadata(metadata);
        // 如果有数据源配置，解析 JSON
        if (StringUtils.isNotEmpty(datasourceJson)) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                PropertyDatasourceParam datasource = mapper.readValue(datasourceJson, PropertyDatasourceParam.class);
                param.setDatasource(datasource);
            } catch (Exception e) {
                throw new IllegalArgumentException("数据源配置 JSON 格式错误: " + e.getMessage());
            }
        }
        ontologyPropertyService.createProperty(param);
    }
}
