package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "属性元数据schema创建")
public class PropertyMetadataSchemaCreateParam extends OntologyIdentifierParam {


    @Schema(name = "parentId", description = "父节点id，为0表示当前节点为根节点", example = "0")
    @NotNull(message = "parentId is null")
    private Integer parentId;

    @Schema(name = "name", description = "名称", example = "信息等级")
    @NotBlank(message = "name is empty")
    private String name;

    @Schema(name = "enumValues", description = "元数据枚举值", example = "【高、中、低】")
    private List<String> enumValues;

    @Schema(name = "children", description = "子节点")
    @Valid
    private List<MetadataSchemaNode> children;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Accessors(chain = true)
    @Schema(description = "属性元数据节点")
    public static class MetadataSchemaNode {

        @Schema(hidden = true)
        private Integer parentId;

        @Schema(hidden = true)
        private String path;

        @Schema(name = "name", description = "名称", example = "信息等级")
        @NotBlank(message = "name is empty")
        private String name;

        @Schema(name = "children", description = "子节点")
        @Valid
        private List<MetadataSchemaNode> children;

        @Schema(name = "enumValues", description = "元数据枚举值", example = "【高、中、低】")
        private List<String> enumValues;

    }
}
