package com.example.kvdb.util;

import com.example.kvdb.model.User;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * TLV кодек.
 * Tags: 1=id, 2=name, 3=email, 4=age, 5=active, 6=tags (list<string>)
 * WireType:
 *   0 = varint (age, active)
 *   1 = string (UTF-8, length-varint + bytes)
 *   2 = list<string> (varint count + [strings])
 *   3 = null (для строковых полей id/name/email, без payload)
 */
public class TlvUserSerializer implements Serializer<User> {

    private static final int WT_VARINT = 0;
    private static final int WT_STRING = 1;
    private static final int WT_LIST   = 2;
    private static final int WT_NULL   = 3; // новый явный маркер null

    @Override
    public byte[] serialize(User u) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);

        // id
        VarInts.writeVarInt(out, 1);
        if (u.id == null) {
            out.write(WT_NULL);
        } else {
            out.write(WT_STRING);
            VarInts.writeString(out, u.id);
        }

        // name
        VarInts.writeVarInt(out, 2);
        if (u.name == null) {
            out.write(WT_NULL);
        } else {
            out.write(WT_STRING);
            VarInts.writeString(out, u.name);
        }

        // email
        VarInts.writeVarInt(out, 3);
        if (u.email == null) {
            out.write(WT_NULL);
        } else {
            out.write(WT_STRING);
            VarInts.writeString(out, u.email);
        }

        // age (zigzag varint)
        VarInts.writeVarInt(out, 4);
        out.write(WT_VARINT);
        VarInts.writeVarInt(out, VarInts.zigZagEncodeInt(u.age));

        // active (0/1)
        VarInts.writeVarInt(out, 5);
        out.write(WT_VARINT);
        VarInts.writeVarInt(out, u.active ? 1 : 0);

        // tags (list<string>)
        List<String> tags = (u.tags == null ? List.of() : u.tags);
        VarInts.writeVarInt(out, 6);
        out.write(WT_LIST);
        VarInts.writeVarInt(out, tags.size());
        for (String t : tags) {
            VarInts.writeString(out, t == null ? "" : t); // пустая строка — допустимая метка
        }

        return out.toByteArray();
    }

    @Override
    public User deserialize(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        User u = new User();
        u.tags = new ArrayList<>();

        while (buf.hasRemaining()) {
            int tag = VarInts.readVarInt(buf);
            int wt  = buf.get() & 0xFF;

            switch (tag) {
                case 1 -> { // id
                    if (wt == WT_NULL) {
                        u.id = null;
                    } else if (wt == WT_STRING) {
                        u.id = VarInts.readString(buf);
                    } else {
                        throw bad(tag, wt);
                    }
                }
                case 2 -> { // name
                    if (wt == WT_NULL) {
                        u.name = null;
                    } else if (wt == WT_STRING) {
                        u.name = VarInts.readString(buf);
                    } else {
                        throw bad(tag, wt);
                    }
                }
                case 3 -> { // email
                    if (wt == WT_NULL) {
                        u.email = null;
                    } else if (wt == WT_STRING) {
                        u.email = VarInts.readString(buf);
                    } else {
                        throw bad(tag, wt);
                    }
                }
                case 4 -> { // age
                    if (wt != WT_VARINT) throw bad(tag, wt);
                    u.age = VarInts.zigZagDecodeInt(VarInts.readVarInt(buf));
                }
                case 5 -> { // active
                    if (wt != WT_VARINT) throw bad(tag, wt);
                    u.active = (VarInts.readVarInt(buf) != 0);
                }
                case 6 -> { // tags
                    if (wt != WT_LIST) throw bad(tag, wt);
                    int n = VarInts.readVarInt(buf);
                    for (int i = 0; i < n; i++) {
                        u.tags.add(VarInts.readString(buf)); // "" остаётся пустой строкой
                    }
                }
                default -> throw new IllegalArgumentException("Unknown tag: " + tag + " (wt=" + wt + ")");
            }
        }
        return u;
    }

    private static IllegalArgumentException bad(int tag, int wt) {
        return new IllegalArgumentException("Unexpected wire type " + wt + " for tag " + tag);
    }
}
