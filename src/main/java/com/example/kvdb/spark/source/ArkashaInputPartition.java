package com.example.kvdb.spark.source;

import org.apache.spark.sql.connector.read.InputPartition;

import java.io.Serializable;

public class ArkashaInputPartition implements InputPartition, Serializable {

    private final String path;
    private final String tableName;

    public ArkashaInputPartition(String path, String tableName) {
        this.path = path;
        this.tableName = tableName;
    }

    public String getPath() {
        return path;
    }

    public String getTableName() {
        return tableName;
    }
}
