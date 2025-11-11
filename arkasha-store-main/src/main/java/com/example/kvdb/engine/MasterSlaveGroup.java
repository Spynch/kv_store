package com.example.kvdb.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class MasterSlaveGroup {
    private final ClusterNode master;
    private final List<ClusterNode> slaves;

    MasterSlaveGroup(ClusterNode master, List<ClusterNode> slaves) {
        this.master = master;
        this.slaves = new ArrayList<>(slaves);
    }

    ClusterNode getMaster() {
        return master;
    }

    List<ClusterNode> getSlaves() {
        return Collections.unmodifiableList(slaves);
    }
}
