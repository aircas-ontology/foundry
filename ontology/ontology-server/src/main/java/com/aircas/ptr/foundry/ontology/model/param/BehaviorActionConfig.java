package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 行为节点的配置。
 * <p>按 ontology_action.api 绑定同空间内已发布的行为；实例主键默认全树共用一个，
 * 节点配置了 entityPrimaryKeyKey 时改用该黑板键的值，不同对象类型的节点因此能各用各的实例。
 * 一个行为同时读写多个对象类型的属性时（应用行为没有所属对象），用 objectInstanceKeys 按对象各配一个键。
 * 行为的外部输入参数按参数名从黑板取值，分组表行主键与关系对端主键这两个装不下的取值
 * 由黑板键供给（配置里写键名）。
 * 失败重试在单次执行内同步进行，重试会重复落库，只应对可重复执行的行为开启；
 * 超时无法安全中断已经开始的行为调用，不再支持，需要限制后续步骤请用限时节点。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树行为节点配置")
public class BehaviorActionConfig {

    @Schema(name = "actionApi", description = "绑定的行为 api", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "entityPrimaryKeyKey",
            description = "本节点执行时的实例主键用哪个黑板键；留空用执行请求里的 entityPrimaryKey（推荐），"
                    + "不同对象类型或需要不同实例的节点各配各的键", example = "satelliteId")
    private String entityPrimaryKeyKey;

    @Schema(name = "objectInstanceKeys",
            description = "对象实例键：对象类型标识 -> 黑板键名。绑定的行为同时读写多个对象类型的属性时必填，"
                    + "每个对象各取各的实例；只涉及一个对象时留空，沿用 entityPrimaryKeyKey 或执行请求里的 entityPrimaryKey")
    private Map<String, String> objectInstanceKeys;

    @Schema(name = "dataPrimaryKeyKey", description = "属性值-修改/删除行为定位分组表行用的黑板键名；行为不需要就留空", example = "extraRowId")
    private String dataPrimaryKeyKey;

    @Schema(name = "inputDataPrimaryKeyKeys",
            description = "应用行为入参按行读取时用：对象id -> (存储分组 -> 黑板键名)。"
                    + "没配的分组沿用执行请求里的默认行主键或原口径（最新一行）",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":{\"weapon_load\":\"weaponRowId\"}}")
    private Map<String, Map<String, String>> inputDataPrimaryKeyKeys;

    @Schema(name = "outputDataPrimaryKeyKeys",
            description = "应用行为出参按行写回时用：对象id -> (存储分组 -> 黑板键名)。"
                    + "没配的分组沿用执行请求里的默认行主键或原口径（新增一行）",
            example = "{\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":{\"duty_log\":\"dutyRowId\"}}")
    private Map<String, Map<String, String>> outputDataPrimaryKeyKeys;

    @Schema(name = "inputParamKeys",
            description = "应用行为按算子入参指定数据位置时用：入参名 -> {实例主键黑板键, 存储分组 -> 行主键黑板键}。"
                    + "同一对象的两个属性入参要读不同实例时用它，比 inputDataPrimaryKeyKeys 更细；"
                    + "命中时优先于该对象的实例键与行主键，不绑算子的行为配了会被拒绝",
            example = "{\"speed\":{\"entityPrimaryKeyKey\":\"speedSourceId\"},\"load\":{\"dataPrimaryKeyKeys\":{\"weapon_load\":\"loadRowId\"}}}")
    private Map<String, BehaviorActionParamKeys> inputParamKeys;

    @Schema(name = "outputParamKeys",
            description = "应用行为按出参指定写回位置时用：key -> {实例主键黑板键, 存储分组 -> 行主键黑板键}。"
                    + "key 认一条写属性绑定行，从高到低三种写法：目标属性的属性唯一标识 > 该行的出参字段名 > 该行的顶层算子出参名；"
                    + "同一算子出参的几个属性要写到不同实例时按字段名或属性唯一标识逐条点名，比 outputDataPrimaryKeyKeys 更细；"
                    + "命中时优先于该对象的实例键与行主键，不绑算子的行为配了会被拒绝",
            example = "{\"price\":{\"entityPrimaryKeyKey\":\"priceTargetId\"},\"duty\":{\"entityPrimaryKeyKey\":\"dutyTargetId\",\"dataPrimaryKeyKeys\":{\"duty_log\":\"dutyRowId\"}}}")
    private Map<String, BehaviorActionParamKeys> outputParamKeys;

    @Schema(name = "linkEntityPrimaryKeyKey", description = "实例关系行为对端实体主键用的黑板键名；行为不需要就留空", example = "targetId")
    private String linkEntityPrimaryKeyKey;

    @Schema(name = "timeoutSeconds", description = "已不支持：单轮同步执行无法中断已经开始的行为，配正数会被拒绝；0 或空表示不限制；限制后续步骤请改用限时节点")
    private Integer timeoutSeconds;

    @Schema(name = "retryTimes", description = "失败重试次数，0 或空表示不重试；重试会重复落库，只应对可重复执行的行为开启")
    private Integer retryTimes;
}
