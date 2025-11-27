package com.example.kvdb.engine;

import com.example.kvdb.api.Distributed;
import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.core.InMemoryKeyValueStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class DistributedTable implements KeyValueStore<byte[]> {
    private final String name;
    private final TableOptions masterOptions;
    private final TableOptions slaveOptions;
    private final ConsistentHashRing hashRing;
    private final ArkashaWriteAheadLog wal;
    private final ArkashaMetrics metrics;

    DistributedTable(String name,
                     TableOptions options,
                     ConsistentHashRing hashRing,
                     ArkashaWriteAheadLog wal,
                     ArkashaMetrics metrics) {
        this.name = name;
        this.hashRing = hashRing;
        this.wal = wal;
        this.metrics = metrics;
        this.masterOptions = new TableOptions(options.isWalEnabled(), options.isFsync(), options.getMaxValueSize());
        this.slaveOptions = new TableOptions(false, false, options.getMaxValueSize());
        for (MasterSlaveGroup group : hashRing.getGroups()) {
            registerGroup(group);
        }
    }

    String getName() {
        return name;
    }

    TableOptions getMasterOptions() {
        return new TableOptions(masterOptions.isWalEnabled(), masterOptions.isFsync(), masterOptions.getMaxValueSize());
    }

    synchronized void registerGroup(MasterSlaveGroup group) {
        getMasterStore(group);
        for (ClusterNode slave : group.getSlaves()) {
            getSlaveStore(slave);
        }
    }

    private MasterSlaveGroup locateGroup(String key) {
        return hashRing.locate(name + "::" + key);
    }

    MasterSlaveGroup locateGroupForKey(String key) {
        return locateGroup(key);
    }

    ClusterNode locateMasterForKey(String key) {
        return locateGroup(key).getMaster();
    }

    private InMemoryKeyValueStore getMasterStore(MasterSlaveGroup group) {
        return group.getMaster().getOrCreateStore(name,
                () -> new InMemoryKeyValueStore(name, masterOptions, wal, metrics));
    }

    private InMemoryKeyValueStore getSlaveStore(ClusterNode slave) {
        return slave.getOrCreateStore(name,
                () -> new InMemoryKeyValueStore(name, slaveOptions, wal, new ArkashaMetrics()));
    }

    @Override
    public synchronized void put(String key, byte[] value) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        if (masterOptions.getMaxValueSize() >= 0 && value.length > masterOptions.getMaxValueSize()) {
            throw new IllegalArgumentException("Размер значения превышает допустимый лимит");
        }
        MasterSlaveGroup group = locateGroup(key);
        InMemoryKeyValueStore masterStore = getMasterStore(group);
        masterStore.put(key, value);
        replicateToSlaves(group, key, value);
    }

    @Override
    public synchronized byte[] get(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        MasterSlaveGroup group = locateGroup(key);
        InMemoryKeyValueStore masterStore = getMasterStore(group);
        byte[] value = masterStore.get(key);
        if (value != null) {
            return value;
        }
        for (ClusterNode slave : group.getSlaves()) {
            InMemoryKeyValueStore slaveStore = getSlaveStore(slave);
            value = slaveStore.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public synchronized void delete(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        MasterSlaveGroup group = locateGroup(key);
        deleteFromGroup(group, key);
    }

    @Override
    public synchronized boolean containsKey(String key) {
        Objects.requireNonNull(key, "Key must not be null");
        MasterSlaveGroup group = locateGroup(key);
        InMemoryKeyValueStore masterStore = getMasterStore(group);
        if (masterStore.containsKey(key)) {
            return true;
        }
        for (ClusterNode slave : group.getSlaves()) {
            InMemoryKeyValueStore slaveStore = getSlaveStore(slave);
            if (slaveStore.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    List<String> keys() {
        Set<String> result = new LinkedHashSet<>();
        for (MasterSlaveGroup group : hashRing.getGroups()) {
            InMemoryKeyValueStore store = group.getMaster().getStore(name);
            if (store != null) {
                result.addAll(store.keys());
            }
        }
        return new ArrayList<>(result);
    }

    synchronized void rebalance() {
        rebalanceFromSources(List.of());
    }

    synchronized void rebalanceFromSources(List<MasterSlaveGroup> additionalSources) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        Set<MasterSlaveGroup> sources = new LinkedHashSet<>(hashRing.getGroups());
        sources.addAll(additionalSources);
        for (MasterSlaveGroup group : sources) {
            InMemoryKeyValueStore store = group.getMaster().getStore(name);
            if (store == null) {
                continue;
            }
            for (String key : store.keys()) {
                byte[] value = store.get(key);
                if (value != null) {
                    entries.put(key, value);
                }
            }
        }
        for (MasterSlaveGroup group : sources) {
            InMemoryKeyValueStore store = group.getMaster().getStore(name);
            if (store == null) {
                continue;
            }
            for (String key : store.keys()) {
                deleteFromGroup(group, key);
            }
        }
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    synchronized void dropFromCluster() {
        List<String> allKeys = keys();
        for (String key : allKeys) {
            delete(key);
        }
        for (MasterSlaveGroup group : hashRing.getGroups()) {
            group.getMaster().dropTable(name);
            for (ClusterNode slave : group.getSlaves()) {
                slave.dropTable(name);
            }
        }
    }

    synchronized void replicateToSlavesOnly(String key, byte[] value) {
        MasterSlaveGroup group = locateGroup(key);
        replicateToSlaves(group, key, value);
    }

    private void deleteFromGroup(MasterSlaveGroup group, String key) {
        InMemoryKeyValueStore masterStore = getMasterStore(group);
        if (masterStore.containsKey(key)) {
            masterStore.delete(key);
        }
        replicateToSlaves(group, key, null);
    }

    private void replicateToSlaves(MasterSlaveGroup group, String key, byte[] value) {
        for (ClusterNode slave : group.getSlaves()) {
            InMemoryKeyValueStore slaveStore = getSlaveStore(slave);
            if (value == null) {
                if (slaveStore.containsKey(key)) {
                    slaveStore.delete(key);
                }
            } else {
                slaveStore.put(key, value);
            }
        }
    }

    byte[] readFromNode(ClusterNode node, String key) {
        InMemoryKeyValueStore store = node.getStore(name);
        if (store == null) {
            return null;
        }
        return store.get(key);
    }

    List<String> keysOnNode(ClusterNode node) {
        return node.listKeys(name);
    }

    synchronized List<Distributed.ShardStatus> describeShards() {
        List<Distributed.ShardStatus> shards = new ArrayList<>();
        for (MasterSlaveGroup group : hashRing.getGroups()) {
            Distributed.NodeStatus master = describeNode(group.getMaster());
            List<Distributed.NodeStatus> replicas = new ArrayList<>();
            for (ClusterNode slave : group.getSlaves()) {
                replicas.add(describeNode(slave));
            }
            shards.add(new Distributed.ShardStatus(group.getMaster().getId(), master, replicas));
        }
        return shards;
    }

    private Distributed.NodeStatus describeNode(ClusterNode node) {
        List<String> keys = keysOnNode(node);
        return new Distributed.NodeStatus(
                node.getId(),
                node.getRole().name().toLowerCase(Locale.ROOT),
                keys.size(),
                keys
        );
    }
}
