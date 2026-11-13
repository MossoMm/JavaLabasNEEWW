package Serialization;

import PartyModel.ArchiveParty;
import PartyModel.ImportParty;
import PartyModel.Party;
import PartyModel.RegularParty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Переносимый снимок склада для сериализации.
 */
public class PartyData implements java.io.Serializable {

    /** Версия схемы формата. При изменении бинарного формата увеличить. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Паспорт версии бинарной сериализации JDK. */
    private static final long serialVersionUID = 1L;

    /** Версия схемы данных (читается и из bin, и из JSON). */
    private int schemaVersion = CURRENT_SCHEMA_VERSION;

    /** Список партий — единственное хранилище данных в DTO. */
    private List<Item> items = new ArrayList<>();

    public PartyData() { }

    public PartyData(List<Item> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public int getSchemaVersion()                  { return schemaVersion; }
    public void setSchemaVersion(int v)            { this.schemaVersion = v; }
    public List<Item> getItems()                   { return items; }
    public void setItems(List<Item> items)         { this.items = items; }

    /** Один элемент данных — плоское представление любой партии. */
    public static class Item implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private String type;          // REGULAR / ARCHIVE / IMPORT
        private String article;
        private String name;
        private int quantity;
        private String cell;
        private LocalDate date;       // ISO-8601: yyyy-MM-dd
        private String country;      // только для IMPORT
        private String customsCode;  // только для IMPORT

        public Item() { }

        public Item(String type, String article, String name, int quantity,
                    String cell, LocalDate date, String country, String customsCode) {
            this.type = type;
            this.article = article;
            this.name = name;
            this.quantity = quantity;
            this.cell = cell;
            this.date = date;
            this.country = country;
            this.customsCode = customsCode;
        }

        public String getType()                    { return type; }
        public void setType(String t)              { this.type = t; }
        public String getArticle()                 { return article; }
        public void setArticle(String a)           { this.article = a; }
        public String getName()                    { return name; }
        public void setName(String n)              { this.name = n; }
        public int getQuantity()                    { return quantity; }
        public void setQuantity(int q)             { this.quantity = q; }
        public String getCell()                    { return cell; }
        public void setCell(String c)              { this.cell = c; }
        public LocalDate getDate()                 { return date; }
        public void setDate(LocalDate d)           { this.date = d; }
        public String getCountry()                 { return country; }
        public void setCountry(String c)           { this.country = c; }
        public String getCustomsCode()             { return customsCode; }
        public void setCustomsCode(String c)       { this.customsCode = c; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Item it)) return false;
            return quantity == it.quantity
                    && Objects.equals(type, it.type)
                    && Objects.equals(article, it.article)
                    && Objects.equals(name, it.name)
                    && Objects.equals(cell, it.cell)
                    && Objects.equals(date, it.date)
                    && emptyEq(country, it.country)
                    && emptyEq(customsCode, it.customsCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, article, name, quantity, cell, date,
                    nullIfEmpty(country), nullIfEmpty(customsCode));
        }

        /** null и пустая строка считаются равными — это нормально для опциональных полей. */
        private static boolean emptyEq(String a, String b) {
            return nullIfEmpty(a) == null ? nullIfEmpty(b) == null
                    : nullIfEmpty(a).equals(nullIfEmpty(b));
        }

        private static String nullIfEmpty(String s) {
            return (s == null || s.isEmpty()) ? null : s;
        }
    }

    // ----- Конвертация домен <-> DTO -----

    /** Снимок массива партий в плоский DTO. */
    public static PartyData from(Party[] parties) {
        List<Item> items = new ArrayList<>(parties == null ? 0 : parties.length);
        if (parties != null) {
            for (Party p : parties) {
                if (p == null) continue;
                String type = p.getTypeCode();
                String country = null, customs = null;
                if (p instanceof ImportParty ip) {
                    country = ip.getCountry();
                    customs = ip.getCustomsCode();
                }
                items.add(new Item(type, p.getArticle(), p.getName(), p.getQuantity(),
                        p.getCell(), p.getDate(), country, customs));
            }
        }
        PartyData data = new PartyData(items);
        data.setSchemaVersion(CURRENT_SCHEMA_VERSION);   // хранилище всегда текущей версии
        return data;
    }

    /** Восстановление массива партий из DTO. */
    public Party[] toParties() throws SerializationException {
        if (items == null) return new Party[0];
        List<Party> out = new ArrayList<>(items.size());
        for (Item it : items) {
            if (it == null) continue;
            String type = it.type == null ? "" : it.type.trim().toUpperCase();
            switch (type) {
                case "REGULAR" -> out.add(new RegularParty(it.article, it.name, it.quantity,
                        it.cell, it.date));
                case "ARCHIVE" -> out.add(new ArchiveParty(it.article, it.name, it.quantity,
                        it.cell, it.date));
                case "IMPORT"  -> out.add(new ImportParty(it.article, it.name, it.quantity,
                        it.cell, it.date, it.country, it.customsCode));
                default -> throw new SerializationException(
                        SerializationException.ErrorCode.UNKNOWN_PARTY_TYPE,
                        "Неизвестный тип партии: " + it.type);
            }
        }
        return out.toArray(new Party[0]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartyData d)) return false;
        return schemaVersion == d.schemaVersion && Objects.equals(items, d.items);
    }

    @Override
    public int hashCode() { return Objects.hash(schemaVersion, items); }
}