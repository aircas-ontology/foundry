package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "属性元数据schema更新")
public class PropertyMetadataSchemaUpdateParam extends PropertyMetadataSchemaDeleteParam {

    @Schema(name = "name", description = "元数据schema名称", example = "等级")
    @NotBlank(message = "元数据schema名称不能为空")
    private String name;


    @Schema(name = "enumValues", description = "枚举值", example = "【一级,二级,三级,四级】")
    private List<String> enumValues;
}
