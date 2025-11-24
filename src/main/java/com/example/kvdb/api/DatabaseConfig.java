package com.example.kvdb.api;


public class DatabaseConfig {
    private final String dataPath;
    private final int cacheSize;

    public DatabaseConfig(String dataPath) {
        this(dataPath, 0);
    }

    public DatabaseConfig(String dataPath, int cacheSize) {
        if (dataPath == null) {
            throw new IllegalArgumentException("Data path must not be null");
        }
        this.dataPath = dataPath;
        this.cacheSize = cacheSize;
    }

    public String getDataPath() {
        return dataPath;
    }

    public int getCacheSize() {
        return cacheSize;
    }
}
