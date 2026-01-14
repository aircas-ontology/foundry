package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "link create param")
public class OntologyLinkCreateParam {

    /**
     * link的名称
     */
    @ApiModelProperty(name = "name", required = true, value = "本体关系名称")
    @NotBlank(message = "link name is empty")
    private String name;

    /**
     * 开始本体unique identifier
     */
    @ApiModelProperty(name = "ontologyUniqueIdentifierFrom", required = true, value = "开始本体uniq id")
    @NotBlank(message = "ontologyUniqueIdentifierFrom name is empty")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierFrom;

    /**
     * 结束本体unique identifier
     */
    @ApiModelProperty(name = "ontologyUniqueIdentifierTo", required = true, value = "结束本体uniq id")
    @NotBlank(message = "ontologyUniqueIdentifierTo name is empty")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierTo;


    @ApiModelProperty(name = "type", required = true, value = "关系类型")
    @NotNull(message = "type is empty")
    private OntologyLinkTypeEnum type;

}
