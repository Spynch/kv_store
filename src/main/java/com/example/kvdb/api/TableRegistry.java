package com.example.kvdb.api;

import com.example.kvdb.util.Serializer;

public interface TableRegistry {

    <T> void register(String tableName, Class<T> type, Serializer<T> serializer);

    <T> Table<T> openTable(String tableName, Class<T> type);
}
