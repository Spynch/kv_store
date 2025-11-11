package com.example.kvdb.api;


public interface Distributed {

    void replicate(String tableName, String key, byte[] value);

    void addNode(String nodeId);

    TableClusterStatus describeTableCluster(String tableName);

    record NodeStatus(String nodeId, String role, int keyCount, java.util.List<String> keys) {
        public NodeStatus {
            java.util.Objects.requireNonNull(nodeId, "nodeId");
            java.util.Objects.requireNonNull(role, "role");
            java.util.Objects.requireNonNull(keys, "keys");
            keys = java.util.List.copyOf(keys);
        }
    }

    record ShardStatus(String shardId, NodeStatus master, java.util.List<NodeStatus> replicas) {
        public ShardStatus {
            java.util.Objects.requireNonNull(shardId, "shardId");
            java.util.Objects.requireNonNull(master, "master");
            java.util.Objects.requireNonNull(replicas, "replicas");
            replicas = java.util.List.copyOf(replicas);
        }
    }

    record TableClusterStatus(String tableName, java.util.List<ShardStatus> shards) {
        public TableClusterStatus {
            java.util.Objects.requireNonNull(tableName, "tableName");
            java.util.Objects.requireNonNull(shards, "shards");
            shards = java.util.List.copyOf(shards);
        }
    }
}
