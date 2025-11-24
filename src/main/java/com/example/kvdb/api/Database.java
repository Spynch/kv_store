package com.example.kvdb.api;

public interface Database {

    void createTable(String name, TableOptions options);


    KeyValueStore<byte[]> openTable(String name);


    void dropTable(String name);

    java.util.List<String> listTables();

    void close();
}
