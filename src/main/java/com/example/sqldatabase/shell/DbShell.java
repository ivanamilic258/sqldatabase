package com.example.sqldatabase.shell;

import com.example.sqldatabase.engine.QueryEngine;
import org.springframework.shell.standard.*;

@ShellComponent
public class DbShell {

    private final QueryEngine engine;

    public DbShell(QueryEngine engine) {
        this.engine = engine;
    }

    @ShellMethod(key = "sql", value = "Execute a SQL-like command")
    public String sql(@ShellOption(arity = Integer.MAX_VALUE) String[] tokens) {
        return engine.execute(String.join(" ", tokens));
    }

    @ShellMethod(key = "help-db", value = "Show supported commands")
    public String helpDb() {
        return """
            ┌─────────────────────────────────────────────────────────────┐
            │                  Mini DB - Supported Commands               │
            ├─────────────────────────────────────────────────────────────┤
            │ CREATE TABLE name (col1 type, col2 type, ...)               │
            │   types: string, integer, double, boolean                   │
            │                                                             │
            │ SHOW TABLES                                                 │
            │ DESCRIBE tablename                                          │
            │ DROP TABLE tablename                                        │
            │                                                             │
            │ INSERT INTO table (col1, col2) VALUES (val1, val2)         │
            │ SELECT * FROM table [WHERE col op val]                      │
            │ SELECT col1, col2 FROM table [WHERE col op val]            │
            │ UPDATE table SET col = val [WHERE col op val]               │
            │ DELETE FROM table [WHERE col op val]                        │
            │                                                             │
            │ Operators: =  !=  >  <  >=  <=                             │
            └─────────────────────────────────────────────────────────────┘
            """;
    }
}