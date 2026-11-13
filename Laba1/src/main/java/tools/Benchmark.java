package tools;

import CSV.DataGenerator;
import PartyModel.Party;
import Serialization.BinaryPartySerializer;
import Serialization.JsonPartySerializer;
import Serialization.PartyData;
import Serialization.Serializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Бенчмарк сравнения форматов сериализации для ЛР №3.
 *
 * Для каждого формата (JSON и bin):
 *   - размер файла на 10⁴ записей;
 *   - время сохранения и загрузки (среднее по 5 прогонам);
 *   - печать фрагмента файла для оценки читаемости «глазами».
 *
 * Результаты выводятся в stdout в виде блока, который подхватывается
 * генератором отчёта. Запуск: mvn -q exec:java -Dexec.mainClass=tools.Benchmark
 */
public final class Benchmark {

    private static final int WARMUP = 1;     // прогон для разогрева JVM (не в зачёт)
    private static final int ROUNDS = 5;     // среднее по 5 прогонам
    private static final int N      = 10_000;

    public static void main(String[] args) throws Exception {
        // Данные генерируем один раз — сериализуем один и тот же массив.
        List<Party> parties = DataGenerator.generateParties(N);
        Party[] arr = parties.toArray(new Party[0]);
        PartyData data = PartyData.from(arr);

        Path tmp = Files.createTempDirectory("bench-");
        Serializer<PartyData> json = new JsonPartySerializer();
        Serializer<PartyData> bin  = new BinaryPartySerializer();

        System.out.println("=== BENCHMARK START ===");
        System.out.printf("N = %d%n", N);
        System.out.println();

        BenchResult jsonR = run(json, data, tmp.resolve("bench.json"));
        BenchResult binR  = run(bin,  data, tmp.resolve("bench.bin"));

        printRow("json", jsonR);
        printRow("bin",  binR);

        System.out.println();
        System.out.println("=== JSON FRAGMENT (first 600 chars) ===");
        System.out.println(jsonR.fragment);
        System.out.println();
        System.out.println("=== BIN FRAGMENT (first 128 bytes as hex) ===");
        System.out.println(binR.fragment);
        System.out.println("=== BENCHMARK END ===");
    }

    private record BenchResult(long bytes, double saveMs, double loadMs, String fragment) {}

    private static BenchResult run(Serializer<PartyData> s, PartyData data, Path file) throws Exception {
        // Разогрев: один прогон вне зачёта.
        for (int i = 0; i < WARMUP; i++) {
            s.save(data, file);
            s.load(file);
        }

        long saveTotal = 0, loadTotal = 0;
        for (int i = 0; i < ROUNDS; i++) {
            long t0 = System.nanoTime();
            s.save(data, file);
            long t1 = System.nanoTime();
            s.load(file);
            long t2 = System.nanoTime();
            saveTotal += (t1 - t0);
            loadTotal += (t2 - t1);
        }
        long bytes = Files.size(file);
        double saveMs = saveTotal / 1_000_000.0 / ROUNDS;
        double loadMs = loadTotal / 1_000_000.0 / ROUNDS;

        String frag;
        if (s.format().equals("json")) {
            String text = Files.readString(file);
            frag = text.length() > 600 ? text.substring(0, 600) + "…" : text;
        } else {
            byte[] raw = Files.readAllBytes(file);
            int n = Math.min(raw.length, 128);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                sb.append(String.format("%02x", raw[i] & 0xff));
                if (i % 16 == 15) sb.append('\n'); else sb.append(' ');
            }
            frag = sb.toString();
        }
        return new BenchResult(bytes, saveMs, loadMs, frag);
    }

    private static void printRow(String fmt, BenchResult r) {
        System.out.printf("%-5s | size=%,10d bytes | save=%.2f ms | load=%.2f ms%n",
                fmt, r.bytes, r.saveMs, r.loadMs);
    }
}