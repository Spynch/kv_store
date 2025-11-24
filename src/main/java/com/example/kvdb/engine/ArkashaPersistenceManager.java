package com.example.kvdb.engine;

import com.example.kvdb.api.PersistenceManager;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.core.InMemoryKeyValueStore;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class ArkashaPersistenceManager implements PersistenceManager {
    private final ArkashaEngine engine;
    private final File snapshotFile;

    ArkashaPersistenceManager(ArkashaEngine engine, File snapshotFile) {
        this.engine = engine;
        this.snapshotFile = snapshotFile;
    }

    @Override
    public void flush() {
        try (RandomAccessFile raf = new RandomAccessFile(snapshotFile, "rw")) {
            raf.setLength(0);
            raf.writeBytes("ARKA");
            raf.writeInt(1);
            List<String> tableNames = engine.getAllTableNames();
            raf.writeInt(tableNames.size());
            for (String tableName : tableNames) {
                InMemoryKeyValueStore store = engine.getStore(tableName);
                byte[] nameBytes = tableName.getBytes(StandardCharsets.UTF_8);
                raf.writeInt(nameBytes.length);
                raf.write(nameBytes);
                TableOptions options = store.getOptions();
                raf.writeBoolean(options.isWalEnabled());
                raf.writeBoolean(options.isFsync());
                raf.writeInt(options.getMaxValueSize());
                List<String> keys = store.keys();
                raf.writeInt(keys.size());
                for (String key : keys) {
                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    byte[] value = store.get(key);
                    int valueLength = (value != null ? value.length : 0);
                    raf.writeInt(keyBytes.length);
                    raf.write(keyBytes);
                    raf.writeInt(valueLength);
                    if (valueLength > 0) {
                        raf.write(value);
                    }
                }
            }
            raf.getFD().sync();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при сохранении данных: " + e.getMessage(), e);
        }
        File walFile = ((ArkashaWriteAheadLog) engine.getWriteAheadLog()).getLogFile();
        if (walFile.exists()) {
            walFile.delete();
        }
    }

    @Override
    public void load() {
        if (!snapshotFile.exists()) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(snapshotFile, "r")) {
            byte[] magic = new byte[4];
            raf.readFully(magic);
            String magicStr = new String(magic, StandardCharsets.UTF_8);
            if (!"ARKA".equals(magicStr)) {
                throw new RuntimeException("Неверный формат файла данных");
            }
            int version = raf.readInt();
            if (version != 1) {
                throw new RuntimeException("Неподдерживаемая версия данных: " + version);
            }
            int tableCount = raf.readInt();
            for (int i = 0; i < tableCount; i++) {
                int nameLen = raf.readInt();
                byte[] nameBytes = new byte[nameLen];
                raf.readFully(nameBytes);
                String tableName = new String(nameBytes, StandardCharsets.UTF_8);
                boolean walEnabled = raf.readBoolean();
                boolean fsync = raf.readBoolean();
                int maxValueSize = raf.readInt();
                TableOptions options = new TableOptions(walEnabled, fsync, maxValueSize);
                engine.createTable(tableName, options);
                InMemoryKeyValueStore store = engine.getStore(tableName);
                int entryCount = raf.readInt();
                for (int j = 0; j < entryCount; j++) {
                    int keyLen = raf.readInt();
                    byte[] keyBytes = new byte[keyLen];
                    raf.readFully(keyBytes);
                    String key = new String(keyBytes, StandardCharsets.UTF_8);
                    int valueLen = raf.readInt();
                    byte[] value = new byte[valueLen];
                    if (valueLen > 0) {
                        raf.readFully(value);
                    }
                    store.put(key, value);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при загрузке данных: " + e.getMessage(), e);
        }
    }
}
