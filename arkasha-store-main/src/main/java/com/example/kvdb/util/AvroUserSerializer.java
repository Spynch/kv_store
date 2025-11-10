package com.example.kvdb.util;

import com.example.kvdb.model.User;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class AvroUserSerializer implements Serializer<User> {

    private static final String SCHEMA_JSON = """
    {
      "type":"record",
      "name":"User",
      "namespace":"com.example.kvdb.avro",
      "fields":[
        {"name":"id","type":["null","string"],"default":null},
        {"name":"name","type":["null","string"],"default":null},
        {"name":"email","type":["null","string"],"default":null},
        {"name":"age","type":"int"},
        {"name":"active","type":"boolean"},
        {"name":"tags","type":{"type":"array","items":"string"}}
      ]
    }
    """;

    private static final Schema SCHEMA = new Schema.Parser().parse(SCHEMA_JSON);

    @Override
    public byte[] serialize(User u) {
        try {
            GenericData.Record rec = new GenericData.Record(SCHEMA);
            rec.put("id",    u.id);     // Avro сам завернёт String в Utf8
            rec.put("name",  u.name);
            rec.put("email", u.email);
            rec.put("age",   u.age);
            rec.put("active",u.active);
            rec.put("tags",  u.tags == null ? List.of() : u.tags);

            ByteArrayOutputStream out = new ByteArrayOutputStream(64);
            BinaryEncoder enc = EncoderFactory.get().binaryEncoder(out, null);
            new GenericDatumWriter<GenericData.Record>(SCHEMA).write(rec, enc);
            enc.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("Avro encode failed", e);
        }
    }

    @Override
    public User deserialize(byte[] bytes) {
        try {
            BinaryDecoder dec = DecoderFactory.get().binaryDecoder(bytes, null);
            GenericDatumReader<GenericData.Record> rdr = new GenericDatumReader<>(SCHEMA);
            GenericData.Record rec = rdr.read(null, dec);

            User u = new User();
            u.id     = asString(rec.get("id"));
            u.name   = asString(rec.get("name"));
            u.email  = asString(rec.get("email"));
            u.age    = (Integer) rec.get("age");
            u.active = (Boolean) rec.get("active");
            u.tags   = asStringList(rec.get("tags"));

            return u;
        } catch (Exception e) {
            throw new IllegalArgumentException("Avro decode failed", e);
        }
    }

    // ---- helpers ----
    private static String asString(Object v) {
        if (v == null) return null;
        // Utf8, String, CharSequence → toString()
        if (v instanceof CharSequence cs) return cs.toString();
        return String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        // Может быть GenericData.Array<Utf8> или обычный List<String>
        List<Object> raw;
        if (v instanceof List<?> list) {
            raw = (List<Object>) list;
        } else if (v instanceof GenericData.Array<?> arr) {
            raw = (List<Object>) (List<?>) new ArrayList<>(arr);
        } else {
            // на всякий случай
            return List.of();
        }
        List<String> res = new ArrayList<>(raw.size());
        for (Object o : raw) res.add(asString(o));
        return res;
    }
}
