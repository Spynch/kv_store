package com.example.kvdb.api;


public interface PersistenceManager {

    void flush();


    void load();
}
