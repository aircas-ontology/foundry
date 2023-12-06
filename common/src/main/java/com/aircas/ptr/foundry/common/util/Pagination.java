package com.aircas.ptr.foundry.common.util;

public class Pagination {

    private int page;

    private int total;

    private int size;

    public Pagination() { // NOSONAR
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
