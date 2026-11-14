package model;

import java.time.LocalDate;


public enum DataType {
    INT, DOUBLE, STRING, DATE, BOOLEAN;


    public Object parseValue(String rawValue) {
        if (rawValue == null || rawValue.equalsIgnoreCase("NULL")) {
            return null;
        }

        rawValue = rawValue.trim();

        switch (this) {
            case INT:
                return Integer.parseInt(rawValue);
            case DOUBLE:
                return Double.parseDouble(rawValue);
            case BOOLEAN:

                if (!rawValue.equalsIgnoreCase("true") && !rawValue.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Для BOOLEAN нужно TRUE или FALSE, а получено: " + rawValue);
                }
                return Boolean.parseBoolean(rawValue); // Превращаем текст в логическое true/false
            case DATE:
                // Превращаем в объект даты
                return LocalDate.parse(rawValue.replace("'", ""));
            case STRING:
                if (rawValue.startsWith("'") && rawValue.endsWith("'") && rawValue.length() >= 2) {
                    return rawValue.substring(1, rawValue.length() - 1);
                }
                return rawValue; // Возвращаем очищенную строку
            default:
                throw new IllegalArgumentException("Неизвестный тип данных: " + this);
        }
    }
}
