package com.example.kvdb.engine;

import com.example.kvdb.api.Metrics;
import java.util.concurrent.atomic.AtomicLong;


public class ArkashaMetrics implements Metrics {
    private final AtomicLong totalKeys = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);

    @Override
    public long getKeyCount() {
        return totalKeys.get();
    }

    @Override
    public long getDataSize() {
        return totalBytes.get();
    }

    public void incrementKeyCount() {
        totalKeys.incrementAndGet();
    }

    public void decrementKeyCount() {
        totalKeys.decrementAndGet();
    }

    public void decrementKeyCount(long n) {
        totalKeys.addAndGet(-n);
    }

    public void addDataSize(long bytes) {
        totalBytes.addAndGet(bytes);
    }
}
