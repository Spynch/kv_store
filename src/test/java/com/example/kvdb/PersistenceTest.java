package com.example.kvdb;

import com.example.kvdb.api.*;
import com.example.kvdb.engine.ArkashaEngine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class PersistenceTest {

    @Test
    void testSnapshotPersistence() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        StorageEngine db1 = new ArkashaEngine(new DatabaseConfig(dataDir));
        db1.createTable("tbl", new TableOptions().setWalEnabled(false));
        KeyValueStore<byte[]> table1 = db1.openTable("tbl");
        table1.put("k1", "v1".getBytes(StandardCharsets.UTF_8));
        table1.put("k2", "v2".getBytes(StandardCharsets.UTF_8));
        db1.close();

        StorageEngine db2 = new ArkashaEngine(new DatabaseConfig(dataDir));
        KeyValueStore<byte[]> table2 = db2.openTable("tbl");
        assertThat(new String(table2.get("k1"), StandardCharsets.UTF_8)).isEqualTo("v1");
        assertThat(new String(table2.get("k2"), StandardCharsets.UTF_8)).isEqualTo("v2");
        Metrics metrics = db2.getMetrics();
        assertThat(metrics.getKeyCount()).isEqualTo(2);
        db2.close();

        File snapshot = new File(dataDir, "arkasha.dat");
        assertThat(snapshot.exists()).isTrue();
    }
}
