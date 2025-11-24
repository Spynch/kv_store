package com.example.kvdb.model;

import java.util.List;
import java.util.Objects;

public class User {
    public String id;          // GUID/строковый ID
    public String name;
    public String email;
    public int age;
    public boolean active;
    public List<String> tags;

    public User() {}
    public User(String id, String name, String email, int age, boolean active, List<String> tags) {
        this.id = id; this.name = name; this.email = email;
        this.age = age; this.active = active; this.tags = tags;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User u = (User) o;
        return age == u.age && active == u.active &&
                Objects.equals(id, u.id) &&
                Objects.equals(name, u.name) &&
                Objects.equals(email, u.email) &&
                Objects.equals(tags, u.tags);
    }
    @Override public int hashCode() { return Objects.hash(id, name, email, age, active, tags); }
}
