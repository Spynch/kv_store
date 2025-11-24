package com.example.kvdb.api;


public interface StorageEngine extends Database {

    PersistenceManager getPersistenceManager();

    WriteAheadLog getWriteAheadLog();


    DatabaseConfig getConfig();

    Metrics getMetrics();
}
