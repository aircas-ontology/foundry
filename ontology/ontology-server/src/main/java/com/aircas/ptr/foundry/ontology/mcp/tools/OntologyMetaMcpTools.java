package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本体元数据 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明本体元数据的只读原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集为 {@code ToolCallbackProvider}，
 * agent-server 端经 {@code tools/list} 动态发现并按需调用。工具方法直接返回既有业务 VO，
 * Spring AI 自动序列化为 MCP 结果；方法委托既有 Service，不含业务逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyMetaMcpTools implements McpTools {

    private final OntologyMetaService ontologyMetaService;

    @Tool(description = """
            按关键词搜索本体（Ontology）元数据。
            当用户想查找、检索、列出本体，或询问"有哪些本体""某某本体是否存在"时使用。
            返回本体的名称、描述、唯一标识、所属空间及实例/关系/属性数量等信息。
            关键词为空时返回全部本体。
            """)
    public List<OntologyMetaInfoVO> searchOntologyMeta(
            @ToolParam(description = "搜索关键词，可为空；为空时返回全部本体", required = false) String keyword) {
        return ontologyMetaService.searchByKeyword(keyword);
    }

    @Tool(description = """
            根据本体唯一标识（uniqueIdentifier）查询单个本体的元数据详情。
            当用户已经知道某个本体的唯一标识，想了解该本体的详细信息时使用。
            返回该本体的名称、描述、所属空间及各类数量统计。
            """)
    public OntologyMetaInfoVO getOntologyMetaDetail(
            @ToolParam(description = "本体唯一标识 uniqueIdentifier") String uniqueIdentifier) {
        return ontologyMetaService.getMetaByUniqueIdentifier(uniqueIdentifier);
    }

    @Tool(description = """
            按本体空间 id（spaceId）列出该空间下已存在的本体对象（启用中）。
            当用户在创建新本体对象时需要推导它与同空间已有对象的关系（功能四）时使用，
            返回包含 displayName、apiName、uniqueIdentifier、description 等字段，不含实体与属性详情。
            空间内无已有本体时返回空集合，调用方应据此短路关系推导。
            """)
    public List<OntologyMetaInfoVO> listOntologyMetaBySpace(
            @ToolParam(description = "本体空间 id（ontology_space 主键）") Integer spaceId) {
        return ontologyMetaService.listBySpaceId(spaceId);
    }
}
