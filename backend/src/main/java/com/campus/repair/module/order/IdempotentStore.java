package com.campus.repair.module.order;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 进程内幂等缓存（clientToken 防重复提交）。
 * 说明：计划书原方案为 Redis SETNX，本环境无 Redis，按其降级策略改为进程内实现（单实例部署等价）。
 */
@Component
public class IdempotentStore {

    private record Entry(long expireAt, Object value) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private static final int MAX_SIZE = 10000;

    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(String key, java.time.Duration ttl, Supplier<T> loader) {
        Entry e = store.get(key);
        long now = System.currentTimeMillis();
        if (e != null && e.expireAt() > now) {
            return (T) e.value();
        }
        T value = loader.get();
        store.put(key, new Entry(now + ttl.toMillis(), value));
        if (store.size() > MAX_SIZE) {
            cleanup(now);
        }
        return value;
    }

    private void cleanup(long now) {
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext() && store.size() > MAX_SIZE / 2) {
            Map.Entry<String, Entry> next = it.next();
            if (next.getValue().expireAt() <= now) {
                it.remove();
            }
        }
    }
}
