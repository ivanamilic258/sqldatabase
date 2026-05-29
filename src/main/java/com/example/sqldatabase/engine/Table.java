package com.example.sqldatabase.engine;

import com.example.sqldatabase.engine.storage.PageManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Table {

    private final String name;
    private final List<Column> columns;
    private final List<Row> rows = new ArrayList<>();

    private final PageManager pageManager;

    public Table(String name, List<Column> columns, PageManager pageManager) {
        this.name = name;
        this.columns = new ArrayList<>();
        this.columns.addAll(columns);
        this.pageManager = pageManager;
    }


    public List<Column> getColumns() { return columns; }
    public String getName()          { return name; }
    public int rowCount()            { return rows.size(); }

    public record WhereClause(String column, String operator, String value) {}

    // Called once on startup to load rows from disk
    public void loadFromDisk() throws IOException {
        rows.clear();
        rows.addAll(pageManager.readAllPages(columns));
    }

    // Flush current in-memory rows to disk pages
    public void persist() throws IOException {
        pageManager.writeAllPages(rows, columns);
    }

    public void insert(List<String> colNames, List<String> values) throws IOException {
        if (colNames.size() != values.size())
            throw new IllegalArgumentException("Column/value count mismatch.");

        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < colNames.size(); i++) {
            String colName = colNames.get(i).toLowerCase().trim();
            Column col = findColumn(colName);
            data.put(colName, col.parse(values.get(i).trim()));
        }
        for (Column col : columns) {
            data.putIfAbsent(col.getName().toLowerCase(), null);
        }

        rows.add(new Row(data));
        persist(); // write-through to pages on every insert
    }


    private Column findColumn(String name) {
        return columns.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown column: " + name));
    }

    public List<Row> select(List<String> selectCols, WhereClause where) {
        return rows.stream()
                .filter(row -> where == null || row.matches(
                        where.column(), where.operator(), where.value()))
                .collect(Collectors.toList());
    }

    public int update(String setCol, String setValue, WhereClause where) throws IOException {
        Column col = findColumn(setCol);
        List<Row> updated = new ArrayList<>();
        int count = 0;

        for (Row row : rows) {
            if (where == null || row.matches(where.column(), where.operator(), where.value())) {
                Map<String, Object> newData = new LinkedHashMap<>(row.getData());
                newData.put(setCol.toLowerCase(), col.parse(setValue));
                updated.add(new Row(newData));
                count++;
            } else {
                updated.add(row);
            }
        }

        rows.clear();
        rows.addAll(updated);
        persist();
        return count;
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    public int delete(WhereClause where) {
        int before = rows.size();
        if (where == null) {
            rows.clear();
        } else {
            rows.removeIf(row ->
                    row.matches(where.column(), where.operator(), where.value()));
        }
        return before - rows.size();
    }
}
