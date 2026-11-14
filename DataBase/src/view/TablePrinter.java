package view;

import model.Column;
import model.Table;
import java.util.*;


public class TablePrinter {

    public static void print(Table table, List<String> selectedCols, List<Map<String, Object>> rows) {
        List<String> headers = new ArrayList<>();

        // Если пользователь написал SELECT * — берем все столбцы таблицы
        if (selectedCols.size() == 1 && selectedCols.get(0).equals("*")) {
            for (Column c : table.getColumns()) {
                headers.add(c.getName());
            }
        } else {
            headers.addAll(selectedCols);
        }

        // Вычисляем максимальную ширину каждого столбца, чтобы рамки не съезжали
        Map<String, Integer> widths = new HashMap<>();
        for (String h : headers) {
            widths.put(h, h.length());
        }
        for (Map<String, Object> row : rows) {
            for (String h : headers) {
                String text = String.valueOf(row.get(h));
                if (text.length() > widths.get(h)) {
                    widths.put(h, text.length());
                }
            }
        }

        // Рисуем шапку таблицы
        printLine(headers, widths);
        System.out.print("|");
        for (String h : headers) {

            System.out.printf(" %-" + widths.get(h) + "s |", h.toUpperCase());
        }
        System.out.println();
        printLine(headers, widths);

        // Рисуем все найденные строки данных
        for (Map<String, Object> row : rows) {
            System.out.print("|");
            for (String h : headers) {
                System.out.printf(" %-" + widths.get(h) + "s |", String.valueOf(row.get(h)));
            }
            System.out.println();
        }
        printLine(headers, widths); // Нижняя граница таблицы
        System.out.println("Всего записей: " + rows.size());
    }

    // Вспомогательный метод для рисования горизонтальной линии
    private static void printLine(List<String> headers, Map<String, Integer> widths) {
        System.out.print("+");
        for (String h : headers) {
            for (int i = 0; i < widths.get(h) + 2; i++) {
                System.out.print("-");
            }
            System.out.print("+");
        }
        System.out.println();
    }
}

