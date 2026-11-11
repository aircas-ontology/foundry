package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为测试请求；入参方向给出多个候选值即批量测试（entityPrimaryKeys 的值写成数组，"
        + "inputParams 的每个入参放多个数据位置），候选值之间做笛卡尔积、最多 50 组，逐组执行并各自落库，失败组合在 cases 里记原因")
public class ActionTestParam {

    @Schema(name = "ontologySpaceId", description = "对象空间id", example = "156", required = true)
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "actionApi", description = "行为api", example = "syncSpeed", required = true)
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(name = "entityPrimaryKey", description = "行为所属对象的实例主键；只涉及一个对象时用它。不支持传数组，多组取值改用 entityPrimaryKeys 或 inputParams",
            example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "entityPrimaryKeys", description = "各对象选中的实例主键：对象id -> 实例主键；"
            + "应用行为的入参/出参可能跨对象，映射涉及的对象都要提供；某个算子入参要单独读别的实例时用 inputParams；"
            + "行为绑定关系时用保留键 from、to 分别传关系两端的实例主键，与按对象 id 传的实例互不混用；"
            + "实例主键可以写成数组（如 {\"from\":[1,2]}），数组之间做笛卡尔积、逐组执行，即批量测试",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":1,\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":2}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "inputParams", description = "按算子入参指定的数据位置：入参名 -> 数据位置数组。"
            + "数组的一个元素就是一组完整取值：读哪个实例（entityPrimaryKey）、每个存储分组读哪一行（dataPrimaryKeys），"
            + "只读一个位置时数组里放一个元素；放多个元素就是批量测试的候选值，元素之间做笛卡尔积、逐组执行；"
            + "命中时优先于 entityPrimaryKeys，只影响该入参的读取，不影响出参写回和关系落库；"
            + "关系对端的实例由本次遍历决定，不支持按入参指定；不绑算子的行为也不支持",
            example = "{\"name\":[{\"entityPrimaryKey\":11}],"
                    + "\"max_mach\":[{\"entityPrimaryKey\":101,\"dataPrimaryKeys\":{\"duty_log\":11}},"
                    + "{\"entityPrimaryKey\":102,\"dataPrimaryKeys\":{\"duty_log\":12}}]}")
    private Map<String, List<ActionInstanceTargetParam>> inputParams;

    @Schema(name = "outputParams", description = "按出参绑定指定的写回位置：key -> {实例主键, 存储分组->行主键}。"
            + "key 按写属性的那条出参绑定定位，从高到低三种写法：目标属性的属性唯一标识 > 该行的出参字段名 > 该行的顶层算子出参名；"
            + "脚本算子往往只有一个顶层出参，几个字段要写到不同实例就按字段名（或属性唯一标识）逐个点名。"
            + "字段名命中多条绑定且分属不同对象时会报错，改用属性唯一标识；"
            + "命中时优先于 entityPrimaryKeys，只影响该行写回，不影响属性入参读取和关系落库；"
            + "写回目标是关系对端对象时同样按这里点名的实例落库，不被 from/to 遍历覆盖；不绑算子的行为不支持；"
            + "这里不支持数组，传了直接报错",
            example = "{\"name\":{\"entityPrimaryKey\":2},\"status\":{\"entityPrimaryKey\":3,\"dataPrimaryKeys\":{\"duty_log\":12}}}")
    private Map<String, ActionInstanceTargetParam> outputParams;

    @Schema(name = "dataPrimaryKey", description = "分组表行的主键：属性值-修改/删除必填；不支持数组", example = "1")
    private Object dataPrimaryKey;

    @Schema(name = "linkEntityPrimaryKey", description = "关系对端实体主键：兼容写法，行为测试建议在 entityPrimaryKeys 里用 from/to 传两端；不支持数组", example = "2")
    private Object linkEntityPrimaryKey;

    @Schema(name = "params", description = "来源为外部输入的参数取值（参数名 -> 值），固定值不在此传；"
            + "数组值就是 LIST 参数本身的内容，不参与批量组合")
    private Map<String, Object> params;
}
