package com.example.kvdb.api;

public interface KeyValueStore<V> {

    void put(String key, V value);

    V get(String key);


    void delete(String key);


    boolean containsKey(String key);

    default void putAll(java.util.Map<String, V> items) {
        if (items == null) return;
        for (java.util.Map.Entry<String, V> e : items.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    default java.util.Map<String, V> getAll(java.util.List<String> keys) {
        java.util.Map<String, V> out = new java.util.HashMap<>();
        if (keys == null) return out;
        for (String k : keys) {
            V v = get(k);
            if (v != null) out.put(k, v);
        }
        return out;
    }

    default void deleteAll(java.util.List<String> keys) {
        if (keys == null) return;
        for (String k : keys) delete(k);
    }

}
