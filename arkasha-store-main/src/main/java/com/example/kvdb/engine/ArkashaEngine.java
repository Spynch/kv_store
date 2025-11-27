package com.example.kvdb.engine;

import com.example.kvdb.api.*;
import com.example.kvdb.core.TableImpl;
import com.example.kvdb.util.Serializer;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArkashaEngine implements StorageEngine, TableRegistry, Distributed {
    private static final int DEFAULT_VIRTUAL_NODES = 128;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(2);
    private static final Duration SUSPECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(8);

    private final DatabaseConfig config;
    private final ArkashaMetrics metrics;
    private final ArkashaPersistenceManager persistenceManager;
    private final ArkashaWriteAheadLog writeAheadLog;
    private final Map<String, DistributedTable> tables;
    private final Map<String, Serializer<?>> serializers;
    private final Map<String, ClusterNode> nodeRegistry;
    private final Map<String, MasterSlaveGroup> masterGroups;
    private final Map<String, ClusterNode.HealthState> masterHealth;
    private final ConsistentHashRing hashRing;
    private final ScheduledExecutorService scheduler;
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
        this.masterGroups = new HashMap<>();
        this.masterHealth = new ConcurrentHashMap<>();
        this.hashRing = new ConsistentHashRing(DEFAULT_VIRTUAL_NODES);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arkasha-health-monitor");
            t.setDaemon(true);
            return t;
        });
        File dataDir = new File(config.getDataPath());
        dataDir.mkdirs();
        this.writeAheadLog = new ArkashaWriteAheadLog(this, new File(dataDir, "arkasha.wal"));
        this.persistenceManager = new ArkashaPersistenceManager(this, new File(dataDir, "arkasha.dat"));
        initializeCluster();
        startHealthMonitoring();
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
        ScheduledExecutorService monitor;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pm = persistenceManager;
            monitor = scheduler;
        }
        monitor.shutdownNow();
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

    @Override
    public synchronized void markMasterAsUnavailable(String masterId) {
        MasterSlaveGroup group = requireMasterGroup(masterId);
        ClusterNode master = group.getMaster();
        master.setManualDisabled(true);
        ClusterNode.HealthState newState = ClusterNode.HealthState.MANUALLY_DISABLED;
        masterHealth.put(masterId, newState);
        handleHealthTransition(masterId, newState);
    }

    @Override
    public synchronized void restoreMaster(String masterId) {
        MasterSlaveGroup group = requireMasterGroup(masterId);
        ClusterNode master = group.getMaster();
        master.setManualDisabled(false);
        ClusterNode.HealthState newState = calculateHealth(master, Instant.now());
        masterHealth.put(masterId, newState);
        handleHealthTransition(masterId, newState);
    }

    @Override
    public synchronized ClusterHealthStatus describeClusterHealth() {
        List<Distributed.MasterNodeHealth> masters = new ArrayList<>();
        for (Map.Entry<String, MasterSlaveGroup> entry : masterGroups.entrySet()) {
            String masterId = entry.getKey();
            ClusterNode node = entry.getValue().getMaster();
            ClusterNode.HealthState state = masterHealth.getOrDefault(masterId, ClusterNode.HealthState.SUSPECT);
            long lastHeartbeat = node.getLastHeartbeat() == null ? -1L : node.getLastHeartbeat().toEpochMilli();
            boolean inRing = hashRing.containsMaster(masterId);
            masters.add(new Distributed.MasterNodeHealth(masterId, state.name().toLowerCase(), lastHeartbeat, inRing, node.isManualDisabled()));
        }
        List<String> activeMasters = hashRing.getGroups().stream()
                .map(group -> group.getMaster().getId())
                .collect(Collectors.toList());
        Distributed.RingStatus ring = new Distributed.RingStatus(activeMasters, DEFAULT_VIRTUAL_NODES, activeMasters.size());
        return new Distributed.ClusterHealthStatus(masters, ring);
    }

    private void startHealthMonitoring() {
        scheduler.scheduleAtFixedRate(this::safeHeartbeatTick, 0, HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::safeHealthCheckTick, HEARTBEAT_INTERVAL.toMillis(), HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void safeHeartbeatTick() {
        try {
            heartbeatMasters();
        } catch (Exception ignored) {
        }
    }

    private void safeHealthCheckTick() {
        try {
            evaluateMastersHealth();
        } catch (Exception ignored) {
        }
    }

    private synchronized void heartbeatMasters() {
        for (MasterSlaveGroup group : masterGroups.values()) {
            ClusterNode master = group.getMaster();
            if (!master.isManualDisabled()) {
                master.markHeartbeat();
            }
        }
    }

    private synchronized void evaluateMastersHealth() {
        Instant now = Instant.now();
        for (Map.Entry<String, MasterSlaveGroup> entry : masterGroups.entrySet()) {
            String masterId = entry.getKey();
            ClusterNode master = entry.getValue().getMaster();
            ClusterNode.HealthState newState = calculateHealth(master, now);
            ClusterNode.HealthState previous = masterHealth.put(masterId, newState);
            if (previous != newState) {
                handleHealthTransition(masterId, newState);
            }
        }
    }

    private ClusterNode.HealthState calculateHealth(ClusterNode node, Instant now) {
        if (node.isManualDisabled()) {
            return ClusterNode.HealthState.MANUALLY_DISABLED;
        }
        Duration sinceLast = Duration.between(node.getLastHeartbeat(), now);
        if (sinceLast.compareTo(HEARTBEAT_TIMEOUT) > 0) {
            return ClusterNode.HealthState.DOWN;
        }
        if (sinceLast.compareTo(SUSPECT_TIMEOUT) > 0) {
            return ClusterNode.HealthState.SUSPECT;
        }
        return ClusterNode.HealthState.HEALTHY;
    }

    private synchronized void handleHealthTransition(String masterId, ClusterNode.HealthState state) {
        boolean ringChanged = false;
        MasterSlaveGroup group = masterGroups.get(masterId);
        List<MasterSlaveGroup> extraSources = List.of();
        if (state == ClusterNode.HealthState.DOWN || state == ClusterNode.HealthState.MANUALLY_DISABLED) {
            if (group != null) {
                extraSources = List.of(group);
            }
            ringChanged = hashRing.removeGroup(masterId);
        } else if (state == ClusterNode.HealthState.HEALTHY) {
            if (group != null && !hashRing.containsMaster(masterId)) {
                hashRing.addGroup(group);
                for (DistributedTable table : tables.values()) {
                    table.registerGroup(group);
                }
                ringChanged = true;
            }
        }
        if (ringChanged) {
            rebalanceTables(extraSources);
        }
    }

    private synchronized void rebalanceTables() {
        rebalanceTables(List.of());
    }

    private synchronized void rebalanceTables(List<MasterSlaveGroup> extraSources) {
        for (DistributedTable table : tables.values()) {
            table.rebalanceFromSources(extraSources);
        }
    }

    private synchronized MasterSlaveGroup requireMasterGroup(String masterId) {
        MasterSlaveGroup group = masterGroups.get(masterId);
        if (group == null) {
            throw new IllegalArgumentException("Master node '" + masterId + "' not found");
        }
        return group;
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
        masterGroups.put(masterId, group);
        masterHealth.put(masterId, ClusterNode.HealthState.HEALTHY);
        master.markHeartbeat();
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
