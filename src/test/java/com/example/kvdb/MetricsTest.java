package com.example.kvdb;

import com.example.kvdb.api.*;
import com.example.kvdb.engine.ArkashaEngine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;

public class MetricsTest {

    @Test
    void testMetricsCounting() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        StorageEngine db = new ArkashaEngine(new DatabaseConfig(dataDir));
        db.createTable("A", new TableOptions().setWalEnabled(false));
        db.createTable("B", new TableOptions().setWalEnabled(false));
        KeyValueStore<byte[]> tableA = db.openTable("A");
        KeyValueStore<byte[]> tableB = db.openTable("B");

        tableA.put("key1", "alpha".getBytes(StandardCharsets.UTF_8));
        tableA.put("key2", "beta".getBytes(StandardCharsets.UTF_8));
        tableB.put("keyX", "gamma".getBytes(StandardCharsets.UTF_8));

        Metrics metrics = db.getMetrics();
        assertThat(metrics.getKeyCount()).isEqualTo(3);
        int expectedBytes = 0;
        expectedBytes += "key1".getBytes(StandardCharsets.UTF_8).length + "alpha".getBytes(StandardCharsets.UTF_8).length;
        expectedBytes += "key2".getBytes(StandardCharsets.UTF_8).length + "beta".getBytes(StandardCharsets.UTF_8).length;
        expectedBytes += "keyX".getBytes(StandardCharsets.UTF_8).length + "gamma".getBytes(StandardCharsets.UTF_8).length;
        assertThat(metrics.getDataSize()).isEqualTo(expectedBytes);

        tableA.put("key1", "alphx".getBytes(StandardCharsets.UTF_8));
        assertThat(metrics.getKeyCount()).isEqualTo(3);
        assertThat(metrics.getDataSize()).isEqualTo(expectedBytes);


        tableB.put("keyX", "delta!".getBytes(StandardCharsets.UTF_8));
        expectedBytes += 1;
        assertThat(metrics.getKeyCount()).isEqualTo(3);
        assertThat(metrics.getDataSize()).isEqualTo(expectedBytes);

        tableA.delete("key2");
        expectedBytes -= ("key2".getBytes(StandardCharsets.UTF_8).length + "beta".getBytes(StandardCharsets.UTF_8).length);
        assertThat(metrics.getKeyCount()).isEqualTo(2);
        assertThat(metrics.getDataSize()).isEqualTo(expectedBytes);

        db.dropTable("B");
        int remainingBytes = "key1".getBytes(StandardCharsets.UTF_8).length + "alphx".getBytes(StandardCharsets.UTF_8).length;
        assertThat(metrics.getKeyCount()).isEqualTo(1);
        assertThat(metrics.getDataSize()).isEqualTo(remainingBytes);

        db.close();
    }
}
