package com.example.kvdb.core;

import com.example.kvdb.api.Table;
import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.util.Serializer;


public class TableImpl<T> implements Table<T> {
    private final KeyValueStore<byte[]> rawStore;
    private final Serializer<T> serializer;

    public TableImpl(KeyValueStore<byte[]> rawStore, Serializer<T> serializer) {
        this.rawStore = rawStore;
        this.serializer = serializer;
    }

    @Override
    public void put(String key, T value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        byte[] data = serializer.serialize(value);
        rawStore.put(key, data);
    }

    @Override
    public T get(String key) {
        byte[] data = rawStore.get(key);
        if (data == null) {
            return null;
        }
        return serializer.deserialize(data);
    }

    @Override
    public void delete(String key) {
        rawStore.delete(key);
    }

    @Override
    public boolean containsKey(String key) {
        return rawStore.containsKey(key);
    }
}
