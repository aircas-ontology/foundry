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
        + "inputParams 的每个入参放多个数据位置），候选值只选实例时彼此做笛卡尔积、选了非 main 分组的行时改为各轴按下标配对成组，"
        + "最多 50 组，逐组执行并各自落库，失败组合在 cases 里记原因")
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
            + "实例主键可以写成数组（如 {\"from\":[1,2]}），数组之间做笛卡尔积、逐组执行，即批量测试；"
            + "候选值里一旦有非 main 分组的行，全部候选值改为按下标配对成组，",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":1,\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":2}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "writeBackEntityPrimaryKey", description = "结果回写落点的实例主键：只用于配了结果回写的基础函数行为，"
            + "只决定写到哪个实例，不参与查询收窄；回写目标与查询对象是同一个时用它区分「查哪个实例」和「写到哪个实例」。"
            + "不传则沿用 entityPrimaryKey/entityPrimaryKeys 里回写目标对象的键",
            example = "2")
    private Object writeBackEntityPrimaryKey;

    @Schema(name = "inputParams", description = "按算子入参指定的数据位置：入参名 -> 数据位置数组。"
            + "键也可以写该入参绑定行的字段名（LIST 的元素字段如 longitude、对象子字段如 position.longitude），"
            + "后端按绑定行归到所属入参：LIST 的各字段位置按数组下标拼成同一个元素，对象入参的各字段必须读同一处；"
            + "外部输入、固定值来源的参数不读实例，按它的参数名或字段名给的位置会被忽略，值分别从 params 与行为定义取。"
            + "数组的一个元素就是一组完整取值：读哪个实例（entityPrimaryKey）、每个存储分组读哪一行（dataPrimaryKeys），"
            + "只读一个位置时数组里放一个元素；放多个元素就是批量测试的候选值，只选实例时各入参的候选值做笛卡尔积、"
            + "一旦有候选值选了非 main 分组的行就改为各轴按下标配对成组（此时各轴候选值个数要一致），逐组执行；"
            + "LIST 类型的入参除外：它的数组是同一个列表的元素，一次执行读同一个实例的多行，"
            + "各元素的 entityPrimaryKey 必须一致，既不参与批量组合，也不按个数配对；"
            + "命中时优先于 entityPrimaryKeys，只影响该入参的读取，不影响出参写回和关系落库；"
            + "绑定落在关系端点对象上也按这里指定的实例读，关系边仍由 from/to 决定；不绑算子的行为也不支持",
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
            + "基础函数的过滤条件声明了外部输入时，按条件的 paramName 在这里给值，IN 传数组、BETWEEN 传两个值的数组；"
            + "数组值就是 LIST 参数本身的内容，不参与批量组合")
    private Map<String, Object> params;
}
