package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.MetadataValueTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "对象属性元数据VO")
public class PropertyMetadataSchemaVO {

    @Schema(name = "schemaId", description = "schemaId", example = "1")
    private Integer schemaId;

    @Schema(name = "name", description = "名称", example = "等级")
    private String name;

    @Schema(name = "valueType", description = "取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值")
    private MetadataValueTypeEnum valueType;

    @Schema(name = "value", description = "取值内容字符串：ENUM 为逗号分隔候选项，RANGE 形如\"0,100\"，INPUT 可空")
    private String value;

    @Schema(name = "children", description = "子节点")
    private List<PropertyMetadataSchemaVO> children;

}
