package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "本体分类体系创建")
public class OntologyCategoryCreateParam extends OntologySpaceIdParam {

    @ApiModelProperty(name = "parentId", value = "父节点id，为0表示当前节点为根节点(新创建属性体系)", example = "0")
    @NotNull(message = "parentId is null")
    private Integer parentId;

    @ApiModelProperty(name = "name", value = "分类名称", example = "舰船")
    @NotBlank(message = "name is empty")
    private String name;

    @ApiModelProperty(name = "children", value = "子节点")
    @Valid
    private List<CategoryNode> children;

}