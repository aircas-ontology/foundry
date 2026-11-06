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
 * 单轮引擎里行为是真实落库的，重试等于重复写一条数据，所以超时与重试只做保存期的正数校验，执行不生效。</p>
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

    @Schema(name = "timeoutSeconds", description = "超时时长（秒），0 或空表示不限制")
    private Integer timeoutSeconds;

    @Schema(name = "retryTimes", description = "失败重试次数，0 或空表示不重试")
    private Integer retryTimes;
}
