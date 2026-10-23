package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 对外稳定错误，仅允许 code/message/retryable 三个字段。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ScanError(String code, String message, boolean retryable) {

    public static ScanError of(ScanErrorCode code) {
        return new ScanError(code.name(), code.message(), code.retryable());
    }

    public static ScanError of(ScanErrorCode code, String message) {
        return new ScanError(code.name(), message, code.retryable());
    }
}
