package com.example.kvdb.engine;

import com.example.kvdb.api.*;
import com.example.kvdb.core.TableImpl;
import com.example.kvdb.util.Serializer;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArkashaEngine implements StorageEngine, TableRegistry, Distributed {
    private final DatabaseConfig config;
    private final ArkashaMetrics metrics;
    private final ArkashaPersistenceManager persistenceManager;
    private final ArkashaWriteAheadLog writeAheadLog;
    private final Map<String, DistributedTable> tables;
    private final Map<String, Serializer<?>> serializers;
    private final ConsistentHashRing hashRing;
    private final Map<String, ClusterNode> clusterNodes;
    private boolean closed = false;

    public ArkashaEngine(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DatabaseConfig must not be null");
        }
        this.config = config;
        this.metrics = new ArkashaMetrics();
        this.tables = new HashMap<>();
        this.serializers = new HashMap<>();
        this.clusterNodes = new HashMap<>();
        this.hashRing = new ConsistentHashRing(128);
        initializeDefaultCluster();
        File dataDir = new File(config.getDataPath());
        dataDir.mkdirs();
        this.writeAheadLog = new ArkashaWriteAheadLog(this, new File(dataDir, "arkasha.wal"));
        this.persistenceManager = new ArkashaPersistenceManager(this, new File(dataDir, "arkasha.dat"));
        writeAheadLog.setActive(false);
        persistenceManager.load();
        int replayed = writeAheadLog.replay(this);
        writeAheadLog.setActive(true);
        if (replayed > 0) {
            persistenceManager.flush();
        }
    }

    private ClusterNode registerNode(String nodeId, ClusterNode.Role role) {
        ClusterNode node = new ClusterNode(nodeId, role);
        clusterNodes.put(nodeId, node);
        return node;
    }

    private void initializeDefaultCluster() {
        ClusterNode master = registerNode("node-0-master", ClusterNode.Role.MASTER);
        ClusterNode slave = registerNode("node-0-slave", ClusterNode.Role.SLAVE);
        hashRing.addGroup(new MasterSlaveGroup(master, List.of(slave)));
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
        serializers.remove(name);
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
        DistributedTable table = tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found");
        }
        table.replicateToSlavesOnly(key, value);
    }

    @Override
    public void addNode(String nodeId) {
        if (closed) {
            throw new IllegalStateException("Database is closed");
        }
        if (nodeId == null || nodeId.isEmpty()) {
            throw new IllegalArgumentException("Node identifier must not be null or empty");
        }
        String masterId = nodeId + "-master";
        String slaveId = nodeId + "-slave";
        if (clusterNodes.containsKey(masterId) || clusterNodes.containsKey(slaveId)) {
            throw new IllegalArgumentException("Cluster already contains node with id '" + nodeId + "'");
        }
        ClusterNode master = registerNode(masterId, ClusterNode.Role.MASTER);
        ClusterNode slave = registerNode(slaveId, ClusterNode.Role.SLAVE);
        MasterSlaveGroup group = new MasterSlaveGroup(master, List.of(slave));
        hashRing.addGroup(group);
        for (DistributedTable table : tables.values()) {
            table.registerGroup(group);
            table.rebalance();
        }
    }

    DistributedTable getStore(String tableName) {
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
        ClusterNode node = clusterNodes.get(nodeId);
        if (table == null || node == null) {
            return null;
        }
        return table.readFromNode(node, key);
    }

    public synchronized List<String> getNodeKeys(String tableName, String nodeId) {
        DistributedTable table = tables.get(tableName);
        ClusterNode node = clusterNodes.get(nodeId);
        if (table == null || node == null) {
            return List.of();
        }
        return table.keysOnNode(node);
    }

    public synchronized String locateMasterNode(String tableName, String key) {
        return hashRing.locate(tableName + "::" + key).getMaster().getId();
    }

    @Override
    public synchronized Distributed.TableClusterStatus describeTableCluster(String tableName) {
        DistributedTable table = tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found");
        }
        return new Distributed.TableClusterStatus(tableName, table.describeShards());
    }
}
