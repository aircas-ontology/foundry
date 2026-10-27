package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * 本体属性 MCP 工具。
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
            按空间名称 + 对象名称 + 属性名称精确查询本体属性。
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
}
