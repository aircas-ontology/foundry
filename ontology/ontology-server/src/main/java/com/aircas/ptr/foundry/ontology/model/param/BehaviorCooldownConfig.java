package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 冷却节点的配置。
 * <p>冷却状态按「树 + 节点 + 执行实例主键」持久化，跨请求、跨实例有效；距上次执行未满 intervalSeconds 时
 * 不执行子节点，直接上报 hitResult。计时从子节点开始执行算起，子节点失败也不退还冷却时间。
 * 实例主键默认取执行请求里的 entityPrimaryKey；子树里的行为节点用节点级实例键定位不同实例时，
 * 这里要配同一个黑板键，否则不同实例会共用一条冷却记录互相误拦。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树冷却节点配置")
public class BehaviorCooldownConfig {

    @Schema(name = "intervalSeconds", description = "冷却时长（秒），必须大于 0")
    private Integer intervalSeconds;

    @Schema(name = "hitResult", description = "处于冷却期时本节点上报的状态，只能是 SUCCESS 或 FAILURE", example = "FAILURE")
    private BehaviorStateEnum hitResult;

    @Schema(name = "entityPrimaryKeyKey",
            description = "冷却按哪个实例计时：填黑板键名，与子树里行为节点的实例主键键名保持一致；"
                    + "留空用执行请求里的 entityPrimaryKey，为空则整棵树共享一份冷却", example = "satelliteId")
    private String entityPrimaryKeyKey;
}
