package com.example.kvdb.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

class ConsistentHashRing {
    private final SortedMap<Integer, MasterSlaveGroup> ring = new TreeMap<>();
    private final Map<String, MasterSlaveGroup> groupsById = new ConcurrentHashMap<>();
    private final int virtualNodes;
    private final ShardGossipProtocol gossipProtocol;
    private volatile SortedMap<Integer, MasterSlaveGroup> activeRing = new TreeMap<>();

    ConsistentHashRing(int virtualNodes, ShardGossipProtocol gossipProtocol) {
        this.virtualNodes = Math.max(1, virtualNodes);
        this.gossipProtocol = gossipProtocol;
        gossipProtocol.addStatusListener(this::rebuildActiveRing);
    }

    synchronized void addGroup(MasterSlaveGroup group) {
        String masterId = group.getMaster().getId();
        if (groupsById.containsKey(masterId)) {
            throw new IllegalArgumentException("Master node '" + masterId + "' already exists in ring");
        }
        groupsById.put(masterId, group);
        for (int i = 0; i < virtualNodes; i++) {
            int hash = hash(masterId + "#" + i);
            ring.put(hash, group);
        }
        gossipProtocol.registerShard(masterId);
        rebuildActiveRing();
    }

    synchronized MasterSlaveGroup locate(String key) {
        if (activeRing.isEmpty()) {
            rebuildActiveRing();
        }
        if (activeRing.isEmpty()) {
            throw new IllegalStateException("No nodes registered in the hash ring");
        }
        int hash = hash(key);
        SortedMap<Integer, MasterSlaveGroup> tail = activeRing.tailMap(hash);
        Integer nodeHash = tail.isEmpty() ? activeRing.firstKey() : tail.firstKey();
        return activeRing.get(nodeHash);
    }

    synchronized List<MasterSlaveGroup> getGroups() {
        return new ArrayList<>(new LinkedHashSet<>(activeRing.values()));
    }

    synchronized List<MasterSlaveGroup> getAllGroups() {
        return new ArrayList<>(new LinkedHashSet<>(ring.values()));
    }

    synchronized List<String> getActiveShardIds() {
        List<String> ids = new ArrayList<>();
        for (MasterSlaveGroup group : new LinkedHashSet<>(activeRing.values())) {
            ids.add(group.getMaster().getId());
        }
        return ids;
    }

    synchronized ShardGossipProtocol.ShardState shardState(String shardId) {
        return gossipProtocol.shardState(shardId);
    }

    synchronized void rebuildActiveRing() {
        TreeMap<Integer, MasterSlaveGroup> nextRing = new TreeMap<>();
        for (MasterSlaveGroup group : new LinkedHashSet<>(ring.values())) {
            String masterId = group.getMaster().getId();
            if (!gossipProtocol.isShardAvailable(masterId)) {
                continue;
            }
            for (int i = 0; i < virtualNodes; i++) {
                int hash = hash(masterId + "#" + i);
                nextRing.put(hash, group);
            }
        }
        activeRing = nextRing;
    }

    private int hash(String key) {
        final int fnvPrime = 0x01000193;
        int hash = 0x811c9dc5;
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= fnvPrime;
        }
        return hash & 0x7fffffff;
    }
}
