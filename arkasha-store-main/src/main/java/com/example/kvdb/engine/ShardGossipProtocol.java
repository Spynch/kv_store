package com.example.kvdb.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A lightweight gossip-style protocol that allows shards to exchange
 * their liveness information. Each shard keeps a view of the cluster
 * state and merges updates received from peers. The most recent
 * heartbeat for every shard is used as a single source of truth.
 */
class ShardGossipProtocol {

    enum ShardState {
        UP,
        DOWN
    }

    record ShardHeartbeat(String shardId, ShardState state, long version) {
        ShardHeartbeat {
            Objects.requireNonNull(shardId, "shardId");
            Objects.requireNonNull(state, "state");
        }
    }

    private final Map<String, Map<String, ShardHeartbeat>> shardViews = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, ShardHeartbeat> clusterView = Collections.emptyMap();

    void registerShard(String shardId) {
        shardViews.computeIfAbsent(shardId, k -> new ConcurrentHashMap<>());
        heartbeat(shardId, ShardState.UP);
    }

    void heartbeat(String shardId, ShardState state) {
        Map<String, ShardHeartbeat> localView = shardViews.computeIfAbsent(shardId, k -> new ConcurrentHashMap<>());
        ShardHeartbeat heartbeat = new ShardHeartbeat(shardId, state, System.nanoTime());
        localView.put(shardId, heartbeat);
        spreadState(localView, shardId);
        recomputeClusterView();
    }

    void gossipRound() {
        List<Map<String, ShardHeartbeat>> views = new ArrayList<>(shardViews.values());
        for (int i = 0; i < views.size(); i++) {
            for (int j = i + 1; j < views.size(); j++) {
                mergeView(views.get(i), views.get(j));
                mergeView(views.get(j), views.get(i));
            }
        }
        recomputeClusterView();
    }

    boolean isShardAvailable(String shardId) {
        ShardHeartbeat heartbeat = clusterView.get(shardId);
        return heartbeat == null || heartbeat.state() == ShardState.UP;
    }

    ShardState shardState(String shardId) {
        ShardHeartbeat heartbeat = clusterView.get(shardId);
        return heartbeat == null ? ShardState.UP : heartbeat.state();
    }

    List<ShardHeartbeat> getClusterState() {
        return new ArrayList<>(clusterView.values());
    }

    void addStatusListener(Runnable listener) {
        listeners.add(listener);
    }

    private void recomputeClusterView() {
        Map<String, ShardHeartbeat> merged = new HashMap<>();
        for (Map<String, ShardHeartbeat> view : shardViews.values()) {
            for (ShardHeartbeat hb : view.values()) {
                ShardHeartbeat existing = merged.get(hb.shardId());
                if (existing == null || hb.version() > existing.version()) {
                    merged.put(hb.shardId(), hb);
                }
            }
        }
        Map<String, ShardHeartbeat> previous = clusterView;
        clusterView = Collections.unmodifiableMap(merged);
        if (!previous.equals(clusterView)) {
            listeners.forEach(Runnable::run);
        }
    }

    private void spreadState(Map<String, ShardHeartbeat> fromView, String sourceShard) {
        for (Map.Entry<String, Map<String, ShardHeartbeat>> entry : shardViews.entrySet()) {
            if (entry.getKey().equals(sourceShard)) {
                continue;
            }
            mergeView(entry.getValue(), fromView);
        }
    }

    private void mergeView(Map<String, ShardHeartbeat> target, Map<String, ShardHeartbeat> source) {
        for (ShardHeartbeat hb : source.values()) {
            ShardHeartbeat existing = target.get(hb.shardId());
            if (existing == null || hb.version() > existing.version()) {
                target.put(hb.shardId(), hb);
            }
        }
    }
}
