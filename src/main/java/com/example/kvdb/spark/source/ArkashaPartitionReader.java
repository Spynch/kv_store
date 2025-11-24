package com.example.kvdb.spark.source;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.sql.connector.read.PartitionReaderFactory;
import org.apache.spark.sql.types.StructType;

import java.io.Serializable;


class ArkashaPartitionReaderFactory implements PartitionReaderFactory, Serializable {

    private final StructType schema;

    public ArkashaPartitionReaderFactory(StructType schema) {
        this.schema = schema;
    }

    @Override
    public PartitionReader<InternalRow> createReader(InputPartition partition) {
        ArkashaInputPartition p = (ArkashaInputPartition) partition;
        return new ArkashaPartitionReader(p.getPath(), p.getTableName(), schema);
    }
}
