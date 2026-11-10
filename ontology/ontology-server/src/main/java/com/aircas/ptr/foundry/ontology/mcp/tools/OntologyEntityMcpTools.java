package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.param.EntityPropertyRowQueryParam;
import com.aircas.ptr.foundry.ontology.model.vo.EntityInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyRowDetailVO;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 对象实体 MCP 工具。
 *
 * <p>复用 {@code OntologyEntityController} 的 {@code /list}（实体分页查询）与 {@code /row_detail}
 * （按行返回实体全部属性）两个查询能力，由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。
 * 工具直接调用 {@link EntityService}，不经过控制器的参数校验切面，因此在此做基础的空值防护。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyEntityMcpTools implements McpTools {

    private final EntityService entityService;

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
            @ToolParam(description = "实体主键值（entityPrimaryKey）") Object entityPrimaryKey,
            @ToolParam(description = "排序方向，可选值：ASC（升序）、DESC（降序）；不传默认 DESC", required = false) String sort) {
        if (StringUtils.isEmpty(ontologyUniqueIdentifier) || entityPrimaryKey == null) {
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
                .entityPrimaryKey(entityPrimaryKey)
                .sort(sortEnum)
                .build();
        return entityService.getEntityPropertyRowDetail(param);
    }
}
