package com.example.kvdb.spark.source;

import org.apache.spark.sql.connector.read.Batch;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.types.StructType;


public class ArkashaScan implements Scan {

    private final String path;
    private final String tableName;
    private final StructType schema;

    public ArkashaScan(String path, String tableName, StructType schema) {
        this.path = path;
        this.tableName = tableName;
        this.schema = schema;
    }

    @Override
    public StructType readSchema() {
        return schema;
    }

    @Override
    public Batch toBatch() {
        return new ArkashaBatch(path, tableName, schema);
    }

    @Override
    public String description() {
        return "ArkashaScan(path=" + path + ", table=" + tableName + ")";
    }
}
