package com.example.kvdb.api;


public interface WriteAheadLog {

    void append(LogRecord record);

    void sync();

    void replay();


    class LogRecord {
        public enum Type { PUT, DELETE }
        public final Type type;
        public final String tableName;
        public final String key;
        public final byte[] value;

        private LogRecord(Type type, String tableName, String key, byte[] value) {
            this.type = type;
            this.tableName = tableName;
            this.key = key;
            this.value = value;
        }

        public static LogRecord put(String tableName, String key, byte[] value) {
            return new LogRecord(Type.PUT, tableName, key, value);
        }

        public static LogRecord delete(String tableName, String key) {
            return new LogRecord(Type.DELETE, tableName, key, null);
        }
    }
}
