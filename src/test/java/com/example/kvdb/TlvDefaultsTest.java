package com.example.kvdb;

import com.example.kvdb.api.DatabaseConfig;
import com.example.kvdb.api.Table;
import com.example.kvdb.api.TableOptions;
import com.example.kvdb.api.TableRegistry;
import com.example.kvdb.api.StorageEngine;
import com.example.kvdb.engine.ArkashaEngine;
import com.example.kvdb.model.User;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TlvDefaultsTest {

    @Test
    void tlv_is_auto_bound_for_User_without_manual_register() throws Exception {
        Path dir = Files.createTempDirectory("arkasha-tlv-defaults-");
        StorageEngine db = new ArkashaEngine(new DatabaseConfig(dir.toString()));

        db.createTable("users", new TableOptions().setWalEnabled(false));

        // без register(...): движок сам подтянет TLV по типу User
        Table<User> users = ((TableRegistry) db).openTable("users", User.class);

        User in = new User("u1", "Alice", "a@x.y", 33, true, List.of("qa", "perf"));
        users.put("u1", in);

        User out = users.get("u1");
        assertThat(out).isEqualTo(in);
    }
}
