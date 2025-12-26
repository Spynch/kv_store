package com.example.kvdb.engine;

import com.example.kvdb.core.InMemoryKeyValueStore;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Represents a physical node participating in the cluster.
 */
class ClusterNode {
    enum Role {
        MASTER,
        SLAVE
    }

    enum HealthState {
        HEALTHY,
        SUSPECT,
        DOWN,
        MANUALLY_DISABLED
    }

    private final String id;
    private final Role role;
    private final Map<String, InMemoryKeyValueStore> tables = new ConcurrentHashMap<>();
    private volatile Instant lastHeartbeat = Instant.now();
    private volatile boolean manualDisabled = false;

    ClusterNode(String id, Role role) {
        this.id = id;
        this.role = role;
    }

    String getId() {
        return id;
    }

    Role getRole() {
        return role;
    }

    Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    void markHeartbeat() {
        lastHeartbeat = Instant.now();
    }

    boolean isManualDisabled() {
        return manualDisabled;
    }

    void setManualDisabled(boolean manualDisabled) {
        this.manualDisabled = manualDisabled;
        if (!manualDisabled) {
            markHeartbeat();
        }
    }

    InMemoryKeyValueStore getOrCreateStore(String tableName, Supplier<InMemoryKeyValueStore> factory) {
        return tables.computeIfAbsent(tableName, k -> factory.get());
    }

    InMemoryKeyValueStore getStore(String tableName) {
        return tables.get(tableName);
    }

    void dropTable(String tableName) {
        InMemoryKeyValueStore store = tables.remove(tableName);
        if (store != null) {
            store.clearData();
        }
    }

    List<String> listKeys(String tableName) {
        InMemoryKeyValueStore store = tables.get(tableName);
        if (store == null) {
            return Collections.emptyList();
        }
        return store.keys();
    }
}
