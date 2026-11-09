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

    @Schema(name = "linkEntityPrimaryKeyKey", description = "实例关系行为对端实体主键用的黑板键名；行为不需要就留空", example = "targetId")
    private String linkEntityPrimaryKeyKey;

    @Schema(name = "timeoutSeconds", description = "已不支持：单轮同步执行无法中断已经开始的行为，配正数会被拒绝；0 或空表示不限制；限制后续步骤请改用限时节点")
    private Integer timeoutSeconds;

    @Schema(name = "retryTimes", description = "失败重试次数，0 或空表示不重试；重试会重复落库，只应对可重复执行的行为开启")
    private Integer retryTimes;
}
