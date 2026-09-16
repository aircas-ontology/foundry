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
@Schema(description = "实体属性删除请求")
public class EntityPropertyDeleteParam extends OntologyIdentifierParam {

    @Schema(name = "entityPrimaryKey", description = "实体主键")
    @NotNull(message = "实体主键为空")
    private Object entityPrimaryKey;

    @Schema(name = "storageGroup", description = "属性存储分组名称", example = "main")
    @NotBlank(message = "storageGroup is empty")
    private String storageGroup;

    @Schema(name = "dataPrimaryKey", description = "属性数据主键")
    @NotNull(message = "dataPrimaryKey is empty")
    private Object dataPrimaryKey;


}
