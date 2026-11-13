package Serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-сериализатор на Jackson. Реализует общий контракт {Serializer}.
 *
 *Использует {avaTimeModule}, чтобы {java.time.LocalDate} записывался
 * строкой ISO-8601  "2025-01-15", а не массивом чисел. Сериализатор пишет
 * {@code schemaVersion} в JSON-объект
 */
public final class JsonPartySerializer implements Serializer<PartyData> {

    private final ObjectMapper mapper;

    public JsonPartySerializer() {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Писать даты строкой, не массивом; человекочитаемый отступ.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override public String format()    { return "json"; }
    @Override public String extension()  { return ".json"; }

    @Override
    public void save(PartyData data, Path file) throws IOException, SerializationException {
        if (data == null) throw new SerializationException(
                SerializationException.ErrorCode.INVALID_JSON, "data is null");
        // Принудительно выставляем текущую версию схемы при записи.
        data.setSchemaVersion(PartyData.CURRENT_SCHEMA_VERSION);
        try {
            mapper.writeValue(file.toFile(), data);
        } catch (IOException e) {
            throw new SerializationException(SerializationException.ErrorCode.IO_ERROR,
                    "Не удалось записать JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public PartyData load(Path file) throws IOException, SerializationException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            throw new SerializationException(SerializationException.ErrorCode.EMPTY_FILE,
                    "JSON-файл пуст или не существует: " + file);
        }
        PartyData data;
        try {
            data = mapper.readValue(file.toFile(), PartyData.class);
        } catch (MismatchedInputException e) {
            throw new SerializationException(SerializationException.ErrorCode.INVALID_JSON,
                    "JSON не соответствует схеме PartyData: " + e.getOriginalMessage(), e);
        } catch (JsonProcessingException e) {
            throw new SerializationException(SerializationException.ErrorCode.INVALID_JSON,
                    "JSON повреждён: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new SerializationException(SerializationException.ErrorCode.IO_ERROR,
                    "Ошибка чтения JSON: " + e.getMessage(), e);
        }
        // Проверка версии схемы — требование «указание версии при сериализации».
        if (data.getSchemaVersion() != PartyData.CURRENT_SCHEMA_VERSION) {
            throw new SerializationException(SerializationException.ErrorCode.UNSUPPORTED_VERSION,
                    "В файле schemaVersion=" + data.getSchemaVersion()
                            + ", ожидается " + PartyData.CURRENT_SCHEMA_VERSION);
        }
        return data;
    }
}