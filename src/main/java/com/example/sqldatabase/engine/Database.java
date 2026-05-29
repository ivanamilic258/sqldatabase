package com.example.sqldatabase.engine;

import com.example.sqldatabase.engine.storage.CatalogManager;
import com.example.sqldatabase.engine.storage.PageManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Database {

    private static final Path DATA_DIR = Path.of("data");
    private static final Path TABLES_DIR = DATA_DIR.resolve("tables");

    private final Map<String, Table> tables = new HashMap<>();
    private final CatalogManager catalogManager;


    public Database() throws IOException {
        Files.createDirectories(TABLES_DIR);
        this.catalogManager = new CatalogManager(DATA_DIR);
        loadFromDisk(); // restore all tables + rows on startup
    }


    private void loadFromDisk() throws IOException {
        Map<String, List<Column>> schemas = catalogManager.loadCatalog();
        for (Map.Entry<String, List<Column>> entry : schemas.entrySet()) {
            String tableName = entry.getKey();
            List<Column> cols = entry.getValue();
            PageManager pm = new PageManager(TABLES_DIR.resolve(tableName));
            Table table = new Table(tableName, cols, pm);
            table.loadFromDisk();
            tables.put(tableName, table);
        }
    }

    public void createTable(String name, List<Column> columns) throws IOException {
        if (tables.containsKey(name)) {
            throw new IllegalArgumentException("Table already exists: " + name);
        }

        PageManager pm = new PageManager(TABLES_DIR.resolve(name.toLowerCase()));
        Table table = new Table(name, columns, pm);
        tables.put(name.toLowerCase(), table);

        saveCatalog(); // persist schema
    }



    public void dropTable(String name) throws IOException {
        Table table = getTable(name);
        tables.remove(name.toLowerCase());

        // Delete all page files for this table
        Path tableDir = TABLES_DIR.resolve(name.toLowerCase());
        if (Files.exists(tableDir)) {
            try (var stream = Files.walk(tableDir)) {
                for (Path p : stream.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(p);
            }
        }

        saveCatalog();
    }


    public Table getTable(String name) {
        Table t = tables.get(name.toLowerCase());
        if (t == null) throw new IllegalStateException("Table not found: " + name);
        return t;
    }

    public Map<String, Table> getTables() {
        return Collections.unmodifiableMap(tables);
    }


    private void saveCatalog() throws IOException {
        Map<String, List<Column>> schemas = new LinkedHashMap<>();
        tables.forEach((name, table) -> schemas.put(name, table.getColumns()));
        catalogManager.saveCatalog(schemas);
    }
}
