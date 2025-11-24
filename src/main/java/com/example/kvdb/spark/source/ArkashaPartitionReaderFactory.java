package com.example.kvdb.spark.source;

import com.example.kvdb.api.DatabaseConfig;
import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.core.InMemoryKeyValueStore;
import com.example.kvdb.engine.ArkashaEngine;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;


class ArkashaPartitionReader implements PartitionReader<InternalRow>, Serializable {

    private final String path;
    private final String tableName;
    @SuppressWarnings("unused")
    private final StructType schema;

    private transient ArkashaEngine engine;
    private transient InMemoryKeyValueStore store;
    private transient Iterator<String> keyIterator;

    private InternalRow currentRow;

    public ArkashaPartitionReader(String path, String tableName, StructType schema) {
        this.path = path;
        this.tableName = tableName;
        this.schema = schema;
        initialize();
    }

    private void initialize() {
        DatabaseConfig config = new DatabaseConfig(path);
        this.engine = new ArkashaEngine(config);

        KeyValueStore<byte[]> kvStore = engine.openTable(tableName);
        this.store = (InMemoryKeyValueStore) kvStore;

        List<String> keys = store.keys();
        this.keyIterator = keys.iterator();
    }

    @Override
    public boolean next() throws IOException {
        if (keyIterator == null) {
            return false;
        }
        if (!keyIterator.hasNext()) {
            currentRow = null;
            return false;
        }

        String key = keyIterator.next();
        byte[] value = store.get(key);
        int valueSize = (value != null ? value.length : 0);
        boolean isLarge = valueSize > 1024;

        Object[] values = new Object[4];
        values[0] = UTF8String.fromString(key);
        values[1] = value;
        values[2] = valueSize;
        values[3] = isLarge;

        this.currentRow = new GenericInternalRow(values);
        return true;
    }

    @Override
    public InternalRow get() {
        return currentRow;
    }

    @Override
    public void close() throws IOException {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }
}
