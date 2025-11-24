package com.example.kvdb.core;

import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.engine.ArkashaWriteAheadLog;
import com.example.kvdb.engine.ArkashaMetrics;
import com.example.kvdb.api.WriteAheadLog.LogRecord;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class InMemoryKeyValueStore implements KeyValueStore<byte[]> {
    private final String tableName;
    private final TableOptions options;
    private final ArkashaWriteAheadLog wal;
    private final ArkashaMetrics metrics;
    private final Map<String, byte[]> map;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public InMemoryKeyValueStore(String tableName, TableOptions options, ArkashaWriteAheadLog wal, ArkashaMetrics metrics) {
        this.tableName = tableName;
        this.options = options;
        this.wal = wal;
        this.metrics = metrics;
        this.map = new HashMap<>();
    }

    public TableOptions getOptions() {
        return options;
    }

    public List<String> keys() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(map.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearData() {
        lock.writeLock().lock();
        try {
            map.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void put(String key, byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        if (options.getMaxValueSize() >= 0 && value.length > options.getMaxValueSize()) {
            throw new IllegalArgumentException("Размер значения превышает допустимый лимит");
        }
        lock.writeLock().lock();
        try {
            byte[] oldValue = map.put(key, value);
            if (oldValue == null) {
                metrics.incrementKeyCount();
                metrics.addDataSize(key.getBytes(StandardCharsets.UTF_8).length + value.length);
            } else {
                long diff = value.length - oldValue.length;
                metrics.addDataSize(diff);
            }
            if (options.isWalEnabled() && wal != null && wal.isActive()) {
                wal.append(LogRecord.put(tableName, key, value));
                if (options.isFsync()) {
                    wal.sync();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public byte[] get(String key) {
        lock.readLock().lock();
        try {
            return map.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String key) {
        lock.writeLock().lock();
        try {
            byte[] removed = map.remove(key);
            if (removed != null) {
                metrics.decrementKeyCount();
                metrics.addDataSize(- (key.getBytes(StandardCharsets.UTF_8).length + removed.length));
                if (options.isWalEnabled() && wal != null && wal.isActive()) {
                    wal.append(LogRecord.delete(tableName, key));
                    if (options.isFsync()) {
                        wal.sync();
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean containsKey(String key) {
        lock.readLock().lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }
}
