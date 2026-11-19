package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.MetadataValueTypeEnum;
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

    @Schema(name = "parentId", description = "新父节点 id：不传保持原父级；传了则移动节点（子孙路径同步重写）。0 表示根节点（仅节点本身已是根时允许）；不能移动到自身或其子孙节点下", example = "12")
    private Integer parentId;


    @Schema(name = "valueType", description = "取值类型：INPUT 输入值 / ENUM 枚举值 / RANGE 范围值；不传保持原类型（旧字段 enumValues 非空时视为 ENUM）", example = "ENUM")
    private MetadataValueTypeEnum valueType;

    @Schema(name = "value", description = "取值内容字符串：ENUM 为逗号分隔候选项如\"高,中,低\"，RANGE 形如\"0,100\"，INPUT 可空；传 valueType 时以本字段为准（null 清空值）", example = "高,中,低")
    private String value;

    @Deprecated
    @Schema(name = "enumValues", description = "已废弃，兼容旧前端/旧导出文件：valueType 未传且本字段非空时视为 ENUM，值逗号拼接存入 value", example = "[\"一级\",\"二级\",\"三级\"]")
    private List<String> enumValues;
}
