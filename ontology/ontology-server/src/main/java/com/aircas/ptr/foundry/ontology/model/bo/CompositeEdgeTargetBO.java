package com.aircas.ptr.foundry.ontology.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排节点之间的一条数据连线在编译后的形态。
 * <p>两端都以<b>顶层端口名</b>为键，连线引用到子字段时由 {@code fieldPath} 记录端口对象内部的下钻路径。</p>
 */
@Data
@Schema(name = "CompositeEdgeTargetBO", description = "编排数据连线目标")
public class CompositeEdgeTargetBO {

    @Schema(description = "目标节点 LiteFlow 节点 id")
    private String targetElId;

    @Schema(description = "目标节点的顶层入参端口名")
    private String targetParamName;

    @Schema(description = "本节点的顶层出参端口名（与产出 Map 的键一致）")
    private String sourceParamId;

    @Schema(description = "源端口对象内的取值路径，引用顶层端口时为空")
    private List<String> sourceFieldPath = new ArrayList<>();

    @Schema(description = "目标端口对象内的写入路径，引用顶层端口时为空")
    private List<String> targetFieldPath = new ArrayList<>();

    public CompositeEdgeTargetBO() {
    }

    public CompositeEdgeTargetBO(String targetElId, String targetParamName, String sourceParamId,
                                 List<String> sourceFieldPath, List<String> targetFieldPath) {
        this.targetElId = targetElId;
        this.targetParamName = targetParamName;
        this.sourceParamId = sourceParamId;
        this.sourceFieldPath = sourceFieldPath == null ? new ArrayList<>() : sourceFieldPath;
        this.targetFieldPath = targetFieldPath == null ? new ArrayList<>() : targetFieldPath;
    }

    /** 是否引用到目标端口子字段 */
    public boolean nestedTarget() {
        return targetFieldPath != null && !targetFieldPath.isEmpty();
    }
}
