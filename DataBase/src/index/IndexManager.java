package index;

import model.Table;
import java.io.Serializable;
import java.util.*;


public class IndexManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Map<String, TreeMap<Comparable<Object>, List<Integer>>>> indexes = new HashMap<>();


    @SuppressWarnings("unchecked")
    public void createIndex(Table table, String columnName) {
        String tName = table.getName().toLowerCase();
        String cName = columnName.toLowerCase();

        // Проверяем, есть ли вообще такой столбец в таблице
        if (table.getColumn(cName) == null) {
            throw new IllegalArgumentException("Столбец '" + cName + "' не найден в таблице '" + tName + "'");
        }

        // Создаем отсортированное дерево TreeMap для быстрого поиска
        TreeMap<Comparable<Object>, List<Integer>> tree = new TreeMap<>();
        List<Map<String, Object>> rows = table.getRows(); // Получаем все строки таблицы

        // Проходим по всем строкам таблицы по их порядковым номерам
        for (int i = 0; i < rows.size(); i++) {
            Object val = rows.get(i).get(cName);
            if (val instanceof Comparable) {
                // Кладем в дерево: ключ = значение ячейки, значение = добавляем номер строки i в список
                tree.computeIfAbsent((Comparable<Object>) val, k -> new ArrayList<>()).add(i);
            }
        }

        // Сохраняем построенное дерево в общую карту индексов
        indexes.computeIfAbsent(tName, k -> new HashMap<>()).put(cName, tree);
    }


    public void rebuildIndexesForTable(Table table) {
        String tName = table.getName().toLowerCase();
        if (!indexes.containsKey(tName)) return;

        // Берем список всех проиндексированных столбцов этой таблицы и перестраиваем каждый
        Set<String> indexedCols = new HashSet<>(indexes.get(tName).keySet());
        for (String col : indexedCols) {
            createIndex(table, col);
        }
    }

    // Удаляет индексы при удалении самой таблицы (DROP TABLE)
    public void dropIndexesForTable(String tableName) {
        indexes.remove(tableName.toLowerCase());
    }

    // Проверяет, создан ли индекс для конкретного столбца конкретной таблицы
    public boolean hasIndex(String tableName, String columnName) {
        Map<String, TreeMap<Comparable<Object>, List<Integer>>> tableIdx = indexes.get(tableName.toLowerCase());
        return tableIdx != null && tableIdx.containsKey(columnName.toLowerCase());
    }


    @SuppressWarnings("unchecked")
    public List<Integer> search(String tableName, String columnName, String operator, Object targetValue) {
        // Достаем дерево индекса для нужной таблицы и столбца
        TreeMap<Comparable<Object>, List<Integer>> tree = indexes.get(tableName.toLowerCase()).get(columnName.toLowerCase());
        Comparable<Object> target = (Comparable<Object>) targetValue;
        List<Integer> matchedRowIndices = new ArrayList<>();

        switch (operator) {
            case "=":
                if (tree.containsKey(target)) matchedRowIndices.addAll(tree.get(target));
                break;
            case ">":
                for (List<Integer> list : tree.tailMap(target, false).values()) matchedRowIndices.addAll(list);
                break;
            case "<":
                for (List<Integer> list : tree.headMap(target, false).values()) matchedRowIndices.addAll(list);
                break;
            case ">=":
                for (List<Integer> list : tree.tailMap(target, true).values()) matchedRowIndices.addAll(list);
                break;
            case "<=":
                for (List<Integer> list : tree.headMap(target, true).values()) matchedRowIndices.addAll(list);
                break;
            default:
                return null;
        }
        return matchedRowIndices;
    }
}

