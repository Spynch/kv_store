package com.example.kvdb.apihttp;

import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.api.StorageEngine;
import com.example.kvdb.api.TableOptions;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DatabaseController {
    private String guessMimeType(String key) {
        try {
            return Files.probeContentType(Paths.get(key));
        } catch (Exception e) {
            return null;
        }
    }

    private final StorageEngine db;
    public DatabaseController(StorageEngine db) {
        this.db = db;
    }

    // 2.1. Создание новой таблицы
    @PostMapping("/tables")
    public ResponseEntity<String> createTable(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body("Table name is required");
        }
        try {
            TableOptions opts = new TableOptions().setWalEnabled(true);
            db.createTable(name, opts);
            return ResponseEntity.status(201).body("Table '" + name + "' created");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("already exists")) {
                return ResponseEntity.status(409).body("Table already exists");
            }
            return ResponseEntity.badRequest().body("Error: " + msg);
        }
    }

    // 2.2. Получение списка таблиц
    @GetMapping("/tables")
    public List<String> listTables() {
        return db.listTables();
    }

    // 2.3. Удаление таблицы
    @DeleteMapping("/tables/{tableName}")
    public ResponseEntity<String> dropTable(@PathVariable String tableName) {
        try {
            db.dropTable(tableName);
            return ResponseEntity.ok("Table '" + tableName + "' dropped");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Table not found");
        }
    }

    // 2.4. Сохранение значения по ключу
    @PutMapping("/tables/{tableName}/keys/{key}")
    public ResponseEntity<String> putValue(
            @PathVariable String tableName,
            @PathVariable String key,
            @RequestBody(required = false) byte[] value) {
        if (value == null || value.length == 0) {
            return ResponseEntity.badRequest().body("Value is required in request body");
        }
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            table.put(key, value);
            return ResponseEntity.ok("OK");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Table not found: " + tableName);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }


    // 2.5. Получение значения по ключу
    @GetMapping("/tables/{tableName}/keys/{key}")
    public ResponseEntity<byte[]> getValue(
            @PathVariable String tableName,
            @PathVariable String key) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            byte[] result = table.get(key);
            if (result == null) {
                return ResponseEntity.status(404).build();
            }
            String mimeType = guessMimeType(key);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (mimeType != null) {
                try {
                    mediaType = MediaType.parseMediaType(mimeType);
                } catch (Exception ignored) {}
            }

            return ResponseEntity
                    .ok()
                    .contentType(mediaType)
                    .body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).build();
        }
    }

    // 2.6. Удаление значения по ключу
    @DeleteMapping("/tables/{tableName}/keys/{key}")
    public ResponseEntity<String> deleteValue(
            @PathVariable String tableName,
            @PathVariable String key) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            table.delete(key);
            return ResponseEntity.ok("Deleted (if existed)");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Table not found: " + tableName);
        }
    }

    // 2.7. (Опционально) Получить все ключи в таблице
    @GetMapping("/tables/{tableName}/keys")
    public ResponseEntity<?> listKeys(@PathVariable String tableName) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            if (table instanceof com.example.kvdb.core.InMemoryKeyValueStore memStore) {
                List<String> keys = memStore.keys();
                return ResponseEntity.ok(keys);
            } else {
                return ResponseEntity.ok("Listing keys not supported for this engine");
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Table not found: " + tableName);
        }
    }
}
