package com.aircas.ptr.foundry.ontology.mcp.tools;

import com.aircas.ptr.foundry.ontology.mcp.McpTools;
import com.aircas.ptr.foundry.ontology.model.param.OntologyInstanceUrlSaveParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyInstanceUrlVO;
import com.aircas.ptr.foundry.ontology.service.OntologyInstanceUrlService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域空间实例 URL MCP 工具（对应 {@code OntologyInstanceUrlController}，REST 前缀 /instance/url）。
 *
 * <p>按（空间 + 对象 + 实例）保存并查询实例对应的 URL，落库主库 ontology.ontology_instance_url，
 * 唯一键即这三个 id，保存为覆盖写。由 {@code McpServerConfig} 通过 {@link McpTools} 标记接口自动收集。</p>
 */
@Component
@RequiredArgsConstructor
public class OntologyInstanceUrlMcpTools implements McpTools {

    private final OntologyInstanceUrlService ontologyInstanceUrlService;

    @Tool(description = """
            保存实例 URL（对应 REST 端点 POST /instance/url）。
            当需要为某个空间下某对象的某个实例登记/更新其对应的外部 URL（如详情页链接）时使用。
            按（spaceId + objectId + instanceId）唯一：已存在则覆盖 url 并把存入时间刷成本次时间，不新增行。
            参数：
            - spaceId：必填，对象空间 id（须为已存在的空间）；
            - objectId：必填，对象 id（ontology_meta.unique_identifier，即其余接口里的 ontologyIdentifier），最长 255 字；
            - instanceId：必填，实例 id（实例主键值，数字主键按字符串传），最长 255 字；
            - url：必填，实例对应的 URL（如 https://172.16.18.58/detail/123），最长 1024 字，落库前会去除首尾空白。
            返回 OntologyInstanceUrlVO：id 主键、spaceId、objectId、instanceId、url、createTime 存入时间
            （同一实例再次提交后为本次写入时间）。id 与存入时间由后台生成，不接受传入。
            """)
    public OntologyInstanceUrlVO saveInstanceUrl(
            @ToolParam(description = "对象空间 id（须为已存在的空间）") Integer spaceId,
            @ToolParam(description = "对象 id（ontology_meta.unique_identifier），最长 255 字") String objectId,
            @ToolParam(description = "实例 id（实例主键值，数字主键按字符串传），最长 255 字") String instanceId,
            @ToolParam(description = "实例对应的 URL，最长 1024 字") String url) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId（对象空间 id）不能为空");
        }
        requireText(objectId, "objectId");
        requireText(instanceId, "instanceId");
        requireText(url, "url");
        if (objectId.length() > 255) {
            throw new IllegalArgumentException("对象 id 最长 255 字");
        }
        if (instanceId.length() > 255) {
            throw new IllegalArgumentException("实例 id 最长 255 字");
        }
        if (url.length() > 1024) {
            throw new IllegalArgumentException("URL 最长 1024 字");
        }
        OntologyInstanceUrlSaveParam param = new OntologyInstanceUrlSaveParam();
        param.setSpaceId(spaceId);
        param.setObjectId(objectId);
        param.setInstanceId(instanceId);
        param.setUrl(url);
        return ontologyInstanceUrlService.saveInstanceUrl(param);
    }

    @Tool(description = """
            查询实例 URL（对应 REST 端点 GET /instance/url/list）。只读操作。
            当需要查看某空间下实例登记的外部 URL（全部或按对象/实例过滤）时使用。
            参数：
            - spaceId：必填，对象空间 id；
            - objectId：可选，对象 id（ontology_meta.unique_identifier），不传不过滤；
            - instanceId：可选，实例 id，不传不过滤。
            objectId / instanceId 都不传则查该空间下全部记录，按存入时间倒序（最新在前）。
            返回 OntologyInstanceUrlVO 列表：id、spaceId、objectId、instanceId、url、createTime 存入时间。
            无匹配时返回空列表。
            """)
    public List<OntologyInstanceUrlVO> listInstanceUrls(
            @ToolParam(description = "对象空间 id（必填）") Integer spaceId,
            @ToolParam(description = "对象 id（ontology_meta.unique_identifier），不传不过滤", required = false) String objectId,
            @ToolParam(description = "实例 id，不传不过滤", required = false) String instanceId) {
        if (spaceId == null) {
            throw new IllegalArgumentException("spaceId（对象空间 id）不能为空");
        }
        return ontologyInstanceUrlService.listInstanceUrls(spaceId, objectId, instanceId);
    }

    private void requireText(String value, String paramName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
    }
}