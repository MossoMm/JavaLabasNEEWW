package Serialization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты собственного бинарного формата.
 *
 * Позитивный roundtrip + негативные сценарии:
 * неверная сигнатура, неснятая версия, обрезанный файл, пустой файл.
 */
class BinaryPartySerializerTest {

    private final BinaryPartySerializer serializer = new BinaryPartySerializer();

    @TempDir
    Path tmp;

    private PartyData sample() {
        return PartyData.from(new PartyModel.Party[]{
                new PartyModel.RegularParty("ART-001", "Деталь А", 10, "A-1-1",
                        LocalDate.of(2025, 1, 15)),
                new PartyModel.ImportParty("ART-100", "Компонент Г", 50, "D-4-5",
                        LocalDate.of(2025, 3, 1), "Китай", "53010000")
        });
    }

    @Test
    void roundtrip_returnsEqualData() throws Exception {
        PartyData original = sample();
        Path file = tmp.resolve("out.bin");

        serializer.save(original, file);
        PartyData loaded = serializer.load(file);

        assertEquals(original, loaded);
        assertEquals(BinaryPartySerializer.MAGIC, readMagic(file));
        assertEquals(PartyData.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        assertEquals(2, loaded.getItems().size());
    }

    @Test
    void loadsReferenceBinFile() throws Exception {
        Path ref = Path.of("src/test/resources/serialization/warehouse.bin");
        PartyData data = serializer.load(ref);

        assertEquals(PartyData.CURRENT_SCHEMA_VERSION, data.getSchemaVersion());
        assertEquals(3, data.getItems().size());
        // Импортная партия — на 3-й позиции, проверяем доп. поля.
        PartyData.Item imp = data.getItems().get(2);
        assertEquals("IMPORT", imp.getType());
        assertEquals("Китай", imp.getCountry());
        assertEquals("53010000", imp.getCustomsCode());
    }

    // ---------- Негативные ----------

    @Test
    void throwsOnWrongMagic() {
        Path ref = Path.of("src/test/resources/serialization/wrong_magic.bin");
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(ref));
        assertEquals(SerializationException.ErrorCode.WRONG_MAGIC, ex.getCode());
    }

    @Test
    void throwsOnUnsupportedVersion() {
        Path ref = Path.of("src/test/resources/serialization/wrong_version.bin");
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(ref));
        assertEquals(SerializationException.ErrorCode.UNSUPPORTED_VERSION, ex.getCode());
    }

    @Test
    void throwsOnTruncatedFile() {
        Path ref = Path.of("src/test/resources/serialization/truncated.bin");
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(ref));
        assertEquals(SerializationException.ErrorCode.TRUNCATED_FILE, ex.getCode());
    }

    @Test
    void throwsOnEmptyFile() throws Exception {
        Path empty = tmp.resolve("empty.bin");
        Files.write(empty, new byte[0]);
        SerializationException ex = assertThrows(SerializationException.class,
                () -> serializer.load(empty));
        assertEquals(SerializationException.ErrorCode.EMPTY_FILE, ex.getCode());
    }

    /** Читаем первые 4 байта, чтобы убедиться, что сигнатура реально записана. */
    private static int readMagic(Path file) throws Exception {
        try (var in = java.nio.channels.FileChannel.open(file)) {
            var buf = java.nio.ByteBuffer.allocate(4);
            in.read(buf);
            buf.flip();
            return buf.getInt();
        }
    }
}