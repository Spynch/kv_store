package com.example.kvdb.apihttp.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchResponse {
    public static class Result {
        public String op;
        public String key;
        public String value;
        public String status;
        public String error;
    }
    private final List<Result> results = new ArrayList<>();
    public void addOk(String op, String key, String value) {
        Result r = new Result(); r.op=op; r.key=key; r.value=value; r.status="ok"; results.add(r);
    }
    public void addError(String op, String key, String msg) {
        Result r = new Result(); r.op=op; r.key=key; r.status="error"; r.error=msg; results.add(r);
    }
    public List<Result> results() { return results; }
}
