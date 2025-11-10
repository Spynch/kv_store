package com.example.kvdb.api;

public interface KeyValueStore<V> {

    void put(String key, V value);

    V get(String key);


    void delete(String key);


    boolean containsKey(String key);
}
