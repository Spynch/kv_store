package com.example.kvdb.apihttp.dto;
import java.util.List;

public record BatchRequest(List<Op> ops) {
    public record Op(String op, String key, String value) {}
}
