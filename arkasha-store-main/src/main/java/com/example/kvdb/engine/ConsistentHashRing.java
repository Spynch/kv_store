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

    ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = Math.max(1, virtualNodes);
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
    }

    synchronized void removeGroup(String masterId) {
        MasterSlaveGroup group = groupsById.remove(masterId);
        if (group == null) {
            return;
        }
        ring.entrySet().removeIf(entry -> entry.getValue().equals(group));
    }

    synchronized void replaceMaster(String failedMasterId, MasterSlaveGroup replacement) {
        removeGroup(failedMasterId);
        addGroup(replacement);
    }

    synchronized MasterSlaveGroup locate(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No nodes registered in the hash ring");
        }
        int hash = hash(key);
        SortedMap<Integer, MasterSlaveGroup> tail = ring.tailMap(hash);
        Integer nodeHash = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(nodeHash);
    }

    synchronized List<MasterSlaveGroup> getGroups() {
        return new ArrayList<>(new LinkedHashSet<>(ring.values()));
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
