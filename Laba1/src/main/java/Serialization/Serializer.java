package Serialization;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Общий контракт сериализации данных структуры.
 *
 * Несколько реализаций одного контракта — GUI и тесты работают через
 * Serializer, не зная, что внутри: JSON, бинарный формат или что-то ещё.
 *
 * Контракт roundtrip: {@code load(save(x), ...)} обязан вернуть данные,
 * равные {@code x} — это главный тест сериализации.
 *
 * @param <T> тип переносимых данных
 */
public interface Serializer<T> {

    /** Короткое имя формата: {@code "json"}, {@code "bin"} и т.д. — для FileChooser. */
    String format();

    /** Расширение файла: {@code ".json"}, {@code ".bin"} и т.д. */
    String extension();

    /** Сохранить данные {@code data} в файл {@code file}. */
    void save(T data, Path file) throws IOException, SerializationException;

    /** Прочитать данные из файла {@code file}. */
    T load(Path file) throws IOException, SerializationException;
}