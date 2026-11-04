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

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为测试请求")
public class ActionTestParam {

    @Schema(name = "ontologySpaceId", description = "对象空间id", example = "156", required = true)
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "actionApi", description = "行为api", example = "syncSpeed", required = true)
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(name = "entityPrimaryKey", description = "本实体主键：属性值行为定位实体、算子类行为取属性值都靠它", example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "dataPrimaryKey", description = "分组表行的主键：属性值-修改/删除必填", example = "1")
    private Object dataPrimaryKey;

    @Schema(name = "linkEntityPrimaryKey", description = "关系对端实体主键：实例关系行为必填", example = "2")
    private Object linkEntityPrimaryKey;

    @Schema(name = "params", description = "来源为外部输入的参数取值（参数名 -> 值），固定值不在此传")
    private Map<String, Object> params;
}
