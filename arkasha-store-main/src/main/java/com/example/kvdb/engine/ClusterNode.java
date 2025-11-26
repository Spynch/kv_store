package com.example.kvdb.engine;

import com.example.kvdb.core.InMemoryKeyValueStore;
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

    private final String id;
    private volatile Role role;
    private final Map<String, InMemoryKeyValueStore> tables = new ConcurrentHashMap<>();
    private volatile boolean available = true;
    private volatile com.example.kvdb.api.Distributed.HealthStatus healthStatus =
            new com.example.kvdb.api.Distributed.HealthStatus(true, System.currentTimeMillis(), "not checked yet");

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

    void setRole(Role role) {
        this.role = role;
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

    boolean isAvailable() {
        return available;
    }

    void setAvailable(boolean available) {
        this.available = available;
    }

    com.example.kvdb.api.Distributed.HealthStatus getHealthStatus() {
        return healthStatus;
    }

    void updateHealthStatus(boolean healthy, String message) {
        this.healthStatus = new com.example.kvdb.api.Distributed.HealthStatus(healthy, System.currentTimeMillis(), message);
    }

    List<String> listKeys(String tableName) {
        InMemoryKeyValueStore store = tables.get(tableName);
        if (store == null) {
            return Collections.emptyList();
        }
        return store.keys();
    }
}
