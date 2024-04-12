package com.aircas.ptr.foundry.common.base;

import com.aircas.ptr.foundry.common.util.Pagination;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DataResult<T> extends ApiResult {

    private T detail;

    private Pagination pagination;

    public static DataResult<?> empty() {
        return new DataResult<>(null);
    }

    public static <T> DataResult<T> ofData(T data) {
        return new DataResult<>(data);
    }

    public DataResult(T detail) {
        super(TRUE, "");
        this.detail = detail;
    }
}
