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
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(value = "实体id查询结果")
public class EntityIdsQueryVO {

    @ApiModelProperty(name = "ontologyUniqueIdentifier", value = "本体唯一标识", example = "abcd")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(name = "entityList", value = "实体属性信息列表")
    private List<EntityInfoVO> entityList;
}
