package Serialization;

/**
 * Исключения уровня сериализации: битые/обрезанные/несовместимые по версии файлы.
 *
 * <p>Подкласс {@link Exception} (checked) — то же семейство, что и {@link java.io.IOException},
 * но с дополнительным кодом ошибки, чтобы GUI и тесты могли различать сценарии.</p>
 */
public class SerializationException extends Exception {

    public enum ErrorCode {
        WRONG_MAGIC          ("Неверная сигнатура файла (это не складской .bin)"),
        UNSUPPORTED_VERSION  ("Неподдерживаемая версия формата"),
        TRUNCATED_FILE       ("Файл обрезан / повреждён"),
        EMPTY_FILE           ("Файл пуст"),
        INVALID_JSON         ("JSON повреждён или не соответствует схеме"),
        UNKNOWN_PARTY_TYPE   ("Неизвестный тип партии в данных"),
        IO_ERROR             ("Ошибка ввода-вывода");

        private final String description;
        ErrorCode(String d) { this.description = d; }
        public String getDescription() { return description; }
    }

    private final ErrorCode code;

    public SerializationException(ErrorCode code) {
        super(code.getDescription());
        this.code = code;
    }

    public SerializationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public SerializationException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() { return code; }
}