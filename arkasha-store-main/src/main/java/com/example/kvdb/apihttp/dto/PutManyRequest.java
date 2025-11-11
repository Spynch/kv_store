package com.example.kvdb.apihttp.dto;

import java.util.Map;

public record PutManyRequest(Map<String, String> items) {}
