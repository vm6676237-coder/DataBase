package parser;

import index.IndexManager;
import model.*;
import storage.StorageRepository;
import view.TablePrinter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SqlEngine {

    private final Map<String, Table> tables;
    private final IndexManager indexManager;
    private final StorageRepository repository;

    public SqlEngine(StorageRepository repository) {
        this.repository = repository;
        this.tables = repository.loadAllTables();
        this.indexManager = repository.loadIndexes();

        List<String> uncommitted = repository.getUncommittedWalQueries();

        if (!uncommitted.isEmpty()) {
            System.out.println(
                    "[RECOVERY] Обнаружен сбой! Восстанавливаем незавершенные команды..."
            );

            repository.clearWal();

            for (String query : uncommitted) {
                execute(query, true);
            }

            System.out.println("[RECOVERY] База успешно восстановлена.");
        }
    }

    public void execute(String rawSql) {
        execute(rawSql, false);
    }

    private void execute(String rawSql, boolean isRecovery) {
        String sql = rawSql.trim();

        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }

        if (sql.isEmpty()) {
            return;
        }

        String upper = sql.toUpperCase(Locale.ROOT);
        boolean isWrite = !upper.startsWith("SELECT");

        if (isWrite && !isRecovery) {
            repository.logToWal(sql);
        }

        try {
            if (upper.startsWith("CREATE TABLE")) {
                handleCreateTable(sql);
            } else if (upper.startsWith("DROP TABLE")) {
                handleDropTable(sql);
            } else if (upper.startsWith("CREATE INDEX")) {
                handleCreateIndex(sql);
            } else if (upper.startsWith("INSERT INTO")) {
                handleInsert(sql);
            } else if (upper.startsWith("SELECT")) {
                handleSelect(sql);
            } else if (upper.startsWith("UPDATE")) {
                handleUpdate(sql);
            } else if (upper.startsWith("DELETE FROM")) {
                handleDelete(sql);
            } else {
                throw new IllegalArgumentException(
                        "Неизвестная команда: " + sql
                );
            }

            if (isWrite) {
                repository.saveIndexes(indexManager);
                repository.clearWal();
            }

        } catch (Exception e) {
            if (isWrite) {
                repository.clearWal();
            }

            System.out.println("ОШИБКА: " + e.getMessage());
        }
    }

    private void handleCreateTable(String sql) {
        Pattern pattern = Pattern.compile(
                "CREATE TABLE\\s+(\\w+)\\s*\\((.+)\\)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sql);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Неверный синтаксис CREATE TABLE."
            );
        }

        String tableName = matcher.group(1).toLowerCase(Locale.ROOT);

        if (tables.containsKey(tableName)) {
            throw new IllegalArgumentException(
                    "Таблица '" + tableName + "' уже существует."
            );
        }

        String[] columnDefinitions = matcher.group(2).split(",");
        List<Column> columns = new ArrayList<>();

        for (String definition : columnDefinitions) {
            String[] parts = definition.trim().split("\\s+");

            if (parts.length < 2) {
                throw new IllegalArgumentException("Некорректное описание столбца: " + definition
                );
            }

            String columnName = parts[0].toLowerCase(Locale.ROOT);

            DataType type;

            try {
                type = DataType.valueOf(
                        parts[1].toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Неизвестный тип данных: " + parts[1]
                );
            }

            boolean isPrimaryKey = Arrays.stream(parts)
                    .anyMatch(part -> part.equalsIgnoreCase("PK"));

            String refTable = null;
            String refColumn = null;

            Pattern fkPattern = Pattern.compile(
                    "FK\\s+(\\w+)\\s*\\((\\w+)\\)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher fkMatcher = fkPattern.matcher(definition);

            if (fkMatcher.find()) {
                refTable = fkMatcher.group(1).toLowerCase(Locale.ROOT);
                refColumn = fkMatcher.group(2).toLowerCase(Locale.ROOT);

                Table parentTable = tables.get(refTable);

                if (parentTable == null) {
                    throw new IllegalArgumentException(
                            "Связанная таблица '" + refTable
                                    + "' не найдена."
                    );
                }

                if (parentTable.getColumn(refColumn) == null) {
                    throw new IllegalArgumentException(
                            "Столбец '" + refColumn
                                    + "' не найден в таблице '" + refTable + "'."
                    );
                }
            }

            columns.add(
                    new Column(
                            columnName,
                            type,
                            isPrimaryKey,
                            refTable,
                            refColumn
                    )
            );
        }

        Table table = new Table(tableName, columns);

        tables.put(tableName, table);
        repository.saveTable(table);

        System.out.println(
                "Таблица '" + tableName + "' создана."
        );
    }



    private void handleDropTable(String sql) {
        String tableName = sql.replaceAll("(?i)DROP TABLE\\s+", "").trim().toLowerCase();
        boolean existedInMemory = tables.remove(tableName) != null;
        indexManager.dropIndexesForTable(tableName);
        repository.deleteTableFile(tableName);

        if (existedInMemory) {
            System.out.println("Таблица '" + tableName + "' удалена.");
        } else {
            throw new IllegalArgumentException("Таблица '" + tableName + "' не найдена.");
        }
    }


    private void handleCreateIndex(String sql) {
        Pattern pattern = Pattern.compile(
                "CREATE INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\((\\w+)\\)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sql);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Синтаксис: CREATE INDEX имя ON таблица (столбец)"
            );
        }

        String indexName = matcher.group(1);
        String tableName = matcher.group(2).toLowerCase(Locale.ROOT);
        String columnName = matcher.group(3).toLowerCase(Locale.ROOT);

        Table table = getTableOrThrow(tableName);

        if (table.getColumn(columnName) == null) {
            throw new IllegalArgumentException(
                    "Столбец не найден: " + columnName
            );
        }

        indexManager.createIndex(table, columnName);

        System.out.println(
                "Индекс '" + indexName + "' построен для "
                        + tableName + "(" + columnName + ")."
        );
    }

    private void handleInsert(String sql) {
        Pattern pattern = Pattern.compile(
                "INSERT INTO\\s+(\\w+)\\s*(?:\\(([^)]+)\\))?\\s*VALUES\\s*(.+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sql);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Неверный синтаксис INSERT INTO."
            );
        }

        Table table = getTableOrThrow(matcher.group(1));

        String columnsPart = matcher.group(2);
        String valuesBlock = matcher.group(3).trim();

        List<String> targetColumns = new ArrayList<>();

        if (columnsPart != null) {
            for (String column : columnsPart.split(",")) {
                targetColumns.add(
                        column.trim().toLowerCase(Locale.ROOT)
                );
            }
        } else {
            for (Column column : table.getColumns()) {
                targetColumns.add(column.getName());
            }
        }

        if (targetColumns.size() != targetColumns.stream().distinct().count()) {
            throw new IllegalArgumentException(
                    "В списке столбцов обнаружены дубликаты."
            );
        }

        Matcher tupleMatcher = Pattern.compile(
                "\\(([^)]*)\\)"
        ).matcher(valuesBlock);

        int insertedCount = 0;

        while (tupleMatcher.find()) {
            String[] rawValues = splitValues(tupleMatcher.group(1));

            if (targetColumns.size() != rawValues.length) {
                throw new IllegalArgumentException(
                        "Число столбцов не совпадает с числом значений."
                );
            }

            Map<String, Object> row = new HashMap<>();

            for (Column column : table.getColumns()) {
                row.put(column.getName(), null);
            }

            for (int i = 0; i < targetColumns.size(); i++) {
                String columnName = targetColumns.get(i);

                Column column = table.getColumn(columnName);

                if (column == null) {
                    throw new IllegalArgumentException(
                            "Столбец не найден: " + columnName
                    );
                }

                Object parsedValue = column.getType().parseValue(
                        rawValues[i]
                );

                checkForeignKey(column, parsedValue);

                row.put(columnName, parsedValue);
            }

            table.insertRow(row);
            insertedCount++;
        }

        if (insertedCount == 0) {
            throw new IllegalArgumentException(
                    "Не найдены данные в скобках."
            );
        }

        indexManager.rebuildIndexesForTable(table);
        repository.saveTable(table);

        System.out.println(
                "Добавлено записей: " + insertedCount
        );
    }

    private void handleSelect(String sql) {
        Pattern pattern = Pattern.compile(
                "SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sql);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Неверный синтаксис SELECT."
            );
        }

        String[] columnsArray = matcher.group(1).split(",");

        List<String> selectedColumns = new ArrayList<>();

        for (String column : columnsArray) {
            selectedColumns.add(
                    column.trim().toLowerCase(Locale.ROOT)
            );
        }

        Table table = getTableOrThrow(matcher.group(2));

        if (!selectedColumns.contains("*")) {
            for (String column : selectedColumns) {
                if (table.getColumn(column) == null) {
                    throw new IllegalArgumentException(
                            "Столбец не найден: " + column
                    );
                }
            }
        } else if (selectedColumns.size() > 1) {
            throw new IllegalArgumentException(
                    "Нельзя использовать * вместе с другими столбцами."
            );
        }

        String whereClause = matcher.group(3);

        long startTime = System.nanoTime();

        List<Map<String, Object>> resultRows = filterRows(
                table,
                whereClause,
                true
        );

        long elapsedNs = System.nanoTime() - startTime;

        TablePrinter.print(table, selectedColumns, resultRows);

        System.out.printf(
                "Время выполнения запроса: %.4f мс%n",
                elapsedNs / 1_000_000.0
        );
    }

    private void handleUpdate(String sql) {
        Pattern pattern = Pattern.compile(
                "UPDATE\\s+(\\w+)\\s+SET\\s+(\\w+)\\s*=\\s*(.+?)(?:\\s+WHERE\\s+(.+))?$",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sql);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Неверный синтаксис UPDATE."
            );
        }

        Table table = getTableOrThrow(matcher.group(1));

        String setColumnName = matcher.group(2).toLowerCase(Locale.ROOT);
        String setRawValue = matcher.group(3).trim();
        String whereClause = matcher.group(4);

        Column column = table.getColumn(setColumnName);

        if (column == null) {
            throw new IllegalArgumentException(
                    "Столбец не найден: " + setColumnName
            );
        }

        if (column.isPrimaryKey()) {
            throw new IllegalArgumentException(
                    "Запрещено изменять первичный ключ (PK)!"
            );
        }

        Object newValue = column.getType().parseValue(setRawValue);

        checkForeignKey(column, newValue);

        List<Map<String, Object>> matched = filterRows(
                table,
                whereClause,
                false
        );

        for (Map<String, Object> row : matched) {
            row.put(setColumnName, newValue);
        }

        indexManager.rebuildIndexesForTable(table);
        repository.saveTable(table);

        System.out.println(
                "Обновлено записей: " + matched.size()
        );
    }

    private void handleDelete(String sql) {
        Pattern pattern = Pattern.compile(
                "DELETE FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sql);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Неверный синтаксис DELETE."
            );
        }

        Table table = getTableOrThrow(matcher.group(1));

        String whereClause = matcher.group(2);

        List<Map<String, Object>> toDelete = filterRows(
                table,
                whereClause,
                false
        );

        table.getRows().removeAll(toDelete);

        indexManager.rebuildIndexesForTable(table);
        repository.saveTable(table);

        System.out.println(
                "Удалено записей: " + toDelete.size()
        );
    }

    private List<Map<String, Object>> filterRows(
            Table table,
            String whereClause,
            boolean logOptimizer
    ) {
        if (whereClause == null || whereClause.trim().isEmpty()) {
            return new ArrayList<>(table.getRows());
        }

        Pattern pattern = Pattern.compile(
                "(\\w+)\\s*(=|!=|>=|<=|>|<)\\s*(.+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(whereClause.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Некорректное условие WHERE: " + whereClause
            );
        }

        String columnName = matcher.group(1).toLowerCase(Locale.ROOT);
        String operator = matcher.group(2);
        String rawTarget = matcher.group(3).trim();

        Column column = table.getColumn(columnName);

        if (column == null) {
            throw new IllegalArgumentException(
                    "Столбец не найден: " + columnName
            );
        }

        Object targetValue = column.getType().parseValue(rawTarget);

        if (indexManager.hasIndex(table.getName(), columnName)) {
            List<Integer> rowIndices = indexManager.search(
                    table.getName(),
                    columnName,
                    operator,
                    targetValue
            );

            if (rowIndices != null) {
                if (logOptimizer) {
                    System.out.println(
                            "[OPTIMIZER] Использован индекс по столбцу '"
                                    + columnName + "' (Index Scan)."
                    );
                }

                List<Map<String, Object>> result = new ArrayList<>();

                for (int index : rowIndices) {
                    if (index >= 0 && index < table.getRows().size()) {
                        Map<String, Object> row = table.getRows().get(index);

                        if (matchesCondition(
                                row.get(columnName),
                                operator,
                                targetValue
                        )) {
                            result.add(row);
                        }
                    }
                }

                return result;
            }
        }

        if (logOptimizer) {
            System.out.println(
                    "[OPTIMIZER] Полный перебор таблицы (Full Table Scan)."
            );
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : table.getRows()) {
            if (matchesCondition(
                    row.get(columnName),
                    operator,
                    targetValue
            )) {
                result.add(row);
            }
        }

        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean matchesCondition(
            Object cellValue,
            String operator,
            Object targetValue
    ) {
        if (cellValue == null || targetValue == null) {
            return false;
        }

        if (!(cellValue instanceof Comparable)) {
            throw new IllegalArgumentException(
                    "Значение не поддерживает сравнение."
            );
        }

        if (!cellValue.getClass().isInstance(targetValue)) {
            throw new IllegalArgumentException(
                    "Несовместимые типы данных для сравнения."
            );
        }

        int comparison = ((Comparable) cellValue).compareTo(targetValue);

        switch (operator) {
            case "=":
                return comparison == 0;
            case "!=":
                return comparison != 0;
            case ">":
                return comparison > 0;
            case "<":
                return comparison < 0;
            case ">=":
                return comparison >= 0;
            case "<=":
                return comparison <= 0;
            default:
                return false;
        }
    }

    private void checkForeignKey(Column column, Object value) {
        if (!column.isForeignKey() || value == null) {
            return;
        }

        Table parentTable = tables.get(
                column.getRefTable().toLowerCase(Locale.ROOT)
        );

        if (parentTable == null) {
            throw new IllegalArgumentException(
                    "Главная таблица '"
                            + column.getRefTable() + "' не найдена."
            );
        }

        for (Map<String, Object> parentRow : parentTable.getRows()) {
            if (Objects.equals(
                    parentRow.get(column.getRefColumn()),
                    value
            )) {
                return;
            }
        }

        throw new IllegalArgumentException(
                "Нарушение внешнего ключа (FK): значение "
                        + value
                        + " отсутствует в таблице "
                        + column.getRefTable()
                        + "(" + column.getRefColumn() + ")"
        );
    }

    private Table getTableOrThrow(String name) {
        Table table = tables.get(name.toLowerCase(Locale.ROOT));

        if (table == null) {
            throw new IllegalArgumentException(
                    "Таблица '" + name + "' не существует."
            );
        }

        return table;
    }

    private String[] splitValues(String valuesString) {
        List<String> values = new ArrayList<>();

        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char character : valuesString.toCharArray()) {
            if (character == '\'') {
                inQuotes = !inQuotes;
            }

            if (character == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException(
                    "Незакрытая одинарная кавычка в VALUES."
            );
        }

        values.add(current.toString().trim());

        return values.toArray(new String[0]);
    }
}