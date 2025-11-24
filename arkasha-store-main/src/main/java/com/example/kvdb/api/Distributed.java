package com.example.kvdb.api;


import java.util.List;

public interface Distributed {

    void replicate(String tableName, String key, byte[] value);

    void addNode(String nodeId);

    TableClusterStatus describeTableCluster(String tableName);

    record TableClusterStatus(String tableName, List<ShardStatus> shards) {}

    record ShardStatus(String shardId, String status, NodeStatus master, List<NodeStatus> replicas) {}

    record NodeStatus(String nodeId, String role, int keyCount, List<String> keys) {}
}
