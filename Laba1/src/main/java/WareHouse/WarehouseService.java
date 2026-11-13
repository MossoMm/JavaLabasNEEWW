package WareHouse;

import CSV.CSVLoadException;
import CSV.CSVUtil;
import PartyModel.AppConstants;
import PartyModel.Party;
import Serialization.PartyData;
import Serialization.SerializationException;
import Serialization.Serializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервисный слой. Вся бизнес-логика — здесь, GUI её не дублирует.
 * Тяжёлые методы (loadFromFile / saveToFile) вызывать НЕ на UI-потоке.
 */
public class WarehouseService {

    private final WareHouse warehouse;

    public WarehouseService(WareHouse warehouse) {
        this.warehouse = warehouse;
    }

    public record LoadSummary(int loaded, int duplicates,
                              List<CSVLoadException> errors, String filePath) {
        public int totalSkipped()  { return errors.size() + duplicates; }
        public boolean hasIssues() { return !errors.isEmpty() || duplicates > 0; }

        public Map<CSVLoadException.ErrorCode, Integer> errorBreakdown() {
            return errors.stream().collect(Collectors.groupingBy(
                    CSVLoadException::getCode,
                    Collectors.summingInt(e -> 1)));
        }
    }

    // Добавлено поле probedIndices для передачи данных о визуализации в GUI
    public record AddResult(Party party, int probes, int[] probedIndices, boolean wasDuplicate) {}
    public record SaveResult(int saved, String filePath) {}
    // Добавлено поле probedIndices для передачи данных о визуализации в GUI
    public record DeleteOutcome(String article, boolean deleted, int probes, int[] probedIndices) {}

    public LoadSummary loadFromFile(File file) throws IOException {
        // Сначала грузим, только потом трогаем склад — если файл битый,
        // текущее состояние остаётся нетронутым.
        CSVUtil.LoadResult result = CSVUtil.load(file.getAbsolutePath());

        warehouse.clear();
        int dups = 0;
        for (Party p : result.parties()) {
            if (warehouse.get(p.getArticle()).found()) {
                dups++;
            } else {
                warehouse.put(p);
            }
        }
        return new LoadSummary(warehouse.size(), dups, result.errors(), file.getAbsolutePath());
    }

    public SaveResult saveToFile(File file) throws IOException {
        String path = ensureCsvExtension(file.getAbsolutePath());
        CSVUtil.save(path, warehouse.all());
        return new SaveResult(warehouse.size(), path);
    }

    public AddResult addParty(Party party) {
        var check = warehouse.get(party.getArticle());
        if (check.found()) {
            return new AddResult(party, check.probes(), check.probedIndices(), true);
        }
        var r = warehouse.put(party);
        return new AddResult(party, r.probes(), r.probedIndices(), false);
    }

    public AddResult updateParty(Party party) {
        var r = warehouse.put(party);
        return new AddResult(party, r.probes(), r.probedIndices(), false);
    }

    public DeleteOutcome deleteByArticle(String article) {
        var r = warehouse.delete(article);
        return new DeleteOutcome(article, r.found(), r.probes(), r.probedIndices());
    }

    public HashTable.TableSnapshot getSnapshot() {
        return warehouse.getSnapshot();
    }

    public Party[] all() { return warehouse.all(); }
    public int size()         { return warehouse.size(); }
    public int capacity()     { return warehouse.capacity(); }
    public double loadFactor(){ return warehouse.loadFactor(); }
    public double avgPutProbes()    { return warehouse.avgPutProbes(); }
    public double avgGetProbes()    { return warehouse.avgGetProbes(); }
    public double avgDeleteProbes() { return warehouse.avgDeleteProbes(); }

    /** Очистить склад. Удобно для тестов и для «импорт с заменой». */
    public void clear() { warehouse.clear(); }

    private String ensureCsvExtension(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(AppConstants.CSV_EXTENSION)
                ? path : path + AppConstants.CSV_EXTENSION;
    }

    // ----------------------- Сериализация (Лаб. 3) -----------------------

    /** Результат экспорта: сколько записей сохранено, в какой файл, в каком формате. */
    public record ExportResult(int saved, String filePath, String format) {}

    /**
     * Результат импорта. {@code loaded} — сколько партий реально попало в склад
     * (после отсева дубликатов), {@code duplicates} — сколько было пропущено как
     * дубликаты по артикулу, {@code replaced} — {@code true}, если импорт очищал
     * склад перед загрузкой (импорт, а не слияние).
     */
    public record ImportResult(int loaded, int duplicates, boolean replaced,
                               String filePath, String format) {}

    /**
     * Сохранить состояние склада в файл через выбранный {@link Serializer}.
     * Тяжёлый метод — вызывать не на UI-потоке.
     */
    public ExportResult exportToFile(Serializer<PartyData> serializer, File file)
            throws IOException, SerializationException {
        Party[] all = warehouse.all();
        PartyData data = PartyData.from(all);
        Path target = ensureExtension(file.toPath(), serializer.extension());
        serializer.save(data, target);
        return new ExportResult(all.length, target.toString(), serializer.format());
    }

    /**
     * Загрузить склад из файла через выбранный {@link Serializer}.
     * Перед загрузкой склад очищается — это «импорт», а не «слияние».
     *
     * @throws SerializationException при битом файле, неверной сигнатуре,
     *         неснятой версии формата и т. п.
     */
    public ImportResult importFromFile(Serializer<PartyData> serializer, File file)
            throws IOException, SerializationException {
        PartyData data = serializer.load(file.toPath());
        Party[] parties = data.toParties();

        warehouse.clear();
        int dups = 0;
        for (Party p : parties) {
            if (warehouse.get(p.getArticle()).found()) {
                dups++;
            } else {
                warehouse.put(p);
            }
        }
        return new ImportResult(warehouse.size(), dups, true,
                file.getAbsolutePath(), serializer.format());
    }

    private static Path ensureExtension(Path path, String ext) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(ext) ? path
                : path.resolveSibling(path.getFileName() + ext);
    }
}