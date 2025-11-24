package com.example.kvdb.api;


public class TableOptions {
    private boolean walEnabled;
    private boolean fsync;
    private int maxValueSize;

    public TableOptions() {
        this.walEnabled = false;
        this.fsync = false;
        this.maxValueSize = -1;
    }

    public TableOptions(boolean walEnabled, boolean fsync, int maxValueSize) {
        this.walEnabled = walEnabled;
        this.fsync = fsync;
        this.maxValueSize = maxValueSize;
    }

    public boolean isWalEnabled() {
        return walEnabled;
    }

    public TableOptions setWalEnabled(boolean walEnabled) {
        this.walEnabled = walEnabled;
        return this;
    }

    public boolean isFsync() {
        return fsync;
    }

    public TableOptions setFsync(boolean fsync) {
        this.fsync = fsync;
        return this;
    }

    public int getMaxValueSize() {
        return maxValueSize;
    }

    public TableOptions setMaxValueSize(int maxValueSize) {
        this.maxValueSize = maxValueSize;
        return this;
    }
}
