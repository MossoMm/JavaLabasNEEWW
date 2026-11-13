package Serialization;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Собственный бинарный формат сериализации склада. Реализует общий контракт.
 *   [4 байта]  magic = "WHSE"             — сигнатура формата
 *   [4 байта]  schemaVersion (int)        — версия схемы данных
 *   [4 байта]  count  (int)               — число партий
 *   для каждой партии:
 *       [UTF]   type        ("REGULAR" / "ARCHIVE" / "IMPORT")
 *       [UTF]   article
 *       [UTF]   name
 *       [int]   quantity
 *       [UTF]   cell
 *       [long]  date (epochDay, LocalDate.toEpochDay()); 0 если null
 *       [UTF]   country     — только если type == IMPORT, иначе ""
 *       [UTF]   customsCode — только если type == IMPORT, иначе ""
 *И magic, и schemaVersion проверяются при загрузке — это даёт обработку
 * «битых» файлов: неверная сигнатура → {@code WRONG_MAGIC},
 * обрезанный файл → {@code TRUNCATED_FILE}, неснятая версия → {@code UNSUPPORTED_VERSION}.</p>
 */
public final class BinaryPartySerializer implements Serializer<PartyData> {

    /** 4-байтовая сигнатура формата. */
    public static final int MAGIC = 0x57485345;   // 'W' 'H' 'S' 'E'

    @Override public String format()   { return "bin"; }
    @Override public String extension() { return ".bin"; }

    @Override
    public void save(PartyData data, Path file) throws IOException, SerializationException {
        if (data == null) {
            throw new SerializationException(SerializationException.ErrorCode.IO_ERROR, "data is null");
        }
        data.setSchemaVersion(PartyData.CURRENT_SCHEMA_VERSION);
        try (OutputStream raw = Files.newOutputStream(file);
             DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(MAGIC);
            out.writeInt(data.getSchemaVersion());
            java.util.List<PartyData.Item> items = data.getItems();
            out.writeInt(items == null ? 0 : items.size());
            for (PartyData.Item it : items) {
                writeItem(out, it);
            }
        }
    }

    @Override
    public PartyData load(Path file) throws IOException, SerializationException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            throw new SerializationException(SerializationException.ErrorCode.EMPTY_FILE,
                    "Бинарный файл пуст или не существует: " + file);
        }
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(raw)) {
            int magic = in.readInt();                   // EOFException если обрезан
            if (magic != MAGIC) {
                throw new SerializationException(SerializationException.ErrorCode.WRONG_MAGIC,
                        String.format("Неверная сигнатура: 0x%08X (ожидается 0x%08X)", magic, MAGIC));
            }
            int version = in.readInt();
            if (version != PartyData.CURRENT_SCHEMA_VERSION) {
                throw new SerializationException(SerializationException.ErrorCode.UNSUPPORTED_VERSION,
                        "schemaVersion=" + version + ", ожидается " + PartyData.CURRENT_SCHEMA_VERSION);
            }
            int count = in.readInt();
            if (count < 0) {
                throw new SerializationException(SerializationException.ErrorCode.TRUNCATED_FILE,
                        "Отрицательное число элементов: " + count);
            }
            java.util.List<PartyData.Item> items = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                items.add(readItem(in));
            }
            return new PartyData(items);
        } catch (SerializationException e) {
            throw e;
        } catch (java.io.EOFException e) {
            // Файл оборвался раньше, чем мы дочитали заявленные данные.
            throw new SerializationException(SerializationException.ErrorCode.TRUNCATED_FILE,
                    "Файл обрезан: не удалось дочитать данные", e);
        } catch (java.io.UTFDataFormatException e) {
            throw new SerializationException(SerializationException.ErrorCode.TRUNCATED_FILE,
                    "Повреждённая UTF-строка в файле", e);
        } catch (IOException e) {
            throw new SerializationException(SerializationException.ErrorCode.IO_ERROR,
                    "Ошибка чтения бинарного файла: " + e.getMessage(), e);
        }
    }

    private static void writeItem(DataOutputStream out, PartyData.Item it) throws IOException {
        out.writeUTF(safe(it.getType()));
        out.writeUTF(safe(it.getArticle()));
        out.writeUTF(safe(it.getName()));
        out.writeInt(it.getQuantity());
        out.writeUTF(safe(it.getCell()));
        LocalDate d = it.getDate();
        out.writeLong(d == null ? 0L : d.toEpochDay());
        out.writeUTF(safe(it.getCountry()));
        out.writeUTF(safe(it.getCustomsCode()));
    }

    private static PartyData.Item readItem(DataInputStream in) throws IOException {
        PartyData.Item it = new PartyData.Item();
        it.setType(in.readUTF());
        it.setArticle(in.readUTF());
        it.setName(in.readUTF());
        it.setQuantity(in.readInt());
        it.setCell(in.readUTF());
        long epoch = in.readLong();
        it.setDate(epoch == 0L ? null : LocalDate.ofEpochDay(epoch));
        it.setCountry(in.readUTF());
        it.setCustomsCode(in.readUTF());
        return it;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}