package com.example.kvdb.apihttp;

import com.example.kvdb.api.DatabaseConfig;
import com.example.kvdb.api.StorageEngine;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.engine.ArkashaEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ArkashaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArkashaApiApplication.class, args);
    }

    @Bean
    public StorageEngine arkashaEngine() {
        DatabaseConfig config = new DatabaseConfig("data");
        StorageEngine db = new ArkashaEngine(config);
        if (!db.listTables().contains("test")) {
            TableOptions options = new TableOptions().setWalEnabled(true).setMaxValueSize(-1);
            db.createTable("test", options);
        }
        return db;
    }
}
