package com.example.kvdb;

import com.example.kvdb.api.DatabaseConfig;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.engine.ArkashaEngine;
import com.example.kvdb.engine.ShardGossipProtocol;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GossipHashRingTest {

    @Test
    void gossipRemovesDownShardFromActiveRing() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        ArkashaEngine engine = new ArkashaEngine(new DatabaseConfig(dataDir));
        engine.createTable("users", new TableOptions().setWalEnabled(false));

        engine.addNode("node-1");
        assertThat(engine.getActiveShards()).contains("node-0-master", "node-1-master");

        String keyOnFirstShard = IntStream.range(0, 5_000)
                .mapToObj(i -> "user:" + i)
                .filter(k -> engine.locateMasterNode("users", k).equals("node-0-master"))
                .findFirst()
                .orElseThrow();

        engine.updateShardState("node-0-master", ShardGossipProtocol.ShardState.DOWN);

        assertThat(engine.getActiveShards()).containsExactly("node-1-master");
        String newOwner = engine.locateMasterNode("users", keyOnFirstShard);
        assertThat(newOwner).isEqualTo("node-1-master");
    }
}
