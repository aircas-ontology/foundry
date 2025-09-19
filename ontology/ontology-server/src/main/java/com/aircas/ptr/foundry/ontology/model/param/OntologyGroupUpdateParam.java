package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:14
 */

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "OntologyGroupUpdateParam")
public class OntologyGroupUpdateParam extends GroupIdParam {


     /**
      * 本体分组名称
      */
     @ApiModelProperty(name = "groupName")
     @NotBlank(message = "groupName is not empty")
     private String groupName;

}
