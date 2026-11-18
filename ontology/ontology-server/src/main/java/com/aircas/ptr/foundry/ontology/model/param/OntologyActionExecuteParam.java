package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "对象行为执行请求")
public class OntologyActionExecuteParam {


    @Schema(name = "ontologyUniqueIdentifier", description = "对象id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;


    @Schema(name = "actionApi", description = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;


    @Schema(name = "entityPrimaryKey", description = "实例主键：仅基础函数行为可用，传了只查该实例、不传按对象全量查；"
            + "基础函数选了多个对象时，这个实例归属 ontologyUniqueIdentifier 指定的那个对象，其余对象仍按全量查。"
            + "其他类别的行为不需要传（按对象下全部实例执行）", example = "1")
    private Object entityPrimaryKey;


    @Schema(name = "shardIndex", description = "shardIndex", example = "1")
    private Integer shardIndex;

    @Schema(name = "shardTotal", description = "shardTotal", example = "10")
    private Integer shardTotal;


}
