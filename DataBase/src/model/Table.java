package model;

import java.io.Serializable;
import java.util.*;


public class Table implements Serializable, Iterable<Map<String, Object>> {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final List<Column> columns;
    private final List<Map<String, Object>> rows;

    // Конструктор создания новой пустой таблицы
    public Table(String name, List<Column> columns) {
        this.name = name.toLowerCase();
        this.columns = new ArrayList<>(columns);
        this.rows = new ArrayList<>();
    }

    public String getName() { return name; }
    public List<Column> getColumns() { return columns; }
    public List<Map<String, Object>> getRows() { return rows; }

    // Ищет и возвращает объект Column по его названию
    public Column getColumn(String colName) {
        for (Column c : columns) {
            if (c.getName().equalsIgnoreCase(colName)) {
                return c;
            }
        }
        return null;
    }

    // Ищет в таблице столбец, который назначен первичным ключом (PK)
    public Column getPrimaryKeyColumn() {
        for (Column c : columns) {
            if (c.isPrimaryKey()) {
                return c;
            }
        }
        return null;
    }


    public void insertRow(Map<String, Object> newRow) {
        Column pk = getPrimaryKeyColumn(); // Узнаем, есть ли у таблицы первичный ключ
        if (pk != null) {
            Object newPkVal = newRow.get(pk.getName()); // Берем значение PK из новой добавляемой строки
            if (newPkVal == null) {
                // Первичный ключ не может быть пустым
                throw new IllegalArgumentException("Первичный ключ '" + pk.getName() + "' не может быть NULL!");
            }

            for (Map<String, Object> existingRow : rows) {
                // Если в таблице уже есть строка с таким же значением PK
                if (Objects.equals(existingRow.get(pk.getName()), newPkVal)) {
                    throw new IllegalArgumentException("Ошибка уникальности PK: значение " + newPkVal + " уже занято!");
                }
            }
        }
        rows.add(newRow);
    }

    // Реализация метода из интерфейса Iterable (паттерн Iterator)
    @Override
    public Iterator<Map<String, Object>> iterator() {
        return rows.iterator();
    }
}

