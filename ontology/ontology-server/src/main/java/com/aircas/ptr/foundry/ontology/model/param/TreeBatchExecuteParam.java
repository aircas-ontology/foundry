package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 行为树批量执行请求。
 * <p>按对象类型遍历该类型下的全部实例数据，每条实例单独初始化黑板后跑同一棵树，
 * 实例之间互不影响。分片规则与行为批量执行一致，供调度侧横向拆分。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树批量执行请求")
public class TreeBatchExecuteParam {

    @Schema(name = "ontologySpaceId", description = "行为树所属的对象空间id；行为api只在空间内唯一，同名树跨空间靠它区分",
            example = "156", required = true)
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "treeApi", description = "行为树api", example = "tree.satellite.watch", required = true)
    @NotBlank(message = "treeApi is empty")
    private String treeApi;

    @Schema(name = "ontologyUniqueIdentifier", description = "按哪个对象类型遍历实例数据", example = "satellite", required = true)
    @NotBlank(message = "ontologyUniqueIdentifier is empty")
    private String ontologyUniqueIdentifier;

    @Schema(name = "shardIndex", description = "分片序号，从 0 开始；不传表示不分片", example = "0")
    private Integer shardIndex;

    @Schema(name = "shardTotal", description = "分片总数；不传表示不分片", example = "4")
    private Integer shardTotal;

    @Schema(name = "inputs", description = "黑板键的初始取值，所有实例共用；实例差异化数据通过黑板绑定对象属性获取")
    private Map<String, Object> inputs;

    @Schema(name = "includeSuccess", description = "是否返回成功实例的明细，默认只返回失败明细", example = "false")
    private Boolean includeSuccess;
}
