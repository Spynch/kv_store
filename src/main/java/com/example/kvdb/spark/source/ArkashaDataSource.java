package com.example.kvdb.spark.source;

import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableProvider;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.sources.DataSourceRegister;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

import java.util.Map;

public class ArkashaDataSource implements TableProvider, DataSourceRegister {

    public static final String OPTION_PATH = "path";
    public static final String OPTION_TABLE = "table";

    static final StructType DEFAULT_SCHEMA = new StructType()
            .add("key", DataTypes.StringType, false)
            .add("value", DataTypes.BinaryType, true)
            .add("value_size", DataTypes.IntegerType, false)
            .add("is_large", DataTypes.BooleanType, false);

    @Override
    public StructType inferSchema(CaseInsensitiveStringMap options) {
        return DEFAULT_SCHEMA;
    }

    @Override
    public boolean supportsExternalMetadata() {
        return true;
    }

    @Override
    public Table getTable(StructType schema,
                          Transform[] partitions,
                          Map<String, String> properties) {

        CaseInsensitiveStringMap options = new CaseInsensitiveStringMap(properties);

        String path = options.get(OPTION_PATH);
        String tableName = options.get(OPTION_TABLE);

        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(
                    "Arkasha DataSource requires option 'path'");
        }
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Arkasha DataSource requires option 'table'");
        }

        StructType effectiveSchema = (schema != null) ? schema : DEFAULT_SCHEMA;

        return new ArkashaTable(path, tableName, effectiveSchema);
    }

    @Override
    public String shortName() {
        return "arkasha";
    }
}
