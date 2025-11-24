package com.example.kvdb.config;

import com.example.kvdb.model.User;
import com.example.kvdb.util.Serializer;
import com.example.kvdb.util.TlvUserSerializer;

import java.util.HashMap;
import java.util.Map;

public final class DefaultSerializers {
    private static final Map<Class<?>, Serializer<?>> MAP = new HashMap<>();

    static {
        // По умолчанию для User — TLV
        MAP.put(User.class, new TlvUserSerializer());
    }

    private DefaultSerializers() {}

    @SuppressWarnings("unchecked")
    public static <T> Serializer<T> lookup(Class<T> type) {
        return (Serializer<T>) MAP.get(type);
    }
}
