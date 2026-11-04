package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "实体行为执行请求")
public class EntityActionExecuteParam extends EntityQueryParam {


    @Schema(name = "actionApi", description = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;

    @Schema(name = "dataPrimaryKey",
            description = "存储分组行的数据主键；属性值行为的修改/删除需要它定位到具体一行，"
                    + "main 分组可不传（等同实体主键），绑算子的行为忽略该字段", example = "123")
    private Object dataPrimaryKey;

    @Schema(name = "linkedOntologyUniqueIdentifier", description = "关联对象id", example = "123")
    private String linkedOntologyUniqueIdentifier;

    @Schema(name = "linkedEntityPrimaryKey", description = "关联对象id下某实体主键值", example = "123")
    private Object linkedEntityPrimaryKey;
}
