package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 对象关系 MCP 工具。
 *
 * <p>提供空间+主体对象+客体对象+关系名称的组合精确查询，由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyLinkMcpTools implements McpTools {

    private final OntologySpaceService ontologySpaceService;
    private final OntologyMetaService ontologyMetaService;
    private final OntologyLinkGroupService ontologyLinkGroupService;
    private final OntologyLinkCategoryService ontologyLinkCategoryService;

    @Tool(description = """
            按空间名称 + 主体对象名称 + 客体对象名称 + 关系名称精确查询对象关系。
            当用户已知空间、两端的对象和关系名称，需要查找关系的详细信息时使用。
            先按空间名定位空间，再按对象名定位主体和客体对象，最后查找它们之间的关系。
            关系名同时匹配 name 和 apiName（OR 关系）；不传关系名时返回两端对象之间的全部关系。
            无匹配时返回空列表。
            """)
    public List<OntologyLinkInfoVO> queryLinks(
            @ToolParam(description = "空间名称（apiName 或 displayName）") String spaceName,
            @ToolParam(description = "主体对象名称（apiName 或 displayName），关系的起点") String fromObjectName,
            @ToolParam(description = "客体对象名称（apiName 或 displayName），关系的终点") String toObjectName,
            @ToolParam(description = "关系名称（name 或 apiName），可为空；为空时返回两端对象之间的全部关系", required = false) String relationName) {

        if (StringUtils.isEmpty(spaceName) || StringUtils.isEmpty(fromObjectName) || StringUtils.isEmpty(toObjectName)) {
            return Collections.emptyList();
        }

        var spaces = ontologySpaceService.list(new LambdaQueryWrapper<OntologySpace>()
                .eq(OntologySpace::getApiName, spaceName)
                .or().eq(OntologySpace::getDisplayName, spaceName));
        if (CollectionUtils.isEmpty(spaces)) {
            return Collections.emptyList();
        }
        var spaceIds = spaces.stream().map(OntologySpace::getId).collect(Collectors.toList());

        var fromMetas = ontologyMetaService.list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .in(OntologyMeta::getOntologySpaceId, spaceIds)
                .and(w -> w.eq(OntologyMeta::getApiName, fromObjectName)
                        .or().eq(OntologyMeta::getDisplayName, fromObjectName)));
        if (CollectionUtils.isEmpty(fromMetas)) {
            return Collections.emptyList();
        }
        var fromUids = fromMetas.stream().map(OntologyMeta::getUniqueIdentifier).collect(Collectors.toSet());

        var toMetas = ontologyMetaService.list(new LambdaQueryWrapper<OntologyMeta>()
                .eq(OntologyMeta::getStatus, Status.ENABLE.getValue())
                .in(OntologyMeta::getOntologySpaceId, spaceIds)
                .and(w -> w.eq(OntologyMeta::getApiName, toObjectName)
                        .or().eq(OntologyMeta::getDisplayName, toObjectName)));
        if (CollectionUtils.isEmpty(toMetas)) {
            return Collections.emptyList();
        }
        var toUids = toMetas.stream().map(OntologyMeta::getUniqueIdentifier).collect(Collectors.toSet());

        var queryWrapper = new LambdaQueryWrapper<OntologyLinkGroup>()
                .eq(OntologyLinkGroup::getStatus, Status.ENABLE.getValue())
                .in(OntologyLinkGroup::getOntologyUniqueIdentifierFrom, fromUids)
                .in(OntologyLinkGroup::getOntologyUniqueIdentifierTo, toUids);
        if (StringUtils.isNotEmpty(relationName)) {
            queryWrapper.and(w -> w.eq(OntologyLinkGroup::getName, relationName)
                    .or().eq(OntologyLinkGroup::getApiName, relationName));
        }

        var links = ontologyLinkGroupService.list(queryWrapper);
        if (CollectionUtils.isEmpty(links)) {
            return Collections.emptyList();
        }

        var idList = links.stream()
                .flatMap(l -> Stream.of(l.getOntologyUniqueIdentifierFrom(), l.getOntologyUniqueIdentifierTo()))
                .distinct()
                .collect(Collectors.toList());
        var metaMap = ontologyMetaService.list(new LambdaQueryWrapper<OntologyMeta>()
                        .in(OntologyMeta::getUniqueIdentifier, idList)
                        .eq(OntologyMeta::getStatus, Status.ENABLE.getValue()))
                .stream().collect(Collectors.toMap(OntologyMeta::getUniqueIdentifier, v -> v, (a, b) -> a));

        return links.stream().map(link -> {
            var from = metaMap.get(link.getOntologyUniqueIdentifierFrom());
            var to = metaMap.get(link.getOntologyUniqueIdentifierTo());
            return DataConverter.convert(link, from, to);
        }).collect(Collectors.toList());
    }

    @Tool(description = """
            根据对象空间 id（spaceId）查询该空间下的关系分类列表（扁平结构，不分页）。
            当用户需要查看空间下所有关系分类的完整列表时使用。
            返回分类 id、名称、父分类 id 等完整信息。
            """)
    public List<OntologyLinkCategory> listLinkCategories(
            @ToolParam(description = "对象空间 id（spaceId）") Integer spaceId) {
        return ontologyLinkCategoryService.getCategoryList(spaceId);
    }

    @Tool(description = """
            创建对象之间的关系（Link）。
            当用户需要在两个对象之间建立关联关系时使用。
            关系有方向：从主体对象（from）指向客体对象（to）。
            """)
    public void createLink(
            @ToolParam(description = "关系名称") String name,
            @ToolParam(description = "关系 API 名称（代码中使用）") String apiName,
            @ToolParam(description = "主体对象唯一标识（关系的起点）") String ontologyUniqueIdentifierFrom,
            @ToolParam(description = "客体对象唯一标识（关系的终点）") String ontologyUniqueIdentifierTo,
            @ToolParam(description = "关系类型，可选值：COMPOSITION（组合关系）、RECONNAISSANCE（侦察关系）、STRIKE（打击关系）、COORDINATION（协同关系）、POSSESSION（拥有关系）、ATTRIBUTION（归属关系）、OTHER（其他关系）") String type,
            @ToolParam(description = "关系所属空间 ID") Integer spaceId,
            @ToolParam(description = "关系分类 ID，可为空", required = false) Integer categoryId,
            @ToolParam(description = "关系描述，可为空", required = false) String description) {
        OntologyLinkCreateParam param = new OntologyLinkCreateParam();
        param.setName(name);
        param.setApiName(apiName);
        param.setOntologyUniqueIdentifierFrom(ontologyUniqueIdentifierFrom);
        param.setOntologyUniqueIdentifierTo(ontologyUniqueIdentifierTo);
        param.setType(OntologyLinkTypeEnum.valueOf(type));
        param.setSpaceId(spaceId);
        param.setCategoryId(categoryId);
        param.setDescription(description);
        ontologyLinkGroupService.createLink(param);
    }
}
