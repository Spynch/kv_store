package com.example.kvdb.util;


public interface Serializer<T> {

    byte[] serialize(T value);

    T deserialize(byte[] bytes);
}
