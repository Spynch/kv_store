package com.example.kvdb.engine;

import com.example.kvdb.api.*;
import com.example.kvdb.core.TableImpl;
import com.example.kvdb.util.Serializer;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArkashaEngine implements StorageEngine, TableRegistry, Distributed {
    private static final int DEFAULT_VIRTUAL_NODES = 128;

    private final DatabaseConfig config;
    private final ArkashaMetrics metrics;
    private final ArkashaPersistenceManager persistenceManager;
    private final ArkashaWriteAheadLog writeAheadLog;
    private final Map<String, DistributedTable> tables;
    private final Map<String, Serializer<?>> serializers;
    private final Map<String, ClusterNode> nodeRegistry;
    private final ConsistentHashRing hashRing;
    private boolean closed = false;

    public ArkashaEngine(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DatabaseConfig must not be null");
        }
        this.config = config;
        this.metrics = new ArkashaMetrics();
        this.tables = new HashMap<>();
        this.serializers = new HashMap<>();
        this.nodeRegistry = new HashMap<>();
        this.hashRing = new ConsistentHashRing(DEFAULT_VIRTUAL_NODES);
        File dataDir = new File(config.getDataPath());
        dataDir.mkdirs();
        this.writeAheadLog = new ArkashaWriteAheadLog(this, new File(dataDir, "arkasha.wal"));
        this.persistenceManager = new ArkashaPersistenceManager(this, new File(dataDir, "arkasha.dat"));
        initializeCluster();
        writeAheadLog.setActive(false);
        persistenceManager.load();
        int replayed = writeAheadLog.replay(this);
        writeAheadLog.setActive(true);
        if (replayed > 0) {
            persistenceManager.flush();
        }
    }

    @Override
    public synchronized void createTable(String name, TableOptions options) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Table name must not be null or empty");
        }
        if (tables.containsKey(name)) {
            throw new IllegalArgumentException("Table '" + name + "' already exists");
        }
        if (options == null) {
            options = new TableOptions();
        }
        DistributedTable table = new DistributedTable(name, options, hashRing, writeAheadLog, metrics);
        tables.put(name, table);
    }

    @Override
    public synchronized KeyValueStore<byte[]> openTable(String name) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        DistributedTable table = tables.get(name);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + name + "' not found");
        }
        return table;
    }

    @Override
    public synchronized void dropTable(String name) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        DistributedTable table = tables.remove(name);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + name + "' not found");
        }
        table.dropFromCluster();
    }

    @Override
    public synchronized List<String> listTables() {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        return new ArrayList<>(tables.keySet());
    }

    @Override
    public void close() {
        ArkashaPersistenceManager pm;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pm = persistenceManager;
        }
        pm.flush();
    }

    @Override
    public PersistenceManager getPersistenceManager() {
        return persistenceManager;
    }

    @Override
    public WriteAheadLog getWriteAheadLog() {
        return writeAheadLog;
    }

    @Override
    public DatabaseConfig getConfig() {
        return config;
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public <T> void register(String tableName, Class<T> type, Serializer<T> serializer) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        if (!tables.containsKey(tableName)) {
            throw new IllegalArgumentException("Table '" + tableName + "' does not exist");
        }
        if (serializer == null || type == null) {
            throw new IllegalArgumentException("Type and serializer must not be null");
        }
        if (serializers.containsKey(tableName)) {
            throw new IllegalArgumentException("Serializer for table '" + tableName + "' already registered");
        }
        serializers.put(tableName, serializer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Table<T> openTable(String tableName, Class<T> type) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }

        // 🔹 Всегда используем TLV-сериализатор
        com.example.kvdb.util.TlvUserSerializer serializer = new com.example.kvdb.util.TlvUserSerializer();

        KeyValueStore<byte[]> rawStore = openTable(tableName);
        return new TableImpl<>(rawStore, (com.example.kvdb.util.Serializer<T>) serializer);
    }


    @Override
    public void replicate(String tableName, String key, byte[] value) {
        DistributedTable table;
        synchronized (this) {
            table = tables.get(tableName);
        }
        if (table == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found");
        }
        table.replicateToSlavesOnly(key, value);
    }

    @Override
    public void addNode(String nodeId) {
        addNodeInternal(nodeId, true);
    }

    synchronized DistributedTable getDistributedTable(String tableName) {
        return tables.get(tableName);
    }

    List<String> getAllTableNames() {
        return new ArrayList<>(tables.keySet());
    }

    TableOptions getTableOptions(String tableName) {
        DistributedTable table = tables.get(tableName);
        return (table != null ? table.getMasterOptions() : null);
    }

    public synchronized byte[] readFromNode(String tableName, String nodeId, String key) {
        DistributedTable table = tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found");
        }
        ClusterNode node = requireNode(nodeId);
        return table.readFromNode(node, key);
    }

    public synchronized List<String> getNodeKeys(String tableName, String nodeId) {
        DistributedTable table = tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found");
        }
        ClusterNode node = requireNode(nodeId);
        return table.keysOnNode(node);
    }

    public synchronized String locateMasterNode(String tableName, String key) {
        DistributedTable table = tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found");
        }
        return table.locateMasterForKey(key).getId();
    }

    @Override
    public synchronized TableClusterStatus describeTableCluster(String tableName) {
        DistributedTable table = tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found");
        }
        return new Distributed.TableClusterStatus(tableName, table.describeShards());
    }

    private synchronized void initializeCluster() {
        if (nodeRegistry.isEmpty()) {
            addNodeInternal("node-0", false);
        }
    }

    private synchronized void addNodeInternal(String nodeId, boolean rebalance) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        String baseId = normalizeNodeId(nodeId);
        String masterId = baseId + "-master";
        String slaveId = baseId + "-slave";
        if (nodeRegistry.containsKey(masterId) || nodeRegistry.containsKey(slaveId)) {
            throw new IllegalArgumentException("Node group '" + baseId + "' already exists");
        }
        ClusterNode master = new ClusterNode(masterId, ClusterNode.Role.MASTER);
        ClusterNode slave = new ClusterNode(slaveId, ClusterNode.Role.SLAVE);
        nodeRegistry.put(masterId, master);
        nodeRegistry.put(slaveId, slave);
        MasterSlaveGroup group = new MasterSlaveGroup(master, Collections.singletonList(slave));
        hashRing.addGroup(group);
        for (DistributedTable table : tables.values()) {
            table.registerGroup(group);
        }
        if (rebalance) {
            for (DistributedTable table : tables.values()) {
                table.rebalance();
            }
        }
    }

    private ClusterNode requireNode(String nodeId) {
        ClusterNode node = nodeRegistry.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Node '" + nodeId + "' not found");
        }
        return node;
    }

    private String normalizeNodeId(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Node id must not be null or empty");
        }
        String trimmed = nodeId.trim();
        if (trimmed.endsWith("-master")) {
            return trimmed.substring(0, trimmed.length() - "-master".length());
        }
        if (trimmed.endsWith("-slave")) {
            return trimmed.substring(0, trimmed.length() - "-slave".length());
        }
        return trimmed;
    }
}
