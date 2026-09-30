package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(name = "size", description = "返回条数上限，缺省 100")
    private Integer size;
}
