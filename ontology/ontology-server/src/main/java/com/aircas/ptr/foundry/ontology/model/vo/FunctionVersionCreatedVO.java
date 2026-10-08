package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "新建函数版本结果")
public class FunctionVersionCreatedVO {

    private Long functionVersionId;

    @Schema(example = "1.2.31")
    private String version;
}
