package com.example.kvdb.api;


public interface Distributed {

    void replicate(String tableName, String key, byte[] value);

    void addNode(String nodeId);
}
