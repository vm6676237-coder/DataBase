package model;

import java.io.Serializable;


public class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final String refTable;
    private final String refColumn;

    // Конструктор для обычного столбца или первичного ключа (без ссылки на другую таблицу)
    public Column(String name, DataType type, boolean primaryKey) {
        this(name, type, primaryKey, null, null); // Вызываем нижний конструктор, передавая null для связей
    }

    // Использование полного конструктора (для внешний ключ FK)
    public Column(String name, DataType type, boolean primaryKey, String refTable, String refColumn) {
        this.name = name.toLowerCase();
        this.type = type;
        this.primaryKey = primaryKey;
        this.refTable = refTable != null ? refTable.toLowerCase() : null;
        this.refColumn = refColumn != null ? refColumn.toLowerCase() : null;
    }

    // Геттеры
    public String getName() { return name; }
    public DataType getType() { return type; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getRefTable() { return refTable; }
    public String getRefColumn() { return refColumn; }

    // Метод проверяет, является ли столбец внешним ключом
    public boolean isForeignKey() {
        return refTable != null && refColumn != null;
    }
}
