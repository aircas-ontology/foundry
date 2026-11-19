package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.MetadataValueTypeEnum;
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

    @Schema(name = "valueType", description = "取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值；不传默认 INPUT（旧字段 enumValues 非空时视为 ENUM）", example = "ENUM")
    private MetadataValueTypeEnum valueType;

    @Schema(name = "value", description = "取值内容字符串：ENUM 为逗号分隔候选项如\"高,中,低\"，RANGE 形如\"0,100\"，INPUT 可空", example = "高,中,低")
    private String value;

    @Deprecated
    @Schema(name = "enumValues", description = "已废弃，兼容旧导出文件：valueType 未传且本字段非空时视为 ENUM，值逗号拼接存入 value", example = "[\"高\",\"中\",\"低\"]")
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

        @Schema(name = "valueType", description = "取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值；不传默认 INPUT（旧字段 enumValues 非空时视为 ENUM）", example = "ENUM")
        private MetadataValueTypeEnum valueType;

        @Schema(name = "value", description = "取值内容字符串：ENUM 为逗号分隔候选项，RANGE 形如\"0,100\"，INPUT 可空", example = "高,中,低")
        private String value;

        @Deprecated
        @Schema(name = "enumValues", description = "已废弃，兼容旧导出文件：valueType 未传且本字段非空时视为 ENUM", example = "[\"高\",\"中\",\"低\"]")
        private List<String> enumValues;

    }
}
