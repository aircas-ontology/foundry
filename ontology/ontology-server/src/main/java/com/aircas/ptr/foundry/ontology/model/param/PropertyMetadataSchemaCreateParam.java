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
@ApiModel(description = "属性元数据schema创建")
public class PropertyMetadataSchemaCreateParam extends OntologyIdentifierParam {


    @ApiModelProperty(name = "parentId", value = "父节点id，为0表示当前节点为根节点", example = "0")
    @NotNull(message = "parentId is null")
    private Integer parentId;

    @ApiModelProperty(name = "name", value = "名称", example = "信息等级")
    @NotBlank(message = "name is empty")
    private String name;

    @ApiModelProperty(name = "enumValues", value = "元数据枚举值", example = "【高、中、低】")
    private List<String> enumValues;

    @ApiModelProperty(name = "children", value = "子节点")
    @Valid
    private List<MetadataSchemaNode> children;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Accessors(chain = true)
    @ApiModel(description = "属性元数据节点")
    public static class MetadataSchemaNode {

        @ApiModelProperty(hidden = true)
        private Integer parentId;

        @ApiModelProperty(hidden = true)
        private String path;

        @ApiModelProperty(name = "name", value = "名称", example = "信息等级")
        @NotBlank(message = "name is empty")
        private String name;

        @ApiModelProperty(name = "children", value = "子节点")
        @Valid
        private List<MetadataSchemaNode> children;

        @ApiModelProperty(name = "enumValues", value = "元数据枚举值", example = "【高、中、低】")
        private List<String> enumValues;

    }
}
