package com.example.kvdb;

import com.example.kvdb.api.DatabaseConfig;
import com.example.kvdb.api.Distributed;
import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.engine.ArkashaEngine;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class DistributedEngineTest {

    @Test
    void replicationCopiesDataToSlave() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        ArkashaEngine engine = new ArkashaEngine(new DatabaseConfig(dataDir));
        engine.createTable("users", new TableOptions().setWalEnabled(false));
        KeyValueStore<byte[]> table = engine.openTable("users");

        byte[] value = "John".getBytes(StandardCharsets.UTF_8);
        table.put("user:1", value);

        byte[] replicaValue = engine.readFromNode("users", "node-0-slave", "user:1");
        assertThat(replicaValue).isNotNull();
        assertThat(new String(replicaValue, StandardCharsets.UTF_8)).isEqualTo("John");

        table.delete("user:1");
        assertThat(engine.readFromNode("users", "node-0-slave", "user:1")).isNull();
    }

    @Test
    void shardStatusReflectsMasterAndReplicaKeys() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        ArkashaEngine engine = new ArkashaEngine(new DatabaseConfig(dataDir));
        engine.createTable("users", new TableOptions().setWalEnabled(false));
        KeyValueStore<byte[]> table = engine.openTable("users");

        table.put("user:42", "Alice".getBytes(StandardCharsets.UTF_8));

        Distributed.TableClusterStatus status = engine.describeTableCluster("users");
        assertThat(status.tableName()).isEqualTo("users");
        assertThat(status.shards()).isNotEmpty();
        Distributed.ShardStatus shard = status.shards().get(0);
        assertThat(shard.master().role()).isEqualTo("master");
        assertThat(shard.master().keys()).contains("user:42");
        assertThat(shard.replicas()).isNotEmpty();
        assertThat(shard.replicas().get(0).keys()).contains("user:42");

        table.delete("user:42");

        Distributed.TableClusterStatus afterDelete = engine.describeTableCluster("users");
        assertThat(afterDelete.shards()).allSatisfy(s -> {
            assertThat(s.master().keyCount()).isZero();
            assertThat(s.replicas()).allSatisfy(replica -> {
                assertThat(replica.keyCount()).isZero();
                assertThat(replica.keys()).isEmpty();
            });
        });
    }

    @Test
    void consistentHashingRebalancesAfterAddingNode() {
        String dataDir = System.getProperty("java.io.tmpdir") + "/arkashaTest-" + System.nanoTime();
        ArkashaEngine engine = new ArkashaEngine(new DatabaseConfig(dataDir));
        engine.createTable("events", new TableOptions().setWalEnabled(false));
        KeyValueStore<byte[]> table = engine.openTable("events");

        for (int i = 0; i < 10; i++) {
            String key = "event:" + i;
            table.put(key, ("payload-" + i).getBytes(StandardCharsets.UTF_8));
        }

        Distributed distributed = engine;
        distributed.addNode("node-1");

        for (int i = 0; i < 10; i++) {
            String key = "event:" + i;
            byte[] value = table.get(key);
            assertThat(value).as("value for " + key).isNotNull();
            String expectedMaster = engine.locateMasterNode("events", key);
            List<String> masterKeys = engine.getNodeKeys("events", expectedMaster);
            assertThat(masterKeys).contains(key);
        }

        List<String> newNodeKeys = engine.getNodeKeys("events", "node-1-master");
        if (newNodeKeys.isEmpty()) {
            Optional<String> targetKey = IntStream.range(100, 500)
                    .mapToObj(i -> "rebalance:" + i)
                    .filter(k -> engine.locateMasterNode("events", k).equals("node-1-master"))
                    .findFirst();
            assertThat(targetKey).isPresent();
            table.put(targetKey.get(), "new".getBytes(StandardCharsets.UTF_8));
            newNodeKeys = engine.getNodeKeys("events", "node-1-master");
        }
        assertThat(newNodeKeys).isNotEmpty();
    }
}
