package com.aircas.ptr.foundry.common.util;

import java.util.List;

public class Page<E> {

    private Pagination pagination;

    private List<E> data;

    public Page() { // NOSONAR
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    public List<E> getData() {
        return data;
    }

    public void setData(List<E> data) {
        this.data = data;
    }
}
