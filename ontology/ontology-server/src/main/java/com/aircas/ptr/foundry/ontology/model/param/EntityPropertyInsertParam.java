package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "实体属性插入请求")
public class EntityPropertyInsertParam extends OntologyIdentifierParam {

    @Schema(name = "entityPrimaryKey", description = "实体主键")
    @NotNull(message = "实体主键为空")
    private Object entityPrimaryKey;

    @Schema(name = "entityProperties", description = "实体属性列表")
    @Size(min = 1, message = "实体属性列表为空")
    @Valid
    private List<EntityCreateParam.EntityProperty> entityProperties;


}
