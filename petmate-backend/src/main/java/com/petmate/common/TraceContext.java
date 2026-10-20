package com.petmate.common;

/**
 * 链路 traceId (PRD §7.1) — ThreadLocal 保存,响应时回填
 */
public final class TraceContext {
    private static final ThreadLocal<String> CTX = new ThreadLocal<>();

    private TraceContext() {}

    public static void set(String traceId) { CTX.set(traceId); }
    public static String get() { return CTX.get(); }
    public static void clear() { CTX.remove(); }
}
