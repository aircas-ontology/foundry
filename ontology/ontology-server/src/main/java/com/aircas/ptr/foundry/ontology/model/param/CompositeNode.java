package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * 函数编排节点。
 * <p>kind 为空表示引用函数的节点；loop/branch/transform 为控制流逻辑节点，
 * 分别使用 {@code loopConfig}/{@code branchConfig}/{@code transformConfig}。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排节点")
public class CompositeNode {

    @Schema(name = "id", description = "节点唯一标识（画布内）", example = "node_1")
    private String id;

    @Schema(name = "kind", description = "节点类型：function/loop/branch/transform，为空按 function 处理", example = "function")
    private String kind;

    @Schema(name = "functionId", description = "函数节点：被引用函数的 api", example = "querySensorData")
    private String functionId;

    @Schema(name = "functionVersion", description = "函数节点：被引用函数的版本（可空）", example = "v1")
    private String functionVersion;

    @Schema(name = "label", description = "节点显示名称", example = "查询传感器数据")
    private String label;

    @Schema(name = "loopConfig", description = "循环节点配置")
    private CompositeLoopConfig loopConfig;

    @Schema(name = "branchConfig", description = "分支节点配置")
    private CompositeBranchConfig branchConfig;

    @Schema(name = "transformConfig", description = "转换节点配置")
    private CompositeTransformConfig transformConfig;

    @Schema(name = "switchConfig", description = "Switch Case 节点配置")
    private CompositeSwitchConfig switchConfig;

    @Schema(name = "inputDefaults", description = "节点输入端口的默认值，key 为端口（参数）标识")
    private Map<String, Object> inputDefaults;

    @Schema(name = "inputParameters", description = "逻辑节点（loop/branch/transform）的输入端口定义；函数节点由被引用函数决定")
    private List<CompositeParameter> inputParameters;

    @Schema(name = "outputParameters", description = "逻辑节点（loop/branch/transform）的输出端口定义；函数节点由被引用函数决定")
    private List<CompositeParameter> outputParameters;
}