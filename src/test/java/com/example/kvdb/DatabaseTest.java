package com.example.kvdb;

import com.example.kvdb.api.*;
import com.example.kvdb.engine.ArkashaEngine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;

public class DatabaseTest {

    @Test
    void testCrudOperations() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        StorageEngine db = new ArkashaEngine(new DatabaseConfig(dataDir));
        TableOptions options = new TableOptions().setWalEnabled(false);
        db.createTable("test", options);
        KeyValueStore<byte[]> table = db.openTable("test");

        byte[] value1 = "value1".getBytes(StandardCharsets.UTF_8);
        table.put("key1", value1);
        assertThat(table.containsKey("key1")).isTrue();
        assertThat(new String(table.get("key1"), StandardCharsets.UTF_8)).isEqualTo("value1");

        byte[] value2 = "value2".getBytes(StandardCharsets.UTF_8);
        table.put("key1", value2);
        assertThat(new String(table.get("key1"), StandardCharsets.UTF_8)).isEqualTo("value2");

        table.delete("key1");
        assertThat(table.containsKey("key1")).isFalse();
        assertThat(table.get("key1")).isNull();

        table.delete("nonexistent");
        assertThat(table.containsKey("nonexistent")).isFalse();

        db.dropTable("test");
        assertThat(db.listTables()).doesNotContain("test");

        assertThatThrownBy(() -> db.openTable("test")).isInstanceOf(IllegalArgumentException.class);

        db.close();
    }

    @Test
    void testValueSizeLimit() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        StorageEngine db = new ArkashaEngine(new DatabaseConfig(dataDir));
        TableOptions options = new TableOptions().setMaxValueSize(5);
        db.createTable("limitTable", options);
        KeyValueStore<byte[]> table = db.openTable("limitTable");

        byte[] smallValue = new byte[5];
        byte[] largeValue = new byte[6];
        table.put("ok", smallValue);
        assertThatThrownBy(() -> table.put("tooLarge", largeValue))
                .isInstanceOf(IllegalArgumentException.class);
        db.close();
    }
}
