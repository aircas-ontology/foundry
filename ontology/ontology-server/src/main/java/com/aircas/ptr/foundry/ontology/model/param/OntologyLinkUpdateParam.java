package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkMappingEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
@ApiModel(description = "link create param")
public class OntologyLinkUpdateParam extends IdentifierParam{

    /**
     * link的名称
     */
    @ApiModelProperty(name = "name", required = true, value = "本体关系名称")
    @NotBlank(message = "link name is empty")
    private String name;



    /**
     * 1: 1对1
     * 2: 1对多
     * 3: 多对1
     * 4: 多对多
     */
    @ApiModelProperty(name = "mapping", required = true, value = "本体映射比")
    @NotNull(message = "mapping is empty")
    private OntologyLinkMappingEnum mapping;

}
