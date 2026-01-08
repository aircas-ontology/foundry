package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(value = "实体关系VO")
public class EntityLinksVO {

    @ApiModelProperty(name = "ontologyUniqueIdentifier", value = "本体id", example = "abc")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(name = "entityPrimaryKey", value = "实体主键值", example = "123")
    private Object entityPrimaryKey;

    @ApiModelProperty(name = "links", value = "当前实体所有关联关系")
    private List<EntityLinkPropertyVO> links;
}
