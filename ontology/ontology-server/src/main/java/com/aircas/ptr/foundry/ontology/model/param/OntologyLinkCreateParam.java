package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyLinkMappingEnum;
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

    /**
     * 开始本体的某个属性，作为连接键
     */
    @ApiModelProperty(name = "propertyUniqueIdentifierFrom",  value = "开始本体的某个属性，作为连接键")
    @NotBlank(message = "propertyUniqueIdentifierFrom is empty")
    private String propertyUniqueIdentifierFrom;

    /**
     * 结束本体的某个属性，作为连接键
     */
    @ApiModelProperty(name = "propertyUniqueIdentifierTo",  value = "结束本体的某个属性，作为连接键")
    @NotBlank(message = "propertyUniqueIdentifierTo is empty")
    private String propertyUniqueIdentifierTo;


    /**
     * 1: 1对1
     * 2: 1对多
     * 3: 多对1
     * 4: 多对多
     */
    @ApiModelProperty(name = "mapping", required = true, value = "本体映射关系")
    @NotNull(message = "mapping is empty")
    private OntologyLinkMappingEnum mapping;

}
