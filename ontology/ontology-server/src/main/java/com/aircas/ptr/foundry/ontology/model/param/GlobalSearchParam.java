package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "本体全局检索参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GlobalSearchParam {

    @Schema(name = "keyword", description = "查询内容，匹配所有字段", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "查询内容不能为空")
    private String keyword;

    @Schema(name = "searchType",
            description = "检索类型：1=空间（含主题） 2=本体对象 3=关系 4=属性 5=实例；不传则五类全查")
    @Min(value = 1, message = "检索类型取值范围为 1-5（1=空间 2=本体对象 3=关系 4=属性 5=实例）")
    @Max(value = 5, message = "检索类型取值范围为 1-5（1=空间 2=本体对象 3=关系 4=属性 5=实例）")
    private Integer searchType;

    @Schema(name = "size", description = "返回条数上限，缺省 100")
    private Integer size;
}
