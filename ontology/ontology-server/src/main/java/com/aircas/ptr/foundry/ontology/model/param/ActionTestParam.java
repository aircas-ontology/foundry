package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionTestInputModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为测试请求。inputMode 决定多组取值怎么展开，只属于本次测试、不落库："
        + "CARTESIAN 各取值轴自由组合 / GROUP 各轴按下标配对 / OBJECT 不选实例和行、遍历行为涉及对象下的全部实例；"
        + "逐组执行并各自落库，失败组合在 cases 里记原因")
public class ActionTestParam {

    @Schema(name = "ontologySpaceId", description = "对象空间id", example = "156", required = true)
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "actionApi", description = "行为api", example = "syncSpeed", required = true)
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(name = "inputMode", description = "多组取值的展开方式，只用本次测试、不存库："
            + "CARTESIAN 各取值轴的候选值自由组合 / GROUP 各轴按下标配对成组（候选值个数必须一致）/ "
            + "OBJECT 不选实例和行，遍历行为涉及对象下的全部实例（多个对象按笛卡尔积组合），子表按 listReadRules 读取。"
            + "出现候选值时必须指定：entityPrimaryKeys 的值是数组、非 LIST 入参的 inputParams 一维元素多于一个、"
            + "LIST 入参的 inputParams 二维数组多于一个候选，这三种都是多组；"
            + "LIST 入参的 inputParams 一维数组是这次执行的列表元素，不算多组", example = "CARTESIAN")
    private ActionTestInputModeEnum inputMode;

    @Schema(name = "entityPrimaryKey", description = "行为所属对象的实例主键；只涉及一个对象时用它。不支持传数组，多组取值改用 entityPrimaryKeys 或 inputParams",
            example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "entityPrimaryKeys", description = "各对象选中的实例主键：对象id -> 实例主键；"
            + "应用行为的入参/出参可能跨对象，映射涉及的对象都要提供；某个算子入参要单独读别的实例时用 inputParams；"
            + "行为绑定关系时用保留键 from、to 分别传关系两端的实例主键，与按对象 id 传的实例互不混用；"
            + "实例主键可以写成数组（如 {\"from\":[1,2]}）作为批量测试的候选值，按 inputMode 展开成多组",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":1,\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":2}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "writeBackEntityPrimaryKey", description = "结果回写落点的实例主键：只用于配了结果回写的基础函数行为，"
            + "只决定写到哪个实例，不参与查询收窄；回写目标与查询对象是同一个时用它区分「查哪个实例」和「写到哪个实例」。"
            + "不传则沿用 entityPrimaryKey/entityPrimaryKeys 里回写目标对象的键",
            example = "2")
    private Object writeBackEntityPrimaryKey;

    @Schema(name = "inputParams", description = "按算子入参指定的数据位置：入参名 / 绑定行字段名 -> 数据位置。"
            + "键可以是顶层入参名，也可以是该入参绑定行的字段名（LIST 的元素字段如 longitude、对象子字段如 position.longitude），"
            + "后端按绑定行归到所属入参；同一字段名落在多个入参上时用「父参数名.字段路径」指明归属，如 tracks.longitude；"
            + "外部输入、固定值来源的参数不读实例，按它的参数名或字段名给的位置会被忽略，值分别从 params 与行为定义取。"
            + "写法分两种：一维数组的元素就是数据位置，LIST 入参里它们是这次执行的那份列表的元素（各元素可以各自指定对象和实例，"
            + "一份列表可以由不同对象、不同实例的行拼成），非 LIST 入参里每个元素是一个批量候选值；"
            + "二维数组只在 LIST 入参上用，外层是批量候选、内层是该候选的整份列表元素，这时才按 inputMode 展开成多组。"
            + "LIST 入参按子级键给位置时每个子级只给一个位置，各子级各自读自己的对象和实例：没给 dataPrimaryKeys 的子级"
            + "按该子表默认行数（或 listReadRules）读，给了行按下标取；各子级读到的行数不一致时按最短的对齐，"
            + "此时不必再在 entityPrimaryKeys 里给这些对象选实例。"
            + "每个位置可以写 entityPrimaryKey（不传用 entityPrimaryKeys 里该对象的实例）、"
            + "dataPrimaryKeys（存储分组 -> 行主键，行主键数组按顺序展开成多个元素）；"
            + "按子级键给位置时不传 ontologyIdentifier，读哪个对象由创建行为时的绑定行决定，传了报错；"
            + "按元素给位置时一条列表可以由不同对象的行拼成，绑定跨多个对象时元素里必须传 ontologyIdentifier；"
            + "命中时优先于 entityPrimaryKeys，只影响该入参的读取，不影响出参写回和关系落库；"
            + "绑定落在关系端点对象上也按这里指定的实例读，关系边仍由 from/to 决定；不绑算子的行为也不支持",
            example = "{\"name\":[{\"entityPrimaryKey\":11}],"
                    + "\"tracks.longitude\":[[{\"entityPrimaryKey\":1,\"dataPrimaryKeys\":{\"track\":11}},"
                    + "{\"entityPrimaryKey\":1,\"dataPrimaryKeys\":{\"track\":12}}],"
                    + "[{\"entityPrimaryKey\":2,\"dataPrimaryKeys\":{\"track\":21}}]]}")
    private Map<String, ActionInputPositionsParam> inputParams;

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
            + "键按整包参数名给值（对象给 JSON 对象、列表给数组），也可以按绑定行的字段路径全名给"
            + "（tracks.time、startPoint.latitude），全名键优先于同名的裸键；"
            + "基础函数的过滤条件声明了外部输入时，按条件的属性 apiName 在这里给值，IN 传数组、BETWEEN 传两个值的数组；"
            + "数组值就是 LIST 参数本身的内容，不参与批量组合。多组测试共用同一个值，来源为外部输入的参数必须给值")
    private Map<String, Object> params;

    @Schema(name = "listReadRules", description = "子表读取规则：子级字段 -> 读取行数与排序方式。键写成「父参数名.字段路径」"
            + "（如 tracks.time、startPoint.position.longitude），字段名只属于一个入参时也可裸写字段名；"
            + "没有字段路径的绑定行按它绑的属性的 apiName 当字段名。不接受顶层入参名做键：同一个入参的子级可以来自"
            + "不同对象的不同子表，一张子表一次只能按一个列取一段行，按入参给规则分不开。一条键命中的所有绑定行共用"
            + "这份规则；落到同一张子表上的多条键必须写成一致的内容，否则报错。排序属性必须是这条键所在子表上绑过的属性，"
            + "行数与排序都不会作用到别的子表上。不配规则的子表沿用原口径：按行主键倒序取最新 10 行"
            + "（绑定行配了 rowLimit 的按行数）；子级位置自己给了行主键时以行主键为准。"
            + "对象、笛卡尔积、分组三种执行方式都能用：只选实例不选子级时按规则取行，同一入参有多列子表行数不一致时"
            + "按最短的对齐；逐元素给位置的入参不能用规则，同时传会直接报错",
            example = "{\"tracks.time\":{\"rowCount\":20,\"orderByProperty\":\"create_time\",\"sort\":\"ASC\"},"
                    + "\"history.time\":{\"rowCount\":5}}")
    private Map<String, ActionTestListReadRuleParam> listReadRules;
}
