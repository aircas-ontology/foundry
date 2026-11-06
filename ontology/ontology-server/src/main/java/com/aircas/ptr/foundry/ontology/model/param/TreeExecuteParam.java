package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 行为树执行请求。
 * <p>一次树执行只带一个实例主键，全树共用（解释二）；黑板键值由 inputs 覆盖变量默认值。
 * 一棵树里的行为可以涉及多个对象类型，但它们都按这同一个主键定位各自的实例。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行请求")
public class TreeExecuteParam {

    @Schema(name = "ontologySpaceId", description = "行为树所属的对象空间id；行为api只在空间内唯一，同名树跨空间靠它区分",
            example = "156", required = true)
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "treeApi", description = "行为树api", example = "tree.satellite.watch", required = true)
    @NotBlank(message = "treeApi is empty")
    private String treeApi;

    @Schema(name = "entityPrimaryKey", description = "本次执行共用的实例主键", example = "1", required = true)
    @NotNull(message = "entityPrimaryKey is null")
    private Object entityPrimaryKey;

    @Schema(name = "inputs", description = "黑板键的初始取值（黑板键名 -> 值），只允许覆盖已声明且未绑定对象属性的键")
    private Map<String, Object> inputs;
}
