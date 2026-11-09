package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 行为节点的配置。
 * <p>按 ontology_action.api 绑定同空间内已发布的行为；一次树执行只有一个实例主键，全树共用，
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

    @Schema(name = "dataPrimaryKeyKey", description = "属性值-修改/删除行为定位分组表行用的黑板键名；行为不需要就留空", example = "extraRowId")
    private String dataPrimaryKeyKey;

    @Schema(name = "linkEntityPrimaryKeyKey", description = "实例关系行为对端实体主键用的黑板键名；行为不需要就留空", example = "targetId")
    private String linkEntityPrimaryKeyKey;

    @Schema(name = "timeoutSeconds", description = "已不支持：单轮同步执行无法中断已经开始的行为，配正数会被拒绝；0 或空表示不限制；限制后续步骤请改用限时节点")
    private Integer timeoutSeconds;

    @Schema(name = "retryTimes", description = "失败重试次数，0 或空表示不重试；重试会重复落库，只应对可重复执行的行为开启")
    private Integer retryTimes;
}
