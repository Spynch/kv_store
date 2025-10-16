package com.example.kvdb;

import com.example.kvdb.api.*;
import com.example.kvdb.engine.ArkashaEngine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class WALTest {

    @Test
    void testRecoveryFromWAL() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        StorageEngine db1 = new ArkashaEngine(new DatabaseConfig(dataDir));
        db1.createTable("walTable", new TableOptions().setWalEnabled(true));
        KeyValueStore<byte[]> table1 = db1.openTable("walTable");
        table1.put("id1", "data1".getBytes(StandardCharsets.UTF_8));

        StorageEngine db2 = new ArkashaEngine(new DatabaseConfig(dataDir));
        KeyValueStore<byte[]> table2 = db2.openTable("walTable");
        assertThat(new String(table2.get("id1"), StandardCharsets.UTF_8)).isEqualTo("data1");
        db2.close();
        File walFile = new File(dataDir, "arkasha.wal");
        assertThat(walFile.exists()).isFalse();
    }
}
