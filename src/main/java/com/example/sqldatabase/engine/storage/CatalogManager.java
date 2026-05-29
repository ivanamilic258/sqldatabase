package com.example.sqldatabase.engine.storage;

import com.example.sqldatabase.engine.Column;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CatalogManager {

    private final Path catalogPath;

    public CatalogManager(Path dataDir) {
        this.catalogPath = dataDir.resolve("catalog.bin");
    }

    /*STRUCTURE
    *
[number of tables]

[table name length]
[table name]

[number of columns]

[column name length]
[column name]
[column type]

[column name length]
[column name]
[column type]
*
* */
    public void saveCatalog(Map<String, List<Column>> schemas) throws IOException {
            try (DataOutputStream dos = new DataOutputStream(
                    new FileOutputStream(catalogPath.toFile()))) {
                dos.writeInt(schemas.size());
                for (Map.Entry<String, List<Column>> entry : schemas.entrySet()) {
                    //tables
                    byte[] nameBytes = entry.getKey().getBytes();
                    dos.writeShort(nameBytes.length);
                    dos.write(nameBytes);
                    //columns
                    List<Column> columns = entry.getValue();
                    dos.writeInt( columns.size());
                    for (Column col : columns) {
                        byte[] colNameBytes = col.getName().getBytes();
                        dos.writeShort(colNameBytes.length);
                        dos.write(colNameBytes);
                        dos.writeByte(col.getType().ordinal());
                    }
                }
               }
            }


    public Map<String, List<Column>> loadCatalog() throws IOException {
        Map<String, List<Column>> schemas = new LinkedHashMap<>();
        if (!Files.exists(catalogPath)) return schemas;
        try (DataInputStream dis = new DataInputStream(
                new FileInputStream(catalogPath.toFile()))) {
            int tableCount = dis.readInt();
            for (int i = 0; i < tableCount; i++) {
                // Table name
                int nameLen = dis.readShort();
                byte[] nameBytes = new byte[nameLen];
                dis.readFully(nameBytes);
                String tableName = new String(nameBytes);

                // Columns
                int colCount = dis.readInt();
                List<Column> cols = new ArrayList<>();
                for (int j = 0; j < colCount; j++) {
                    int colNameLen = dis.readShort();
                    byte[] colNameBytes = new byte[colNameLen];
                    dis.readFully(colNameBytes);
                    String colName = new String(colNameBytes);
                    int typeOrdinal = dis.readInt();
                    cols.add(new Column(colName, Column.Type.values()[typeOrdinal]));
                }

                schemas.put(tableName, cols);
            }
        }
        return schemas;
    }

}
