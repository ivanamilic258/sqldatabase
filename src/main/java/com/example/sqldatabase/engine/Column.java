package com.example.sqldatabase.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Column {

    public enum Type { STRING, INTEGER, DOUBLE, BOOLEAN }

    private String name;
    private Type type;

    public Object parse(String value) {
        if(value == null) {
            return null;
        }
        switch (type) {
            case STRING:
                return value.replaceAll("^\"|\"$", ""); // Remove surrounding quotes if present
            case INTEGER:
                return Integer.parseInt(value.trim());
            case DOUBLE:
                return Double.parseDouble(value.trim());
            case BOOLEAN:
                return Boolean.parseBoolean(value.trim());
            default:
                throw new IllegalArgumentException("Unsupported column type: " + type);
        }
    }

    @Override
    public String toString() {
        return name + " (" + type.name().toLowerCase() + ")";
    }

}
