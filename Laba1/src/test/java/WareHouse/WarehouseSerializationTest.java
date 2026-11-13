package WareHouse;

import PartyModel.ArchiveParty;
import PartyModel.ImportParty;
import PartyModel.Party;
import PartyModel.RegularParty;
import Serialization.BinaryPartySerializer;
import Serialization.JsonPartySerializer;
import Serialization.PartyData;
import Serialization.SerializationException;
import Serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты экспорта/импорта склада через WarehouseService
 * и общий интерфейс Serializer.

 * Проверяет, что GUI-уровень (сервис) корректно работает с обоими форматами,
 * и что после импорта склад содержит те же данные, что были до экспорта.
 */
class WarehouseSerializationTest {

    @TempDir
    Path tmp;

    private final WarehouseService service = new WarehouseService(new WareHouse());

    private void fillSample(WarehouseService s) {
        s.addParty(new RegularParty("ART-001", "Деталь А", 10, "A-1-1",
                LocalDate.of(2025, 1, 15)));
        s.addParty(new ArchiveParty("ART-003", "Блок В", 5, "C-3-4",
                LocalDate.of(2024, 6, 10)));
        s.addParty(new ImportParty("ART-100", "Компонент Г", 50, "D-4-5",
                LocalDate.of(2025, 3, 1), "Китай", "53010000"));
    }

    /** Полный roundtrip через сервис в JSON — содержимое склада совпадает. */
    @Test
    void exportImportJson_preservesWarehouse() throws Exception {
        fillSample(service);
        File json = tmp.resolve("warehouse.json").toFile();

        var exp = service.exportToFile(new JsonPartySerializer(), json);
        assertEquals(3, exp.saved());
        assertEquals("json", exp.format());

        // Очищаем склад и заливаем обратно.
        service.clear();
        assertEquals(0, service.size());

        var imp = service.importFromFile(new JsonPartySerializer(), json);
        assertEquals(3, imp.loaded());
        assertEquals(0, imp.duplicates());

        Party[] restored = service.all();
        assertEquals(3, restored.length);
        // Импортная партия сохранила доп. поля.
        ImportParty impParty = (ImportParty) java.util.Arrays.stream(restored)
                .filter(p -> p instanceof ImportParty)
                .findFirst().orElseThrow();
        assertEquals("Китай", impParty.getCountry());
        assertEquals("53010000", impParty.getCustomsCode());
    }

    /** Полный roundtrip через сервис в бинарный формат. */
    @Test
    void exportImportBinary_preservesWarehouse() throws Exception {
        fillSample(service);
        File bin = tmp.resolve("warehouse.bin").toFile();

        var exp = service.exportToFile(new BinaryPartySerializer(), bin);
        assertEquals(3, exp.saved());
        assertEquals("bin", exp.format());

        service.clear();
        var imp = service.importFromFile(new BinaryPartySerializer(), bin);
        assertEquals(3, imp.loaded());
        assertEquals(0, imp.duplicates());

        Party[] restored = service.all();
        assertEquals(3, restored.length);
        // Проверяем регулярную партию.
        RegularParty reg = (RegularParty) java.util.Arrays.stream(restored)
                .filter(p -> p instanceof RegularParty)
                .findFirst().orElseThrow();
        assertEquals("ART-001", reg.getArticle());
        assertEquals(10, reg.getQuantity());
    }

    /** Импорт с дубликатами по артикулу — дубликаты отсекаются. */
    @Test
    void importWithDuplicates_skipsDuplicates() throws Exception {
        // Один и тот же артикул встречается дважды.
        var items = new java.util.ArrayList<PartyData.Item>();
        items.add(new PartyData.Item("REGULAR", "ART-001", "Деталь А", 10,
                "A-1-1", LocalDate.of(2025, 1, 15), null, null));
        items.add(new PartyData.Item("REGULAR", "ART-001", "Дубль", 99,
                "Z-9-9", LocalDate.of(2025, 1, 15), null, null));
        File json = tmp.resolve("dups.json").toFile();
        new JsonPartySerializer().save(new PartyData(items), json.toPath());

        var imp = service.importFromFile(new JsonPartySerializer(), json);
        assertEquals(1, imp.loaded());
        assertEquals(1, imp.duplicates());
    }

    /** Импорт из несуществующего файла выбрасывает исключение, склад не тронут. */
    @Test
    void importFromMissingFile_throwsAndKeepsState() throws Exception {
        fillSample(service);
        int before = service.size();

        File missing = tmp.resolve("does-not-exist.json").toFile();
        SerializationException ex = assertThrows(SerializationException.class, () ->
                service.importFromFile(new JsonPartySerializer(), missing));
        assertEquals(SerializationException.ErrorCode.EMPTY_FILE, ex.getCode());

        // Склад не должен был измениться (исключение брошено до clear).
        assertEquals(before, service.size());
    }

    /** Импорт битого бинарного файла выбрасывает SerializationException. */
    @Test
    void importCorruptedBinary_throwsSerializationException() {
        File broken = Path.of("src/test/resources/wrong_magic.bin").toFile();
        SerializationException ex = assertThrows(SerializationException.class, () ->
                service.importFromFile(new BinaryPartySerializer(), broken));
        assertEquals(SerializationException.ErrorCode.WRONG_MAGIC, ex.getCode());
    }
}