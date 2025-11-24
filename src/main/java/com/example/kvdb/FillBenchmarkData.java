package com.example.kvdb;

import com.example.kvdb.api.DatabaseConfig;
import com.example.kvdb.api.KeyValueStore;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.engine.ArkashaEngine;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

public class FillBenchmarkData {

    public static void main(String[] args) {
        String path  = "data";
        String table = "test";
        int count = 100_000;

        DatabaseConfig config = new DatabaseConfig(path);
        ArkashaEngine db = new ArkashaEngine(config);

        try {
            if (db.listTables().contains(table)) {
                System.out.println("Таблица " + table + " уже существует, пересоздаём для чистого бенчмарка");
                db.dropTable(table);
            }

            TableOptions options = new TableOptions()
                    .setWalEnabled(false)      // чтобы не тормозило
                    .setMaxValueSize(1024 * 1024);

            db.createTable(table, options);

            KeyValueStore<byte[]> kv = db.openTable(table);

            System.out.println("Начинаем заливку " + count + " ключей...");

            ThreadLocalRandom rnd = ThreadLocalRandom.current();

            for (int i = 0; i < count; i++) {
                String key = "key_" + i;
                String value = randomString(rnd, 1000, 5000);
                kv.put(key, value.getBytes(StandardCharsets.UTF_8));

                if ((i + 1) % 1000 == 0) {
                    System.out.println("Записано " + (i + 1) + " ключей");
                }
            }

            System.out.println("Готово! В таблицу " + table + " записано " + count + " ключей.");
        } finally {
            db.close();
        }
    }

    private static String randomString(ThreadLocalRandom rnd, int minLen, int maxLen) {
        int len = rnd.nextInt(minLen, maxLen + 1);
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
