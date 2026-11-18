package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.param.GlobalSearchParam;
import com.aircas.ptr.foundry.ontology.model.vo.GlobalSearchHitVO;
import com.aircas.ptr.foundry.ontology.service.GlobalSearchService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 全局检索 MCP 工具。
 *
 * <p>作为 MCP Server 的能力提供方：用 Spring AI {@link Tool} / {@link ToolParam} 声明跨索引全局检索原子操作，
 * 由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。方法委托既有 Service，
 * 直接返回自有业务 VO，Spring AI 自动序列化为 MCP 结果。</p>
 */
@Component
@RequiredArgsConstructor
public class GlobalSearchMcpTools implements McpTools {

    private final GlobalSearchService globalSearchService;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Tool(description = """
            全局检索：按关键词跨五个对象索引（空间/对象/关系/属性/实例）全文检索，按相关性得分降序返回命中项。
            当用户只给了一个名称/描述片段、不知道目标是空间、对象、关系、属性还是实例，需要全平台定位时使用；
            已知类型且限定空间时，用对应的 queryOntologyMetaBySpace / queryLinks / queryProperties 等更精准。
            paramJson 为 JSON 对象字符串，字段：
            - keyword：查询内容，必填，匹配文档所有字段（名称/描述等）；
            - searchType：检索类型，可选，取值 1=空间（含主题/子空间） 2=对象 3=关系 4=属性 5=实例；
              不传则五类索引全查；传 1-5 之外的值报错；
            - size：返回条数上限，可选，缺省 100。
            示例：{"keyword":"舰船","searchType":2,"size":20}。
            返回命中列表 [{name, type（命中类型中文）, spaceType, uniqueIdentifier（命中文档业务 id）,
            ontologyUniqueIdentifier, desc, spaceId, objectId, instanceId, propertyId, ontologyName, spaceName}]，
            不同命中类型只填对应的 id 字段，其余为空。空数组表示无命中（关键词无匹配，不代表出错）。
            """)
    public List<GlobalSearchHitVO> globalSearch(
            @ToolParam(description = "检索入参 JSON 对象字符串，如 {\"keyword\":\"舰船\",\"searchType\":2,\"size\":20}；keyword 必填，searchType/size 可选") String paramJson) {
        if (StringUtils.isBlank(paramJson)) {
            throw new IllegalArgumentException("paramJson 不能为空，应为 {\"keyword\":\"...\"} 形式的 JSON 对象字符串");
        }
        GlobalSearchParam param;
        try {
            param = jsonMapper.readValue(paramJson, GlobalSearchParam.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("paramJson 解析失败，应为合法 JSON 对象：" + e.getMessage());
        }
        if (StringUtils.isBlank(param.getKeyword())) {
            throw new IllegalArgumentException("keyword（查询内容）不能为空");
        }
        if (param.getSearchType() != null && (param.getSearchType() < 1 || param.getSearchType() > 5)) {
            throw new IllegalArgumentException("searchType 取值范围为 1-5（1=空间 2=对象 3=关系 4=属性 5=实例），不传则五类全查");
        }
        return globalSearchService.search(param);
    }
}