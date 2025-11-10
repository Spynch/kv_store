package com.example.kvdb;

import com.example.kvdb.model.User;
import com.example.kvdb.util.AvroUserSerializer;
import com.example.kvdb.util.ProtoStructUserSerializer;
import com.example.kvdb.util.Serializer;
import com.example.kvdb.util.TlvUserSerializer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class FormatBenchmarks {

    // ======== Генератор с пресетами ========
    static class GenCfg {
        int n = 50_000;
        int nameMin = 5, nameMax = 15;
        int emailMin = 8, emailMax = 20;
        int tagsMin = 0, tagsMax = 2;
        double pNullId = 0.0, pNullName = 0.0, pNullEmail = 0.0;
        boolean unicode = false;
        boolean highDup = false;
        long seed = 42L;
        IntDistribution ageDist = IntDistribution.uniform(16, 80);
        IntDistribution tagLenDist = IntDistribution.uniform(4, 8);
        String presetName = "custom";
    }

    interface IntDistribution {
        int sample(ThreadLocalRandom r);
        static IntDistribution uniform(int a,int b){ return r->r.nextInt(a,b+1); }
    }

    static GenCfg cfg(String name, Consumer<GenCfg> f){
        GenCfg c=new GenCfg();
        c.presetName = name;
        f.accept(c);
        return c;
    }

    static void normalize(GenCfg c) {
        if (c.nameMin > c.nameMax)  { int t=c.nameMin;  c.nameMin=c.nameMax;  c.nameMax=t; }
        if (c.emailMin > c.emailMax){ int t=c.emailMin; c.emailMin=c.emailMax; c.emailMax=t; }
        if (c.tagsMin > c.tagsMax)  { int t=c.tagsMin;  c.tagsMin=c.tagsMax;  c.tagsMax=t; }
    }

    static List<User> generate(GenCfg cfg) {
        normalize(cfg);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        List<String> namesDict = cfg.highDup ? prefillNames(200, cfg) : null;
        List<String> tagsDict  = cfg.highDup ? prefillTags(300, cfg)  : null;

        List<User> list = new ArrayList<>(cfg.n);
        for (int i = 0; i < cfg.n; i++) {
            boolean active = rnd.nextBoolean();
            int age = cfg.ageDist.sample(rnd);

            String id = rnd.nextDouble() < cfg.pNullId ? null : UUID.randomUUID().toString();
            String name = rnd.nextDouble() < cfg.pNullName ? null :
                    (cfg.highDup ? pick(namesDict, rnd) : randString(rnd, cfg.nameMin, cfg.nameMax, cfg.unicode));
            String email = rnd.nextDouble() < cfg.pNullEmail ? null :
                    (cfg.highDup ? (pick(namesDict, rnd).toLowerCase() + "@ex.com")
                            : (randString(rnd, cfg.emailMin, cfg.emailMax, false).toLowerCase() + "@example.com"));

            int tcount = rnd.nextInt(cfg.tagsMin, cfg.tagsMax + 1);
            List<String> tags = new ArrayList<>(tcount);
            for (int t = 0; t < tcount; t++) {
                if (cfg.highDup) {
                    tags.add(pick(tagsDict, rnd));
                } else {
                    int tlen = cfg.tagLenDist.sample(rnd); // один сэмпл длины
                    tags.add(randString(rnd, tlen, tlen, cfg.unicode));
                }
            }
            list.add(new User(id, name, email, age, active, tags));
        }
        return list;
    }

    static List<String> prefillNames(int n, GenCfg cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> d = new ArrayList<>(n);
        for (int i=0;i<n;i++) d.add(randString(rnd, cfg.nameMin, cfg.nameMax, cfg.unicode));
        return d;
    }
    static List<String> prefillTags(int n, GenCfg cfg) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> d = new ArrayList<>(n);
        for (int i=0;i<n;i++) {
            int tlen = cfg.tagLenDist.sample(rnd);
            d.add(randString(rnd, tlen, tlen, cfg.unicode));
        }
        return d;
    }
    static <T> T pick(List<T> list, ThreadLocalRandom rnd) { return list.get(rnd.nextInt(list.size())); }

    static String randString(ThreadLocalRandom rnd, int minLen, int maxLen, boolean unicode) {
        if (minLen > maxLen) { int tmp = minLen; minLen = maxLen; maxLen = tmp; }
        if (minLen < 0) minLen = 0;
        int len = (minLen == maxLen) ? minLen : rnd.nextInt(minLen, maxLen + 1);

        StringBuilder sb = new StringBuilder(len);
        if (!unicode) {
            String abc = "abcdefghijklmnopqrstuvwxyz";
            for (int i=0;i<len;i++) sb.append(abc.charAt(rnd.nextInt(abc.length())));
        } else {
            String cyr = "абвгдезийклмнопрстуфхцчшщьяю";
            for (int i=0;i<len;i++) {
                if (rnd.nextDouble() < 0.1) {
                    int codePoint = 0x1F600 + rnd.nextInt(0x70); // эмодзи
                    sb.appendCodePoint(codePoint);
                } else {
                    sb.append(cyr.charAt(rnd.nextInt(cyr.length())));
                }
            }
        }
        return sb.toString();
    }

    // ======== Бенчинг ========
    record Result(String preset, int N, String format, double kbPerRec, double encKrecPerSec, double decKrecPerSec){}

    public static void main(String[] args) throws Exception {
        int[] Ns = (args.length > 0)
                ? Arrays.stream(args[0].split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray()
                : new int[]{10_000, 100_000, 500_000};

        List<Map.Entry<String, Serializer<User>>> impls = List.of(
                Map.entry("Protobuf(Struct)", (Serializer<User>) new ProtoStructUserSerializer()),
                Map.entry("Avro(Generic)",    (Serializer<User>) new AvroUserSerializer()),
                Map.entry("Custom(TLV)",      (Serializer<User>) new TlvUserSerializer())
        );

        List<GenCfg> presets = List.of(
                cfg("A.Tiny-ASCII", c -> { c.n=50_000; c.tagsMin=0; c.tagsMax=1; }),
                cfg("B.Long-Strings", c -> { c.n=50_000; c.nameMin=40; c.nameMax=80; c.emailMin=30; c.emailMax=60; c.tagsMin=3; c.tagsMax=6; c.tagLenDist=IntDistribution.uniform(20,60); }),
                cfg("C.Many-Nulls", c -> { c.n=50_000; c.pNullId=0.7; c.pNullName=0.7; c.pNullEmail=0.7; c.tagsMin=0; c.tagsMax=1; }),
                cfg("D.Heavy-Lists", c -> { c.n=50_000; c.tagsMin=15; c.tagsMax=40; c.tagLenDist=IntDistribution.uniform(3,8); }),
                cfg("E.Unicode/Emoji", c -> { c.n=50_000; c.unicode=true; c.tagsMin=2; c.tagsMax=5; }),
                cfg("F.High-Duplication", c -> { c.n=50_000; c.highDup=true; c.tagsMin=2; c.tagsMax=5; }),
                cfg("G.Skewed-Ages", c -> { c.n=50_000; c.ageDist = r -> {
                    double u = r.nextDouble();
                    if (u < 0.80) return r.nextInt(16, 40);
                    if (u < 0.95) return r.nextInt(40, 80);
                    return r.nextInt(80, 400);
                };}),
                cfg("H.Mixed-Realistic", c -> { c.n=50_000; c.tagsMin=2; c.tagsMax=5; c.pNullEmail=0.15; c.pNullName=0.10; })
        );

        List<Result> all = new ArrayList<>();

        for (GenCfg base : presets) {
            System.out.println("\n=== Preset: " + base.presetName + " ===");
            for (int N : Ns) {
                GenCfg runCfg = cloneForN(base, N);
                normalize(runCfg);
                List<User> users = generate(runCfg);

                // Прогрев (JIT warm-up)
                for (var e : impls) warmup(e.getValue(), users, 1_000);

                // Прогоны
                for (var e : impls) {
                    var r = runOnce(runCfg.presetName, e.getKey(), e.getValue(), users);
                    all.add(new Result(runCfg.presetName, N, e.getKey(), r.kbPerRec, r.encKrec, r.decKrec));
                    System.out.printf("N=%-8d %-18s | size: %8.2f KB/rec | enc: %7.1f Krec/s | dec: %7.1f Krec/s%n",
                            N, e.getKey(), r.kbPerRec, r.encKrec, r.decKrec);
                }

                // Лучшие по метрикам
                bestOfPrint(all, base.presetName, N);
            }
        }

        writeCsv(all, "bench_results.csv");
        writeMarkdown(all, "bench_results.md");
        System.out.println("\nSaved: bench_results.csv, bench_results.md");
    }

    static GenCfg cloneForN(GenCfg c, int N) {
        GenCfg x = new GenCfg();
        x.n=N; x.nameMin=c.nameMin; x.nameMax=c.nameMax; x.emailMin=c.emailMin; x.emailMax=c.emailMax;
        x.tagsMin=c.tagsMin; x.tagsMax=c.tagsMax; x.pNullId=c.pNullId; x.pNullName=c.pNullName; x.pNullEmail=c.pNullEmail;
        x.unicode=c.unicode; x.highDup=c.highDup; x.seed=c.seed; x.ageDist=c.ageDist; x.tagLenDist=c.tagLenDist;
        x.presetName=c.presetName;
        return x;
    }

    static void warmup(Serializer<User> ser, List<User> users, int w) {
        int n = Math.min(w, users.size());
        List<byte[]> tmp = new ArrayList<>(n);
        for (int i=0;i<n;i++) tmp.add(ser.serialize(users.get(i)));
        long sum=0; for (int i=0;i<n;i++) sum += ser.deserialize(tmp.get(i)).age;
        if (sum==42) System.out.print(""); // чтобы компилятор не выкинул
    }

    static class RunStats { double kbPerRec, encKrec, decKrec; }
    static RunStats runOnce(String preset, String name, Serializer<User> ser, List<User> users) {
        int N = users.size();
        // encode
        Instant t0 = Instant.now();
        List<byte[]> encoded = new ArrayList<>(N);
        long totalBytes = 0;
        for (User u : users) {
            byte[] b = ser.serialize(u);
            totalBytes += b.length;
            encoded.add(b);
        }
        long encMs = Math.max(1, Duration.between(t0, Instant.now()).toMillis());

        // decode + валидность
        Instant t1 = Instant.now();
        long check = 0;
        for (int i=0;i<N;i++) {
            User u = ser.deserialize(encoded.get(i));
            if ((i & 1023)==0) assertEqualLoose(users.get(i), u, name); // лёгкая проверка
            check += (u.age + (u.active ? 1 : 0));
        }
        long decMs = Math.max(1, Duration.between(t1, Instant.now()).toMillis());

        RunStats rs = new RunStats();
        rs.kbPerRec = (totalBytes / 1024.0) / N;
        rs.encKrec  = N / (double) encMs;   // Krec/s ≈ N / ms
        rs.decKrec  = N / (double) decMs;
        if (check == 0xDEADBEEFL) System.out.print(""); // не дать DCE убрать
        return rs;
    }

    static void assertEqualLoose(User a, User b, String fmt) {
        if (!Objects.equals(a.id, b.id)) throw new AssertionError(fmt + " id mismatch");
        if (!Objects.equals(a.name, b.name)) throw new AssertionError(fmt + " name mismatch");
        if (!Objects.equals(a.email, b.email)) throw new AssertionError(fmt + " email mismatch");
        if (a.age != b.age) throw new AssertionError(fmt + " age mismatch");
        if (a.active != b.active) throw new AssertionError(fmt + " active mismatch");
        List<String> at = a.tags == null ? List.of() : a.tags;
        List<String> bt = b.tags == null ? List.of() : b.tags;
        if (at.size() != bt.size()) throw new AssertionError(fmt + " tags length mismatch");
        for (int i=0;i<at.size();i++) if (!Objects.equals(at.get(i), bt.get(i)))
            throw new AssertionError(fmt + " tags["+i+"] mismatch");
    }

    static void bestOfPrint(List<Result> all, String preset, int N) {
        List<Result> subset = all.stream().filter(r -> r.preset.equals(preset) && r.N==N).toList();
        if (subset.isEmpty()) return;
        Result bestSize = subset.stream().min(Comparator.comparingDouble(Result::kbPerRec)).get();
        Result bestEnc  = subset.stream().max(Comparator.comparingDouble(Result::encKrecPerSec)).get();
        Result bestDec  = subset.stream().max(Comparator.comparingDouble(Result::decKrecPerSec)).get();
        System.out.printf("→ Best size: %s (%.2f KB/rec) | Best enc: %s (%.1f Krec/s) | Best dec: %s (%.1f Krec/s)%n",
                bestSize.format, bestSize.kbPerRec, bestEnc.format, bestEnc.encKrecPerSec, bestDec.format, bestDec.decKrecPerSec);
    }

    static void writeCsv(List<Result> all, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {
            pw.println("preset,N,format,kb_per_rec,enc_krec_s,dec_krec_s");
            for (var r : all) {
                pw.printf(Locale.US, "%s,%d,%s,%.4f,%.2f,%.2f%n",
                        r.preset, r.N, r.format, r.kbPerRec, r.encKrecPerSec, r.decKrecPerSec);
            }
        }
    }

    static void writeMarkdown(List<Result> all, String path) throws Exception {
        Map<String, List<Result>> groups = new LinkedHashMap<>();
        for (var r : all) {
            String key = r.preset + " | N=" + r.N;
            groups.computeIfAbsent(key, k->new ArrayList<>()).add(r);
        }
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {
            pw.println("# KV Formats Benchmark\n");
            for (var e : groups.entrySet()) {
                pw.printf("## %s%n%n", e.getKey());
                pw.println("| Format | KB/rec | Enc Krec/s | Dec Krec/s |");
                pw.println("|---|---:|---:|---:|");
                List<Result> list = e.getValue();
                list.sort(Comparator.comparingDouble(Result::kbPerRec)); // по размеру
                for (var r : list) {
                    pw.printf(Locale.US, "| %s | %.4f | %.1f | %.1f |%n",
                            r.format, r.kbPerRec, r.encKrecPerSec, r.decKrecPerSec);
                }
                Result bestSize = list.stream().min(Comparator.comparingDouble(Result::kbPerRec)).get();
                Result bestEnc  = list.stream().max(Comparator.comparingDouble(Result::encKrecPerSec)).get();
                Result bestDec  = list.stream().max(Comparator.comparingDouble(Result::decKrecPerSec)).get();
                pw.printf("%n**Best size:** %s (%.2f KB/rec) — **Best enc:** %s (%.1f Krec/s) — **Best dec:** %s (%.1f Krec/s)%n%n",
                        bestSize.format, bestSize.kbPerRec, bestEnc.format, bestEnc.encKrecPerSec, bestDec.format, bestDec.decKrecPerSec);
            }
        }
    }
}
