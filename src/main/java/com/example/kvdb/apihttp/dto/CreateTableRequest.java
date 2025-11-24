package com.example.kvdb.apihttp.dto;

public record CreateTableRequest(
        String name,
        Boolean walEnabled,
        Integer maxValueSize
) {}
