package com.example.kvdb.spark.source;

import org.apache.spark.sql.connector.read.Batch;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.PartitionReaderFactory;
import org.apache.spark.sql.types.StructType;


public class ArkashaBatch implements Batch {

    private final String path;
    private final String tableName;
    private final StructType schema;

    public ArkashaBatch(String path, String tableName, StructType schema) {
        this.path = path;
        this.tableName = tableName;
        this.schema = schema;
    }

    @Override
    public InputPartition[] planInputPartitions() {
        return new InputPartition[] { new ArkashaInputPartition(path, tableName) };
    }

    @Override
    public PartitionReaderFactory createReaderFactory() {
        return new ArkashaPartitionReaderFactory(schema);
    }
}
