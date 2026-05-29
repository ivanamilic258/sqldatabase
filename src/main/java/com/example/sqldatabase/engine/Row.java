package com.example.sqldatabase.engine;

import java.util.Collections;
import java.util.Map;

public class Row {

    private final Map<String, Object> data;

    public Row(Map<String, Object> data) {
        this.data = data;
    }

    public Object get(String columnName) {
        return data.get(columnName);
    }

    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    // Evaluate WHERE conditions: supports =, !=, >, <, >=, <=
    public boolean matches(String column, String operator, String rawValue) {
        Object cell = get(column);
        if (cell == null) {
            return false;
        }
        if (cell instanceof String) {
            String value = ((String) cell).trim();
            String compareValue = rawValue.replaceAll("^\"|\"$", "").trim();
            switch (operator) {
                case "=":
                    return value.equals(compareValue);
                case "!=":
                    return !value.equals(compareValue);
                default:
                    throw new IllegalArgumentException("Unsupported operator for STRING: " + operator);
            }
        } else if (cell instanceof Number) {
            double value = ((Number) cell).doubleValue();
            double compareValue = Double.parseDouble(rawValue.trim());
            switch (operator) {
                case "=":
                    return value == compareValue;
                case "!=":
                    return value != compareValue;
                case ">":
                    return value > compareValue;
                case "<":
                    return value < compareValue;
                case ">=":
                    return value >= compareValue;
                case "<=":
                    return value <= compareValue;
                default:
                    throw new IllegalArgumentException("Unsupported operator for NUMBER: " + operator);
            }
        } else if (cell instanceof Boolean) {
            boolean value = (Boolean) cell;
            boolean compareValue = Boolean.parseBoolean(rawValue.trim());
            switch (operator) {
                case "=":
                    return value == compareValue;
                case "!=":
                    return value != compareValue;
                default:
                    throw new IllegalArgumentException("Unsupported operator for BOOLEAN: " + operator);
            }
        } else {
            throw new IllegalArgumentException("Unsupported data type for column: " + column);
        }

    }
}
