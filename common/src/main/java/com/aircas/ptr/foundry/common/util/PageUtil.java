package com.aircas.ptr.foundry.common.util;

import com.github.pagehelper.PageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PageUtil {

    /**
     * 数据库分页
     *
     * @param method   a mapper method
     * @param pageNum  if pageNum <= 0, return everything
     * @param pageSize if pageSize <= 0, return everything
     * @return page
     */
    public static <E> Page<E> getPage(Supplier<List<E>> method, Integer pageNum, Integer pageSize) {
        if (null == pageNum || null == pageSize) {
            pageNum = 0;
            pageSize = 0;
        }
        com.github.pagehelper.Page<E> originPage = PageHelper.startPage(pageNum, pageSize);
        List<E> result = method.get();

        Page<E> resultPage = new Page<>();
        resultPage.setData(result);

        Pagination pagination = new Pagination();
        pagination.setTotal((int) originPage.getTotal());
        if (0 == originPage.getPageSize()) {
            pagination.setPage(pageNum);
            pagination.setSize(pageSize);
        } else {
            pagination.setPage(originPage.getPageNum());
            pagination.setSize(originPage.getPageSize());
        }
        resultPage.setPagination(pagination);
        return resultPage;
    }

    /**
     * 内存分页
     *
     * @param pagingList data
     * @param pageNum    if pageNum <= 0, return everything
     * @param pageSize   if pageSize <= 0, return everything
     * @return page
     */
    public static <E> Page<E> getPage(List<E> pagingList, Integer pageNum, Integer pageSize) {
        if (null == pageNum || null == pageSize) {
            pageNum = 0;
            pageSize = 0;
        }
        Pagination pagination = new Pagination();
        Page<E> page = new Page<>();
        if (0 >= pageNum || 0 >= pageSize) {
            pagination.setTotal(pagingList.size());
            pagination.setPage(0);
            pagination.setSize(0);
            page.setPagination(pagination);
            page.setData(pagingList);
        } else {
            pagination.setTotal(pagingList.size());
            pagination.setPage(pageNum);
            pagination.setSize(pageSize);
            page.setPagination(pagination);
            page.setData(subList(pagingList, pageNum, pageSize));
        }
        return page;
    }

    /**
     * 内存分页
     *
     * @param pagingList data
     * @param pageNum    if pageNum <= 0, return everything
     * @param pageSize   if pageSize <= 0, return everything
     * @param total
     * @return page
     */
    public static <E> Page<E> getPage(List<E> pagingList, Integer pageNum, Integer pageSize, Long total) {
        if (null == pageNum || null == pageSize) {
            pageNum = 0;
            pageSize = 0;
        }
        Pagination pagination = new Pagination();
        Page<E> page = new Page<>();
        pagination.setTotal(total.intValue());
        pagination.setPage(pageNum);
        pagination.setSize(pageSize);
        page.setPagination(pagination);
        page.setData(pagingList);
        return page;
    }

    private static <E> List<E> subList(List<E> list, int pageNum, int pageSize) {
        int size = list.size();

        List<E> result = new ArrayList<>();
        int idx = (pageNum - 1) * pageSize;
        int end = idx + pageSize;
        while (idx < size && idx < end) {
            result.add(list.get(idx));
            idx++;
        }

        return result;
    }
}