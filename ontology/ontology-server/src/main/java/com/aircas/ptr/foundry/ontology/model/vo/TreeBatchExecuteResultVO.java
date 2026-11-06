package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树批量执行结果：汇总统计 + 实例明细。
 * <p>明细默认只含失败实例，避免万级实例时响应体过大；需要成功明细时由调用方显式打开。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树批量执行结果")
public class TreeBatchExecuteResultVO {

    @Schema(name = "treeApi", description = "行为树api", example = "tree.satellite.watch")
    private String treeApi;

    @Schema(name = "total", description = "本次实际执行的实例条数（分片后）", example = "10000")
    private int total;

    @Schema(name = "successCount", description = "执行成功的实例条数", example = "9998")
    private int successCount;

    @Schema(name = "failCount", description = "执行失败的实例条数", example = "2")
    private int failCount;

    @Schema(name = "results", description = "实例明细，默认只含失败实例")
    private List<TreeBatchInstanceResultVO> results = new ArrayList<>();

    public List<TreeBatchInstanceResultVO> getResults() {
        return results == null ? new ArrayList<>() : results;
    }
}
