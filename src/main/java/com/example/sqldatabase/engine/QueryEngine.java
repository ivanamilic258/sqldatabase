package com.example.sqldatabase.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QueryEngine {

    private final Database db;

    public QueryEngine() throws IOException {
        this.db = new Database();
    }
    public String execute(String sql) {
        String s = sql.trim();
        try {
            if (s.toUpperCase().startsWith("CREATE TABLE")) return createTable(s);
            if (s.toUpperCase().startsWith("DROP TABLE")) return dropTable(s);
            if (s.toUpperCase().startsWith("SHOW TABLES")) return showTables();
            if (s.toUpperCase().startsWith("DESCRIBE")) return describe(s);
            if (s.toUpperCase().startsWith("INSERT")) return insert(s);
            if (s.toUpperCase().startsWith("SELECT")) return select(s);
            if (s.toUpperCase().startsWith("UPDATE")) return update(s);
            if (s.toUpperCase().startsWith("DELETE")) return delete(s);
            return "Unknown command. Type HELP for available commands.";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String createTable(String sql) {

        Pattern p = Pattern.compile(
                "CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return "Syntax: CREATE TABLE name (col1 type, col2 type, ...)";

        String tableName = m.group(1);
        String columnsDef = m.group(2);
        String[] columnsArr = columnsDef.split(",");

        List<Column> columns = new ArrayList<>();
        for (String col : columnsArr) {
            String[] parts = col.trim().split("\\s+");
            if (parts.length < 2) {
                return "Syntax error in column definition: " + col;
            }
            String colName = parts[0];
            String colTypeStr = parts[1].toUpperCase();
            Column.Type colType;
            try {
                colType = Column.Type.valueOf(colTypeStr);
            } catch (IllegalArgumentException e) {
                return "Unsupported column type: " + colTypeStr;
            }
            columns.add(new Column(colName, colType));
        }
        try {
            db.createTable(tableName, columns);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "Table created: " + tableName;
    }

    private String dropTable(String sql) {
        Pattern p = Pattern.compile("DROP\\s+TABLE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return "Syntax: DROP TABLE name";
        try {
            db.dropTable(m.group(1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "Table '" + m.group(1) + "' dropped.";
    }

    private String insert(String sql) {
        Pattern p = Pattern.compile(
                "INSERT\\s+INTO\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return "Syntax: INSERT INTO table (col1, col2) VALUES (val1, val2)";

        Table table = db.getTable(m.group(1));
        List<String> cols   = Arrays.asList(m.group(2).split(","));
        List<String> values = splitValues(m.group(3));
        try {
            table.insert(cols, values);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "1 row inserted.";
    }

    private List<String> splitValues(String raw) {
        List<String> parts = new ArrayList<>();
        Matcher m = Pattern.compile("'[^']*'|\"[^\"]*\"|[^,]+").matcher(raw);
        while (m.find()) parts.add(m.group().trim());
        return parts;
    }

    private String describe(String sql) {
        Pattern p = Pattern.compile("DESCRIBE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return "Syntax: DESCRIBE tablename";
        Table table = db.getTable(m.group(1));
        StringBuilder sb = new StringBuilder("Schema for table '" + table.getName() + "':\n");
        sb.append("-".repeat(30)).append("\n");
        table.getColumns().forEach(c ->
                sb.append(c.getName() + " : " + c.getType()).append("\n"));
        return sb.toString().trim();
    }

    private String select(String sql) {
        Pattern p = Pattern.compile(
                "SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return "Syntax: SELECT col1,col2 FROM table [WHERE col op val]";

        String colsPart = m.group(1).trim();
        Table table     = db.getTable(m.group(2));
        Table.WhereClause where = parseWhere(m.group(3));

        List<Row> rows = table.select(null, where);

        // Determine columns to display
        List<String> displayCols = colsPart.equals("*")
                ? table.getColumns().stream().map(Column::getName).toList()
                : Arrays.stream(colsPart.split(",")).map(String::trim).toList();

        return formatTable(displayCols, rows) +
                "\n" + rows.size() + " row(s) returned.";
    }

    private String update(String sql) {
        Pattern p = Pattern.compile(
                "UPDATE\\s+(\\w+)\\s+SET\\s+(\\w+)\\s*=\\s*([^\\s]+(?:\\s+[^W][^H][^E][^R][^E]*)*)(?:\\s+WHERE\\s+(.+))?",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return "Syntax: UPDATE table SET col = val [WHERE col op val]";

        Table table = db.getTable(m.group(1));
        String setCol   = m.group(2).trim();
        String setVal   = m.group(3).trim();
        Table.WhereClause where = parseWhere(m.group(4));

        int count = 0;
        try {
            count = table.update(setCol, setVal, where);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return count + " row(s) updated.";
    }

    private String delete(String sql) {
        Pattern p = Pattern.compile(
                "DELETE\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return "Syntax: DELETE FROM table [WHERE col op val]";

        Table table = db.getTable(m.group(1));
        Table.WhereClause where = parseWhere(m.group(2));
        int count = table.delete(where);
        return count + " row(s) deleted.";
    }


    private Table.WhereClause parseWhere(String whereStr) {
        if (whereStr == null || whereStr.isBlank()) return null;
        Pattern p = Pattern.compile("(\\w+)\\s*(>=|<=|!=|=|>|<)\\s*(.+)");
        Matcher m = p.matcher(whereStr.trim());
        if (!m.find()) throw new IllegalArgumentException("Bad WHERE clause: " + whereStr);
        return new Table.WhereClause(m.group(1), m.group(2), m.group(3).trim());
    }


    private String showTables() {
        if (db.getTables().isEmpty()) return "No tables exist yet.";
        StringBuilder sb = new StringBuilder("Tables:\n");
        db.getTables().forEach((name, table) ->
                sb.append("  ").append(name)
                        .append(" (").append(table.rowCount()).append(" rows)\n"));
        return sb.toString().trim();
    }



    // Renders rows as a text table
    private String formatTable(List<String> cols, List<Row> rows) {
        if (rows.isEmpty()) return "(empty)\n";

        // Calculate column widths
        Map<String, Integer> widths = new LinkedHashMap<>();
        cols.forEach(c -> widths.put(c, c.length()));
        rows.forEach(row -> cols.forEach(c -> {
            Object val = row.get(c);
            int len = val == null ? 4 : val.toString().length();
            widths.merge(c, len, Math::max);
        }));

        // Header
        StringBuilder sb = new StringBuilder();
        String separator = widths.values().stream()
                .map(w -> "-".repeat(w + 2))
                .reduce("+", (a, b) -> a + b + "+");

        sb.append(separator).append("\n| ");
        cols.forEach(c -> sb.append(String.format("%-" + widths.get(c) + "s", c)).append(" | "));
        sb.append("\n").append(separator).append("\n");

        // Rows
        rows.forEach(row -> {
            sb.append("| ");
            cols.forEach(c -> {
                Object val = row.get(c);
                sb.append(String.format("%-" + widths.get(c) + "s", val == null ? "null" : val))
                        .append(" | ");
            });
            sb.append("\n");
        });
        sb.append(separator);
        return sb.toString();
    }
}
