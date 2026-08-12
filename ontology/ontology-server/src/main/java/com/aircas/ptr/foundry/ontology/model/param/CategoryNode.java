package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "分类节点")
public class CategoryNode {

    @ApiModelProperty(hidden = true)
    private Integer parentId;

    @ApiModelProperty(hidden = true)
    private String path;

    @ApiModelProperty(name = "name", value = "分类名称", example = "航空母舰")
    @NotBlank(message = "name is empty")
    private String name;

    @ApiModelProperty(name = "children", value = "子节点")
    private List<CategoryNode> children;

}
