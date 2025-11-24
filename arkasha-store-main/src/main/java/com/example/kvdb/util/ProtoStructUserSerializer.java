package com.example.kvdb.util;

import com.example.kvdb.model.User;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import java.util.ArrayList;
import java.util.List;

public class ProtoStructUserSerializer implements Serializer<User> {

    private static Value v(String s) { return Value.newBuilder().setStringValue(s).build(); }
    private static Value v(boolean b){ return Value.newBuilder().setBoolValue(b).build(); }
    private static Value v(double d) { return Value.newBuilder().setNumberValue(d).build(); }
    private static Value vNull()     { return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build(); }

    @Override
    public byte[] serialize(User u) {
        ListValue.Builder tags = ListValue.newBuilder();
        if (u.tags != null) for (String t : u.tags) tags.addValues(v(t == null ? "" : t));

        Struct s = Struct.newBuilder()
                .putFields("id",     u.id == null ? vNull() : v(u.id))
                .putFields("name",   u.name == null ? vNull() : v(u.name))
                .putFields("email",  u.email == null ? vNull() : v(u.email))
                .putFields("age",    v(u.age))                 // protobuf number is double; ок для демо
                .putFields("active", v(u.active))
                .putFields("tags",   Value.newBuilder().setListValue(tags).build())
                .build();
        return s.toByteArray();
    }

    @Override
    public User deserialize(byte[] bytes) {
        try {
            Struct s = Struct.parseFrom(bytes);
            User u = new User();
            u.id = s.getFieldsOrDefault("id", vNull()).getKindCase() == Value.KindCase.STRING_VALUE ? s.getFieldsOrThrow("id").getStringValue() : null;
            u.name = s.getFieldsOrDefault("name", vNull()).getKindCase() == Value.KindCase.STRING_VALUE ? s.getFieldsOrThrow("name").getStringValue() : null;
            u.email= s.getFieldsOrDefault("email", vNull()).getKindCase() == Value.KindCase.STRING_VALUE ? s.getFieldsOrThrow("email").getStringValue() : null;
            u.age  = (int) s.getFieldsOrDefault("age", v(0.0)).getNumberValue();
            u.active = s.getFieldsOrDefault("active", v(false)).getBoolValue();
            var lv = s.getFieldsOrDefault("tags", vNull()).getListValue();
            List<String> tags = new ArrayList<>();
            if (lv != null) for (Value val : lv.getValuesList()) tags.add(val.getStringValue());
            u.tags = tags;
            return u;
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad protobuf bytes", e);
        }
    }
}
