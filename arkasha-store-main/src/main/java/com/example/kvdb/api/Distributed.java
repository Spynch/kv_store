package com.example.kvdb.api;


import java.util.List;

public interface Distributed {

    void replicate(String tableName, String key, byte[] value);

    void addNode(String nodeId);

    TableClusterStatus describeTableCluster(String tableName);

    void markMasterAsUnavailable(String masterId);

    void restoreMaster(String masterId);

    ClusterHealthStatus describeClusterHealth();

    record TableClusterStatus(String tableName, List<ShardStatus> shards) {}

    record ShardStatus(String shardId, NodeStatus master, List<NodeStatus> replicas) {}

    record NodeStatus(String nodeId, String role, int keyCount, List<String> keys) {}

    record MasterNodeHealth(String nodeId, String status, long lastHeartbeatMillis,
                            boolean inRing, boolean manuallyDisabled) {}

    record RingStatus(List<String> activeMasters, int virtualNodes, int ringSize) {}

    record ClusterHealthStatus(List<MasterNodeHealth> masters, RingStatus ring) {}
}
