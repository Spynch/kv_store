package com.example.kvdb;

import com.example.kvdb.api.*;
import com.example.kvdb.engine.ArkashaEngine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConcurrencyTest {

    @Test
    void testParallelWritesAndReads() throws InterruptedException {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        StorageEngine db = new ArkashaEngine(new DatabaseConfig(dataDir));
        db.createTable("concurrent", new TableOptions().setWalEnabled(true));
        KeyValueStore<byte[]> store = db.openTable("concurrent");

        int threadCount = 4;
        int keysPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicBoolean errorFlag = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < keysPerThread; i++) {
                        String key = "T" + threadId + "_K" + i;
                        String val = "Value" + i;
                        store.put(key, val.getBytes(StandardCharsets.UTF_8));
                        byte[] got = store.get(key);
                        if (got == null || !val.equals(new String(got, StandardCharsets.UTF_8))) {
                            errorFlag.set(true);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        assertThat(errorFlag.get()).isFalse();
        Metrics metrics = db.getMetrics();
        assertThat(metrics.getKeyCount()).isEqualTo(threadCount * keysPerThread);
        for (int t = 0; t < threadCount; t++) {
            String sampleKey = "T" + t + "_K" + (keysPerThread - 1);
            assertThat(new String(store.get(sampleKey), StandardCharsets.UTF_8))
                    .isEqualTo("Value" + (keysPerThread - 1));
        }
        db.close();
    }
}
