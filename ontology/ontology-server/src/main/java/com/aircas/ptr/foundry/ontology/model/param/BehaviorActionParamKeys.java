package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 行为节点按算子参数指定的数据位置，值全部来自黑板键。
 * <p>与按对象指定的 inputDataPrimaryKeyKeys 相比，这里精确到一个参数：同一对象的两个属性入参读不同实例、
 * 两个写属性的出参写不同实例时，只有按参数才分得开。两个子字段都可以只配其一，都不配等于该参数不指定。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树行为节点按算子参数指定的数据位置")
public class BehaviorActionParamKeys {

    @Schema(name = "entityPrimaryKeyKey",
            description = "该参数用的实例主键黑板键名；留空沿用节点/执行请求里该对象的实例主键",
            example = "speedSourceId")
    private String entityPrimaryKeyKey;

    @Schema(name = "dataPrimaryKeyKeys",
            description = "存储分组 -> 行主键黑板键名；只对该参数涉及的分组生效，main 分组忽略",
            example = "{\"weapon_load\":\"speedRowId\"}")
    private Map<String, String> dataPrimaryKeyKeys;
}
