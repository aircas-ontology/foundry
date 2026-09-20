package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.agent.tools.api.model.OntologyGroupToolVO;
import com.aircas.ptr.foundry.agent.tools.api.model.OntologyMetaToolVO;
import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.tool.OntologyQueryToolsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本体工具接口（面向 Agent）。
 *
 * <p>将 ontology-server 的只读原子操作以 REST 形式暴露给 agent-server 的工具适配器调用。
 * 这些接口是"工具"而非面向前端的业务接口，返回统一使用工具契约模型 {@code ToolVO}。
 * 路径遵循项目下划线命名规范（见 AI_CODING_RULES.md §2）。</p>
 */
@Tag(name = "本体工具（Agent 调用）")
@RestController
@RequestMapping("/tool")
@RequiredArgsConstructor
@Validated
public class OntologyToolController {

    private final OntologyQueryToolsImpl ontologyQueryTools;

    @GetMapping("/meta_search")
    @Operation(summary = "按关键词搜索本体元数据（工具）")
    public RestResult<List<OntologyMetaToolVO>> searchOntologyMeta(
            @RequestParam(name = "keyword", required = false) @Parameter(description = "搜索关键词，可为空") String keyword) {
        return RestResult.ofData(ontologyQueryTools.searchOntologyMeta(keyword));
    }

    @GetMapping("/meta_detail")
    @Operation(summary = "按唯一标识查询本体元数据详情（工具）")
    public RestResult<OntologyMetaToolVO> getOntologyMetaDetail(
            @RequestParam(name = "uniqueIdentifier") @Parameter(description = "本体唯一标识") String uniqueIdentifier) {
        return RestResult.ofData(ontologyQueryTools.getOntologyMetaDetail(uniqueIdentifier));
    }

    @GetMapping("/group_by_space")
    @Operation(summary = "按空间 id 查询本体分组列表（工具）")
    public RestResult<List<OntologyGroupToolVO>> listGroupsBySpace(
            @RequestParam(name = "spaceId") @Parameter(description = "本体空间 id") Integer spaceId) {
        return RestResult.ofData(ontologyQueryTools.listGroupsBySpace(spaceId));
    }
}
