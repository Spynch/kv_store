package com.example.kvdb;

import com.example.kvdb.api.*;
import com.example.kvdb.engine.ArkashaEngine;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        DatabaseConfig config = new DatabaseConfig("data");
        StorageEngine db = new ArkashaEngine(config);

        if (!db.listTables().contains("test")) {
            db.createTable("test", new TableOptions().setWalEnabled(true).setMaxValueSize(1024));
        }

        KeyValueStore<byte[]> table = db.openTable("test");
        Scanner scanner = new Scanner(System.in);

        System.out.println("Добро пожаловать в базу Аркаша. Команды: put/get/delete/list/exit");
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("exit")) break;

            String[] parts = line.split("\\s+", 3);
            if (parts.length == 0) continue;

            switch (parts[0]) {
                case "put" -> {
                    if (parts.length < 3) {
                        System.out.println("Формат: put key value");
                        break;
                    }
                    table.put(parts[1], parts[2].getBytes(StandardCharsets.UTF_8));
                    System.out.println("OK");
                }
                case "get" -> {
                    if (parts.length < 2) {
                        System.out.println("Формат: get key");
                        break;
                    }
                    byte[] value = table.get(parts[1]);
                    if (value != null) {
                        System.out.println("= " + new String(value, StandardCharsets.UTF_8));
                    } else {
                        System.out.println("(not found)");
                    }
                }
                case "delete" -> {
                    if (parts.length < 2) {
                        System.out.println("Формат: delete key");
                        break;
                    }
                    table.delete(parts[1]);
                    System.out.println("Deleted (если существовал)");
                }
                case "list" -> {
                    System.out.println("Существующие ключи:");
                    for (String k : ((com.example.kvdb.core.InMemoryKeyValueStore) table).keys()) {
                        System.out.println("- " + k);
                    }
                }
                default -> System.out.println("Неизвестная команда: " + parts[0]);
            }
        }

        db.close();
        System.out.println("База закрыта. До свидания!");
    }
}
