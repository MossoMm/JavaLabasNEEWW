package Serialization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты JSON-сериализатора на Jackson.
 *
 * Позитивные сценарии (roundtrip + чтение эталона) и негативные
 * (битый JSON, обрезанный файл, неснятая версия, неизвестный тип, пустой файл).
 */
class JsonPartySerializerTest {

    private final JsonPartySerializer serializer = new JsonPartySerializer();

    @TempDir
    Path tmp;

    private PartyData sample() {
        return PartyData.from(new PartyModel.Party[]{
                new PartyModel.RegularParty("ART-001", "Деталь А", 10, "A-1-1",
                        LocalDate.of(2025, 1, 15)),
                new PartyModel.ImportParty("ART-100", "Компонент Г", 50, "D-4-5",
                        LocalDate.of(2025, 3, 1), "Китай", "53010000"),
                new PartyModel.ArchiveParty("ART-003", "Блок В", 5, "C-3-4",
                        LocalDate.of(2024, 6, 10))
        });
    }

    // ---------- Позитивные ----------

    /** Главный контракт: load(save(x)) обязан вернуть данные, равные x. */
    @Test
    void roundtrip_returnsEqualData() throws Exception {
        PartyData original = sample();
        Path file = tmp.resolve("out.json");

        serializer.save(original, file);
        PartyData loaded = serializer.load(file);

        assertEquals(original, loaded, "roundtrip должен вернуть равные данные");
        assertEquals(PartyData.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        assertEquals(3, loaded.getItems().size());
    }

    /** Чтение эталонного файла из ресурсов — совпадение полей. */
    @Test
    void loadsReferenceJsonFile() throws Exception {
        Path ref = Path.of("src/test/resources/serialization/warehouse.json");
        PartyData data = serializer.load(ref);

        assertEquals(PartyData.CURRENT_SCHEMA_VERSION, data.getSchemaVersion());
        assertEquals(3, data.getItems().size());

        PartyData.Item imp = data.getItems().get(2);
        assertEquals("IMPORT", imp.getType());
        assertEquals("ART-100", imp.getArticle());
        assertEquals("Китай", imp.getCountry());
        assertEquals("53010000", imp.getCustomsCode());
        assertEquals(LocalDate.of(2025, 3, 1), imp.getDate());
    }

    // ---------- Негативные ----------

    @Test
    void throwsOnCorruptedJson() {
        Path ref = Path.of("src/test/resources/serialization/broken.json");
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(ref));
        assertEquals(SerializationException.ErrorCode.INVALID_JSON, ex.getCode());
    }

    @Test
    void throwsOnTruncatedJson() {
        Path ref = Path.of("src/test/resources/serialization/truncated.json");
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(ref));
        assertEquals(SerializationException.ErrorCode.INVALID_JSON, ex.getCode());
    }

    @Test
    void throwsOnUnsupportedVersion() {
        Path ref = Path.of("src/test/resources/serialization/wrong_version.json");
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(ref));
        assertEquals(SerializationException.ErrorCode.UNSUPPORTED_VERSION, ex.getCode());
    }

    @Test
    void throwsOnUnknownPartyType() {
        Path ref = Path.of("src/test/resources/serialization/unknown_type.json");
        // JSON читается, но DTO→Party падает на неизвестном типе.
        SerializationException ex = assertThrows(SerializationException.class, () -> {
            PartyData data = serializer.load(ref);
            data.toParties();
        });
        assertEquals(SerializationException.ErrorCode.UNKNOWN_PARTY_TYPE, ex.getCode());
    }

    @Test
    void throwsOnEmptyFile() throws Exception {
        Path empty = tmp.resolve("empty.json");
        Files.write(empty, new byte[0]);
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(empty));
        assertEquals(SerializationException.ErrorCode.EMPTY_FILE, ex.getCode());
    }
}