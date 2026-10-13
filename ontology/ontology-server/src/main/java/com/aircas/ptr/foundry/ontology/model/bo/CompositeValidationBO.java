package com.aircas.ptr.foundry.ontology.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 函数编排校验结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CompositeValidationBO", description = "函数编排校验结果")
public class CompositeValidationBO {

    @Schema(name = "valid", description = "是否校验通过")
    private boolean valid;

    @Schema(name = "errors", description = "校验错误信息")
    private List<String> errors;

    @Schema(name = "warnings", description = "校验告警信息")
    private List<String> warnings;

    public static CompositeValidationBO ok() {
        return CompositeValidationBO.builder().valid(true).errors(new ArrayList<>()).warnings(new ArrayList<>()).build();
    }

    public static CompositeValidationBO fail(List<String> errors) {
        return CompositeValidationBO.builder().valid(errors == null || errors.isEmpty()).errors(errors == null ? new ArrayList<>() : errors).warnings(new ArrayList<>()).build();
    }
}
