package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "link create param")
public class OntologyLinkCreateParam {

    /**
     * link的名称
     */
    @Schema(name = "name", required = true, description = "本体关系名称")
    @NotBlank(message = "link name is empty")
    private String name;

    /**
     * 开始本体unique identifier
     */
    @Schema(name = "ontologyUniqueIdentifierFrom", required = true, description = "开始本体uniq id")
    @NotBlank(message = "ontologyUniqueIdentifierFrom name is empty")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierFrom;

    /**
     * 结束本体unique identifier
     */
    @Schema(name = "ontologyUniqueIdentifierTo", required = true, description = "结束本体uniq id")
    @NotBlank(message = "ontologyUniqueIdentifierTo name is empty")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierTo;


    @Schema(name = "type", required = true, description = "关系类型")
    @NotNull(message = "type is empty")
    private OntologyLinkTypeEnum type;

    /**
     * 关系分类id（ontology_link_category.id），关系必须归属到关系分类树，必填
     */
    @Schema(name = "categoryId", required = true, description = "关系分类id（ontology_link_category.id），必填", example = "1")
    @NotNull(message = "categoryId is empty")
    private Integer categoryId;

}
