package com.petmate.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等键去重（PRD §7.4）
 * 无 Redis,使用 JVM 内存 ConcurrentHashMap + 60s TTL 实现。
 * 写操作传 Idempotency-Key 时,60s 内同 key 重复提交将直接返回上次的响应。
 */
@Service
public class IdempotencyService {
    private static final long TTL_MS = 60_000L;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    private static class Entry {
        long ts;
        Object response;
        Entry(long ts, Object response) { this.ts = ts; this.response = response; }
    }

    /** 抢占幂等键;若已存在则返回 false (表示重复请求) */
    public synchronized boolean tryClaim(String key) {
        long now = System.currentTimeMillis();
        Entry e = store.get(key);
        if (e != null && now - e.ts < TTL_MS) {
            return false;
        }
        store.put(key, new Entry(now, null));
        return true;
    }

    public synchronized void record(String key, Object response) {
        store.put(key, new Entry(System.currentTimeMillis(), response));
    }

    public synchronized Object getCachedResponse(String key) {
        Entry e = store.get(key);
        if (e == null) return null;
        if (System.currentTimeMillis() - e.ts > TTL_MS) {
            store.remove(key);
            return null;
        }
        return e.response;
    }
}
