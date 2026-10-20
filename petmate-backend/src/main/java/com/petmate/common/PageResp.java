package com.petmate.common;

import java.util.List;

/**
 * 统一分页响应（PRD §7.1）
 *  { "list": [...], "total": 100, "page": 1, "size": 10 }
 */
public class PageResp<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;

    public PageResp() {}

    public PageResp(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResp<T> of(List<T> list, long total, int page, int size) {
        return new PageResp<>(list, total, page, size);
    }

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
