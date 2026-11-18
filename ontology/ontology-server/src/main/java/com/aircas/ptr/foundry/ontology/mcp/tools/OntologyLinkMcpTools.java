package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.converter.DataConverter;
import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkDirectionEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkCategory;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkObjectsVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkCategoryService;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @Tool(description = """
            更新对象关系（Link）的名称、所属分类、备注/描述。
            当用户需要给已有关系改名、调整分类或补充说明时使用；关系的两端对象与类型不可通过此工具修改。
            参数说明：
            - uniqueIdentifier：关系唯一标识（ontology_link_group.unique_identifier），必填，可先用 queryLinks / pageGetLinkObjects 查到。
            - name：新的关系名称，选填，不传表示不修改名称。
            - categoryId：新的关系分类 id（ontology_link_category.id），选填，不传表示不修改分类。
            - description：新的备注/描述，选填，不传表示不修改。
            写操作：直接修改库中关系记录。关系不存在时会报错。
            返回中文结果说明。
            """)
    public String updateLink(
            @ToolParam(description = "关系唯一标识（unique_identifier），必填") String uniqueIdentifier,
            @ToolParam(description = "新的关系名称，不传表示不修改", required = false) String name,
            @ToolParam(description = "新的关系分类 id（ontology_link_category.id），不传表示不修改", required = false) Integer categoryId,
            @ToolParam(description = "新的关系备注/描述，不传表示不修改", required = false) String description) {
        if (StringUtils.isBlank(uniqueIdentifier)) {
            throw new IllegalArgumentException("uniqueIdentifier 不能为空");
        }
        OntologyLinkUpdateParam param = new OntologyLinkUpdateParam();
        param.setUniqueIdentifier(uniqueIdentifier);
        param.setName(name);
        param.setCategoryId(categoryId);
        param.setDescription(description);
        ontologyLinkGroupService.updateLink(param);
        return "关系更新成功，uniqueIdentifier=" + uniqueIdentifier;
    }

    @Tool(description = """
            根据关系唯一标识（uniqueIdentifier）查询单条对象关系的详细信息。
            当用户已知关系 id、需要查看关系名称、api 名称、两端对象、分类、描述时使用。
            参数 uniqueIdentifier 为 ontology_link_group.unique_identifier，字符串，必填。
            返回 OntologyLinkInfoVO：uniqueIdentifier、name、apiName、categoryId、description、
            两端对象的 uniqueIdentifier/名称/图标（ontologyUniqueIdentifierFrom/To、ontologyNameFrom/To 等）。
            关系不存在时返回 null 或报错（以服务端行为为准）。
            """)
    public OntologyLinkInfoVO getLinkByUniqueIdentifier(
            @ToolParam(description = "关系唯一标识（unique_identifier），必填") String uniqueIdentifier) {
        if (StringUtils.isBlank(uniqueIdentifier)) {
            throw new IllegalArgumentException("uniqueIdentifier 不能为空");
        }
        return ontologyLinkGroupService.getLinkByUniqueIdentifier(uniqueIdentifier);
    }

    @Tool(description = """
            根据关系唯一标识（uniqueIdentifier）查询关系两侧的对象信息。
            当用户已知关系 id、只关心关系连接了哪两个对象（对象 id、名称、api 名称、图标）时使用；
            需要关系本身的名称/分类/描述时用 getLinkByUniqueIdentifier。
            参数 uniqueIdentifier 为 ontology_link_group.unique_identifier，字符串，必填。
            返回 OntologyLinkObjectsVO：uniqueIdentifier（关系 id）、name（关系名称）、
            fromObject（起点对象信息）、toObject（终点对象信息）。
            """)
    public OntologyLinkObjectsVO getLinkObjectsByUniqueIdentifier(
            @ToolParam(description = "关系唯一标识（unique_identifier），必填") String uniqueIdentifier) {
        if (StringUtils.isBlank(uniqueIdentifier)) {
            throw new IllegalArgumentException("uniqueIdentifier 不能为空");
        }
        return ontologyLinkGroupService.getLinkObjectsByUniqueIdentifier(uniqueIdentifier);
    }

    @Tool(description = """
            分页查询对象空间内的关系列表，支持按关系名称模糊匹配。
            当用户想浏览/检索某个空间下有哪些关系时使用；已知两端对象和关系名精确查找时用 queryLinks。
            参数说明：
            - spaceId：对象空间 id，整数，必填。
            - relationName：关系名称关键字，模糊匹配，选填，不传查全部。
            - pageNum：页码，从 1 开始，默认 1。
            - pageSize：每页条数，默认 10。
            返回 MyBatis-Plus Page<OntologyLinkObjectsVO>：records 为关系列表（关系 id、关系名称、
            fromObject/toObject 两端对象信息），total 为总条数，current/size 为分页信息。
            无匹配时 records 为空列表、total=0。
            """)
    public Page<OntologyLinkObjectsVO> pageGetLinkObjects(
            @ToolParam(description = "对象空间 id（spaceId），必填") Integer spaceId,
            @ToolParam(description = "关系名称关键字，模糊匹配，不传查全部", required = false) String relationName,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer pageNum,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        int num = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        return ontologyLinkGroupService.pageGetLinkObjects(spaceId, relationName, num, size);
    }

    @Tool(description = """
            根据关系唯一标识删除对象关系（Link）。
            当用户明确要求删除两个对象之间的某条关系时使用。
            参数 linkUniqIdentifier 为 ontology_link_group.unique_identifier，字符串，必填。
            写操作副作用：删除关系定义及其对应的实体关系边数据，删除后不可恢复；
            若关系被行为等引用，服务端可能拒绝删除并报错。
            返回中文结果说明。
            """)
    public String deleteLink(
            @ToolParam(description = "要删除的关系唯一标识（unique_identifier），必填") String linkUniqIdentifier) {
        if (StringUtils.isBlank(linkUniqIdentifier)) {
            throw new IllegalArgumentException("linkUniqIdentifier 不能为空");
        }
        ontologyLinkGroupService.deleteLinkByLinkUniqueIdentifier(linkUniqIdentifier);
        return "关系删除成功，uniqueIdentifier=" + linkUniqIdentifier;
    }

    @Tool(description = """
            查询与指定对象存在关系关联的其他对象列表（1 跳邻居对象）。
            当用户想知道"某对象都和哪些对象有关系"、只需要邻居对象清单而不需要关系本身时使用；
            需要关系明细时用 getLinksByOntology。
            参数 ontologyUniqueIdentifier 为对象的 uniqueIdentifier（ontology_meta.unique_identifier），字符串，必填。
            返回 List<OntologyMetaInfoVO>：邻居对象的基本信息（对象 id、名称、api 名称、图标等）。
            该对象没有任何关系时返回空列表。
            """)
    public List<OntologyMetaInfoVO> getLinkedOntology(
            @ToolParam(description = "对象唯一标识（ontology_meta.unique_identifier），必填") String ontologyUniqueIdentifier) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier)) {
            throw new IllegalArgumentException("ontologyUniqueIdentifier 不能为空");
        }
        return ontologyLinkGroupService.getLinkedOntology(ontologyUniqueIdentifier);
    }

    @Tool(description = """
            根据对象唯一标识查询该对象参与的关系列表（1 跳），可按方向过滤。
            当用户想查看某对象作为起点/终点/任意一端的所有关系明细时使用。
            参数说明：
            - ontologyUniqueIdentifier：对象唯一标识（ontology_meta.unique_identifier），字符串，必填。
            - direction：关系方向，字符串枚举，选填，默认 ALL。可选值：
              ALL（该对象作为起点或终点的全部关系）、FROM（仅该对象作为起点/主体的关系）、TO（仅该对象作为终点/客体的关系）。
            返回 List<OntologyLinkInfoVO>：关系 id、名称、api 名称、分类 id、描述及两端对象信息。
            无关系时返回空列表。
            """)
    public List<OntologyLinkInfoVO> getLinksByOntology(
            @ToolParam(description = "对象唯一标识（ontology_meta.unique_identifier），必填") String ontologyUniqueIdentifier,
            @ToolParam(description = "关系方向：ALL / FROM / TO，不传默认 ALL", required = false) String direction) {
        if (StringUtils.isBlank(ontologyUniqueIdentifier)) {
            throw new IllegalArgumentException("ontologyUniqueIdentifier 不能为空");
        }
        OntologyLinkDirectionEnum directionEnum = OntologyLinkDirectionEnum.ALL;
        if (StringUtils.isNotBlank(direction)) {
            try {
                directionEnum = OntologyLinkDirectionEnum.valueOf(direction.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("direction 取值非法：" + direction + "，可选值：ALL / FROM / TO");
            }
        }
        return ontologyLinkGroupService.getLinksByOntologyUniqueIdentifier(ontologyUniqueIdentifier, directionEnum);
    }

    @Tool(description = """
            根据对象分组 id（groupId）查询组内所有对象参与的关系列表（1 跳）。
            当用户想查看某个分组内对象之间及对外的全部关系时使用。
            参数 groupId 为对象分组 id，字符串，选填；不传时按服务端默认口径查询（不限分组）。
            返回 List<OntologyLinkInfoVO>：关系 id、名称、api 名称、分类 id、描述及两端对象信息。
            组内对象没有关系时返回空列表。
            """)
    public List<OntologyLinkInfoVO> getLinksByGroupId(
            @ToolParam(description = "对象分组 id（groupId），选填", required = false) String groupId) {
        return ontologyLinkGroupService.getLinksByGroupId(groupId);
    }

    @Tool(description = """
            根据关系分类 id（categoryId）查询该分类下的关系列表。
            当用户想按关系分类浏览关系时使用；分类 id 可先用 listLinkCategories 或 getLinkCategoryTree 查到。
            参数 categoryId 为关系分类 id（ontology_link_category.id），整数，选填；不传则查询全部关系。
            返回 List<OntologyLinkInfoVO>：关系 id、名称、api 名称、分类 id、描述及两端对象信息。
            分类下无关系时返回空列表。
            """)
    public List<OntologyLinkInfoVO> getLinksByCategoryId(
            @ToolParam(description = "关系分类 id（ontology_link_category.id），不传查全部关系", required = false) Integer categoryId) {
        return ontologyLinkGroupService.getByCategoryId(categoryId);
    }

    @Tool(description = """
            查询对象关系图数据（对象-关系-对象），用于绘图/拓扑展示。
            当用户需要空间的关系全景图，或以某个对象为中心的局部关系图时使用。
            参数说明：
            - spaceId：对象空间 id，整数，必填。
            - ontologyUniqueIdentifier：对象唯一标识，字符串，选填；传入则以该对象为中心返回其关系，
              不传则返回本空间全部关系图。
            返回 OntologyLinkGraphVO：centerUniqueIdentifier（中心对象 id，空表示全量）、
            nodes（对象节点列表：uniqueIdentifier/displayName/apiName/icon）、
            edges（关系边列表：关系 uniqueIdentifier/name/apiName/from/to/categoryId/description）。
            空间内无关系时 nodes/edges 为空列表。
            """)
    public OntologyLinkGraphVO getLinkGraph(
            @ToolParam(description = "对象空间 id（spaceId），必填") Integer spaceId,
            @ToolParam(description = "中心对象唯一标识，选填；不传返回本空间全部关系图", required = false) String ontologyUniqueIdentifier) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId 不能为空");
        }
        return ontologyLinkGroupService.getLinkGraph(spaceId, ontologyUniqueIdentifier);
    }
}
