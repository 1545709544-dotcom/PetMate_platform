package com.petmate.common;

/**
 * 统一响应包装（PRD §7.1）
 *  { "code": 0, "msg": "ok", "data": {...}, "traceId": "..." }
 */
public class ApiResp<T> {
    private int code;
    private String msg;
    private T data;
    private String traceId;

    public ApiResp() {}

    public ApiResp(int code, String msg, T data, String traceId) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResp<T> ok(T data) {
        return new ApiResp<>(0, "ok", data, TraceContext.get());
    }

    public static <T> ApiResp<T> ok() {
        return ok(null);
    }

    public static <T> ApiResp<T> fail(int code, String msg) {
        return new ApiResp<>(code, msg, null, TraceContext.get());
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
