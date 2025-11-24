package com.example.kvdb.engine;
import com.example.kvdb.core.InMemoryKeyValueStore;

import com.example.kvdb.api.WriteAheadLog;
import com.example.kvdb.api.TableOptions;

import java.io.*;

public class ArkashaWriteAheadLog implements WriteAheadLog {
    private final ArkashaEngine engine;
    private final File logFile;
    private boolean active = true;

    ArkashaWriteAheadLog(ArkashaEngine engine, File logFile) {
        this.engine = engine;
        this.logFile = logFile;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void setActive(boolean active) {
        this.active = active;
    }

    File getLogFile() {
        return logFile;
    }

    @Override
    public synchronized void append(LogRecord record) {
        if (!active || record == null) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "rw")) {
            raf.seek(logFile.length());
            raf.writeByte(record.type == LogRecord.Type.PUT ? 1 : 2);
            byte[] tableBytes = record.tableName.getBytes("UTF-8");
            raf.writeInt(tableBytes.length);
            raf.write(tableBytes);
            byte[] keyBytes = record.key.getBytes("UTF-8");
            raf.writeInt(keyBytes.length);
            raf.write(keyBytes);
            if (record.type == LogRecord.Type.PUT) {
                byte[] valueBytes = record.value;
                raf.writeInt(valueBytes.length);
                raf.write(valueBytes);
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при записи WAL: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void sync() {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "rw")) {
            raf.getFD().sync();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при fsync WAL: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void replay() {
        replay(engine);
    }

    synchronized int replay(ArkashaEngine engine) {
        if (!logFile.exists()) {
            return 0;
        }
        int count = 0;
        try (DataInputStream in = new DataInputStream(new FileInputStream(logFile))) {
            while (true) {
                byte op;
                try {
                    op = in.readByte();
                } catch (EOFException eof) {
                    break;
                }
                count++;
                int tableNameLen = in.readInt();
                byte[] tableNameBytes = new byte[tableNameLen];
                in.readFully(tableNameBytes);
                String tableName = new String(tableNameBytes, "UTF-8");
                int keyLen = in.readInt();
                byte[] keyBytes = new byte[keyLen];
                in.readFully(keyBytes);
                String key = new String(keyBytes, "UTF-8");
                if (op == 1) { // PUT
                    int valueLen = in.readInt();
                    byte[] valueBytes = new byte[valueLen];
                    if (valueLen > 0) {
                        in.readFully(valueBytes);
                    }
                    if (engine.getTableOptions(tableName) == null) {
                        engine.createTable(tableName, new TableOptions(true, false, -1));
                    }
                    InMemoryKeyValueStore store = engine.getStore(tableName);
                    boolean wasActive = isActive();
                    if (wasActive) {
                        setActive(false);
                    }
                    store.put(key, valueBytes);
                    if (wasActive) {
                        setActive(true);
                    }
                } else if (op == 2) { // DELETE
                    if (engine.getTableOptions(tableName) != null) {
                        InMemoryKeyValueStore store = engine.getStore(tableName);
                        boolean wasActive = isActive();
                        if (wasActive) {
                            setActive(false);
                        }
                        store.delete(key);
                        if (wasActive) {
                            setActive(true);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            return 0;
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при восстановлении из WAL: " + e.getMessage(), e);
        }
        return count;
    }
}
