package com.example.kvdb.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.upper;

public class SparkClient {

    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("ArkashaLab2")
                .getOrCreate();

        String path  = "data";
        String table = "test";

        Dataset<Row> df = spark.read()
                .format("com.example.kvdb.spark.source.ArkashaDataSource")
                .option("path", path)
                .option("table", table)
                .load();

        System.out.println("=== RAW DATA FROM ARKASHA ===");
        df.printSchema();
//        df.show(5, false);

        Dataset<Row> transformed = df
                .withColumn("upper_value", upper(col("value")))
                .select("key", "value", "upper_value", "value_size", "is_large")
                .cache();

        System.out.println("=== TRANSFORMED DATA ===");
//        transformed.show(5, false);

        String parquetPath = "lab2_parquet";
        String orcPath     = "lab2_orc";

        long w1 = System.nanoTime();
        transformed.write()
                .mode(SaveMode.Overwrite)
                .parquet(parquetPath);
        long w2 = System.nanoTime();

        long w3 = System.nanoTime();
        transformed.write()
                .mode(SaveMode.Overwrite)
                .orc(orcPath);
        long w4 = System.nanoTime();

        double parquetWriteMs = (w2 - w1) / 1e6;
        double orcWriteMs     = (w4 - w3) / 1e6;

        System.out.println("Parquet write time = " + parquetWriteMs + " ms");
        System.out.println("ORC     write time = " + orcWriteMs + " ms");

        long parquetSize = dirSize(parquetPath);
        long orcSize     = dirSize(orcPath);

        System.out.println("Parquet size = " + parquetSize + " bytes");
        System.out.println("ORC     size = " + orcSize + " bytes");


        Dataset<Row> p = spark.read().parquet(parquetPath);
        Dataset<Row> o = spark.read().orc(orcPath);

        long t1 = System.nanoTime();
        long pc = p.count();
        long t2 = System.nanoTime();

        long t3 = System.nanoTime();
        long oc = o.count();
        long t4 = System.nanoTime();

        System.out.println("Parquet count = " + pc + ", full scan read time = " + (t2 - t1) / 1e6 + " ms");
        System.out.println("ORC     count = " + oc + ", full scan read time = " + (t4 - t3) / 1e6 + " ms");

        long tp1 = System.nanoTime();
        p.select("upper_value").collect();
        long tp2 = System.nanoTime();

        long to1 = System.nanoTime();
        o.select("upper_value").collect();
        long to2 = System.nanoTime();

        System.out.println("Parquet projection (upper_value) read time = " + (tp2 - tp1) / 1e6 + " ms");
        System.out.println("ORC     projection (upper_value) read time = " + (to2 - to1) / 1e6 + " ms");

        long tfp1 = System.nanoTime();
        long parquetLargeCount = p.filter(col("is_large").equalTo(true)).count();
        long tfp2 = System.nanoTime();

        long tfo1 = System.nanoTime();
        long orcLargeCount = o.filter(col("is_large").equalTo(true)).count();
        long tfo2 = System.nanoTime();

        System.out.println("Parquet filter (is_large = true): count = " + parquetLargeCount +
                ", read time = " + (tfp2 - tfp1) / 1e6 + " ms");
        System.out.println("ORC     filter (is_large = true): count = " + orcLargeCount +
                ", read time = " + (tfo2 - tfo1) / 1e6 + " ms");

        spark.stop();
    }

    private static long dirSize(String path) {
        try {
            return Files.walk(Paths.get(path))
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось посчитать размер каталога " + path, e);
        }
    }
}
