package com.aircas.ptr.foundry.common.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ApiResult {

    private String result = TRUE;

    private String rejectReason = "";

    private String thumbPath = "";

    private long strategyVersionNumber;

    public static final String TRUE = "true";
    public static final String FALSE = "false";

    public ApiResult(String aTrue, String rejectReason) {
        this.result = aTrue;
        this.rejectReason = rejectReason;
    }

    public ApiResult(String aTrue, long strategyVersionNumber) {
        this.result = aTrue;
        this.strategyVersionNumber = strategyVersionNumber;
    }

    public static ApiResult success(String thumbPath) {
        return new ApiResult(TRUE, thumbPath);
    }

    public static ApiResult success(long strategyVersionNumber) {
        return new ApiResult(TRUE, strategyVersionNumber);
    }

    public static ApiResult success() {
        return new ApiResult(TRUE, "");
    }

    public static ApiResult fail(String msg) {
        return new ApiResult(FALSE, msg);
    }

    public boolean succeed() {
        return TRUE.equals(result);
    }
}
