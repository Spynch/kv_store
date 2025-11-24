package com.example.kvdb.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class VarInts {
    private VarInts() {}

    public static int zigZagEncodeInt(int n) { return (n << 1) ^ (n >> 31); }
    public static int zigZagDecodeInt(int n) { return (n >>> 1) ^ -(n & 1); }

    public static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    public static int readVarInt(ByteBuffer buf) {
        int value = 0, position = 0;
        while (true) {
            byte b = buf.get();
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) break;
            position += 7;
        }
        return value;
    }

    public static void writeString(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    public static String readString(ByteBuffer buf) {
        int len = readVarInt(buf);
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
