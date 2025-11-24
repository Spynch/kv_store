package com.example.kvdb.spark.source;

import org.apache.spark.sql.connector.catalog.SupportsRead;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCapability;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class ArkashaTable implements Table, SupportsRead {

    private final String path;
    private final String tableName;
    private final StructType schema;

    public ArkashaTable(String path, String tableName, StructType schema) {
        this.path = path;
        this.tableName = tableName;
        this.schema = schema;
    }

    public String getPath() {
        return path;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public String name() {
        return "arkasha:" + path + ":" + tableName;
    }

    @Override
    public StructType schema() {
        return schema;
    }

    @Override
    public Set<TableCapability> capabilities() {
        Set<TableCapability> caps = new HashSet<>();
        caps.add(TableCapability.BATCH_READ);
        return Collections.unmodifiableSet(caps);
    }

    @Override
    public ScanBuilder newScanBuilder(CaseInsensitiveStringMap options) {
        return new ArkashaScanBuilder(path, tableName, schema);
    }
}
