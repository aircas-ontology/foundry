package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.service.OntologyCategoryInitService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 对象分类体系初始化 MCP 工具（对应 {@code OntologyCategoryInitController}，REST 前缀 /category/init）。
 *
 * <p>为全部空间建立对象/关系/行为分类树的“全部”根节点、为每个对象建立属性分类树的“全部”根节点，
 * 并回填 categoryId。由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyCategoryInitMcpTools implements McpTools {

    private final OntologyCategoryInitService ontologyCategoryInitService;

    @Tool(description = """
            初始化全部空间的对象/关系/属性/行为分类树根节点（对应 REST 端点 POST /category/init/all）。
            管理/初始化类操作，通常在新环境部署或存量数据补齐分类体系时执行一次即可，一般不需要在普通对话中反复调用。
            做什么：为每个空间建立对象分类树、关系分类树、行为分类树的“全部”根节点，为每个对象建立属性分类树的
            “全部”根节点，并把对应 categoryId 回填到 ontology_meta / ontology_link_group / ontology_property /
            ontology_action 中 categoryId 为空的记录。
            无参数。已有根节点与已回填分类的记录不会重复处理（只补缺失部分），整批在同一事务内执行。
            返回统计 Map：spaceCount 处理的空间数、metaUpdated 回填分类的对象数、linkUpdated 回填分类的关系数、
            propertyUpdated 回填分类的属性数、actionUpdated 回填分类的行为数。计数为 0 表示没有需要补齐的数据，不是错误。
            """)
    public Map<String, Integer> initAllCategoryRoots() {
        return ontologyCategoryInitService.initAllCategoryRoots();
    }
}