package com.example.kvdb.spark.source;

import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.types.StructType;


public class ArkashaScanBuilder implements ScanBuilder {

    private final String path;
    private final String tableName;
    private final StructType schema;

    public ArkashaScanBuilder(String path, String tableName, StructType schema) {
        this.path = path;
        this.tableName = tableName;
        this.schema = schema;
    }

    @Override
    public Scan build() {
        return new ArkashaScan(path, tableName, schema);
    }
}
