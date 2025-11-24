package com.example.kvdb.apihttp;

import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.api.StorageEngine;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.apihttp.dto.BatchRequest;
import com.example.kvdb.apihttp.dto.BatchResponse;
import com.example.kvdb.apihttp.dto.PutManyRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class DatabaseController {

    private final StorageEngine db;

    public DatabaseController(StorageEngine db) {
        this.db = db;
    }


    @PostMapping(path = "/tables",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> createTable(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body("Table name is required");
        }
        try {
            TableOptions opts = new TableOptions().setWalEnabled(true).setMaxValueSize(-1);
            db.createTable(name, opts);
            return ResponseEntity.ok("created: " + name);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("already exists: " + name);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("error: " + e.getMessage());
        }
    }

    @GetMapping(path = "/tables", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listTables() {
        return ResponseEntity.ok(db.listTables());
    }

    @GetMapping(path = "/tables/{tableName}/keys/{key}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> get(@PathVariable String tableName, @PathVariable String key) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            byte[] v = table.get(key);
            return ResponseEntity.ok(v == null ? "" : new String(v, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Table not found: " + tableName);
        }
    }

    @PutMapping(path = "/tables/{tableName}/keys/{key}",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> put(@PathVariable String tableName,
                                 @PathVariable String key,
                                 @RequestBody String body) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            table.put(key, body.getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Table not found: " + tableName);
        }
    }

    @DeleteMapping(path = "/tables/{tableName}/keys/{key}",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> delete(@PathVariable String tableName, @PathVariable String key) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            table.delete(key);
            return ResponseEntity.ok("ok");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body("Table not found: " + tableName);
        }
    }

    @GetMapping(path = "/tables/{tableName}/items",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getManyQuery(@PathVariable String tableName,
                                          @RequestParam(name = "key") List<String> keys) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            Map<String, String> out = new HashMap<>();
            if (keys != null) {
                for (String k : keys) {
                    byte[] v = table.get(k);
                    if (v != null) out.put(k, new String(v, StandardCharsets.UTF_8));
                }
            }
            return ResponseEntity.ok(Map.of("items", out));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Table not found", "table", tableName));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getClass().getSimpleName(), "message", ex.getMessage()));
        }
    }

    @PutMapping(path="/tables/{tableName}/items",
            consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putMany(@PathVariable String tableName,
                                     @RequestBody PutManyRequest request) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            Map<String, byte[]> toPut = new HashMap<>();
            if (request.items()!=null) {
                for (var e : request.items().entrySet()) {
                    toPut.put(e.getKey(), e.getValue()==null ? null :
                            e.getValue().getBytes(StandardCharsets.UTF_8));
                }
            }
            table.putAll(toPut);
            return ResponseEntity.ok(Map.of("inserted", toPut.size()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error","Table not found","table",tableName));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getClass().getSimpleName(), "message", ex.getMessage()));
        }
    }

    @DeleteMapping(path="/tables/{tableName}/items",
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteMany(@PathVariable String tableName,
                                        @RequestParam(name = "key") List<String> keys) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            int count = (keys == null) ? 0 : keys.size();
            table.deleteAll(keys);
            return ResponseEntity.ok(Map.of("deleted", count));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error","Table not found","table",tableName));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getClass().getSimpleName(), "message", ex.getMessage()));
        }
    }

    @PostMapping(path="/tables/{tableName}/batch",
            consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> batch(@PathVariable String tableName,
                                   @RequestBody BatchRequest request) {
        try {
            KeyValueStore<byte[]> table = db.openTable(tableName);
            BatchResponse out = new BatchResponse();

            if (request.ops()!=null) {
                for (BatchRequest.Op op : request.ops()) {
                    String type = op.op()==null? "" : op.op().toLowerCase();
                    String key  = op.key();
                    try {
                        switch (type) {
                            case "put" -> {
                                if (key==null) { out.addError("put", null, "key is required"); break; }
                                String val = op.value();
                                if (val==null) { out.addError("put", key, "value is required"); break; }
                                table.put(key, val.getBytes(StandardCharsets.UTF_8));
                                out.addOk("put", key, val);
                            }
                            case "get" -> {
                                if (key==null) { out.addError("get", null, "key is required"); break; }
                                byte[] v = table.get(key);
                                out.addOk("get", key, v==null? null : new String(v, StandardCharsets.UTF_8));
                            }
                            case "delete" -> {
                                if (key==null) { out.addError("delete", null, "key is required"); break; }
                                table.delete(key);
                                out.addOk("delete", key, null);
                            }
                            default -> out.addError(type, key, "unknown op (use put|get|delete)");
                        }
                    } catch (Exception ex) {
                        out.addError(type, key, ex.getMessage());
                    }
                }
            }

            return ResponseEntity.ok(Map.of("results", out.results()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error","Table not found","table",tableName));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getClass().getSimpleName(), "message", ex.getMessage()));
        }
    }
}
