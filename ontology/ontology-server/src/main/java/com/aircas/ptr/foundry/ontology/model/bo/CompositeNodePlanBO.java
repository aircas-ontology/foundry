package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.ontology.model.param.CompositeLoopConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排节点的运行期定义，由 {@code CompositeElCompiler} 从画布配置推导，画布节点 id 与 LiteFlow 节点 id 在此建立映射。
 */
@Data
@Schema(name = "CompositeNodePlanBO", description = "编排节点运行期定义")
public class CompositeNodePlanBO {

    @Schema(description = "LiteFlow 节点 id")
    private String elId;

    @Schema(description = "画布节点 id")
    private String canvasId;

    @Schema(description = "节点类型：function/loop/branch/transform")
    private String kind;

    @Schema(description = "节点显示名称")
    private String label;

    @Schema(description = "引用的原子函数 api")
    private String functionApi;

    @Schema(description = "入参：参数 id -> 参数名")
    private Map<String, String> inputParams = new LinkedHashMap<>();

    @Schema(description = "出参：参数 id -> 参数名")
    private Map<String, String> outputParams = new LinkedHashMap<>();

    @Schema(description = "入参默认值：参数 id -> 值（支持嵌套 Map 等复杂类型）")
    private Map<String, Object> inputDefaults = new LinkedHashMap<>();

    @Schema(description = "子字段默认值：顶层入参端口名 -> 归位后的默认对象；"
            + "画布把子字段绑定的默认值按叶子字段名登记，与按端口 id 登记的 inputDefaults 分开存放")
    private Map<String, Map<String, Object>> nestedInputDefaults = new LinkedHashMap<>();

    @Schema(description = "外部输入映射：目标入参 id -> 外部参数名")
    private Map<String, String> externalInputs = new LinkedHashMap<>();

    @Schema(description = "外部输出映射：源出参 id -> 外部参数名")
    private Map<String, String> externalOutputs = new LinkedHashMap<>();

    @Schema(description = "分支目标：true/false -> 目标节点 elId")
    private Map<String, String> branchTargets = new LinkedHashMap<>();

    @Schema(description = "循环节点：循环体内部件 elId，按执行顺序")
    private List<String> loopBody = new ArrayList<>();

    @Schema(description = "循环节点：循环体第一层节点 elId")
    private List<String> loopRoots = new ArrayList<>();

    @Schema(description = "节点所属外层循环 elId，由外到内")
    private List<String> enclosingLoops = new ArrayList<>();

    @Schema(description = "数据连线：本节点出参流向的下游目标")
    private List<CompositeEdgeTargetBO> edgeTargets = new ArrayList<>();

    @Schema(description = "循环节点配置")
    private CompositeLoopConfig loopConfig;

    @Schema(description = "分支节点判断表达式")
    private String branchExpression;

    @Schema(description = "转换节点语言")
    private String transformLanguage;

    @Schema(description = "转换节点脚本")
    private String transformScript;

    @Schema(description = "转换节点映射模式：json/script")
    private String transformMappingMode;

    @Schema(description = "转换节点 JSON 映射规则（JSON 字符串）")
    private String transformMappings;

    public Map<String, String> getInputParams() {
        return inputParams == null ? inputParams = new LinkedHashMap<>() : inputParams;
    }

    public Map<String, Map<String, Object>> getNestedInputDefaults() {
        return nestedInputDefaults == null ? nestedInputDefaults = new LinkedHashMap<>() : nestedInputDefaults;
    }

    public List<String> getLoopBody() {
        return loopBody == null ? loopBody = new ArrayList<>() : loopBody;
    }

    public List<String> getLoopRoots() {
        return loopRoots == null ? loopRoots = new ArrayList<>() : loopRoots;
    }

    public List<String> getEnclosingLoops() {
        return enclosingLoops == null ? enclosingLoops = new ArrayList<>() : enclosingLoops;
    }

    /**
     * 是否是控制流节点（循环/分支/转换），只有原子函数节点需要真正调用下游函数。
     *
     * @return true 表示非原子函数节点
     */
    public boolean isLogicNode() {
        return !"function".equals(kind);
    }
}
