package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "函数版本操作参数")
public class FunctionVersionParam {
    @NotBlank(message = "functionApi is empty")
    private String functionApi;
    private Integer versionNo;
    private String code;
    private String referenceName;
    private String changeLog;
}
