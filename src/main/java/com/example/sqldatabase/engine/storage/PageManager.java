package com.example.sqldatabase.engine.storage;

import com.example.sqldatabase.engine.Column;
import com.example.sqldatabase.engine.Row;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PageManager {

    public static final int PAGE_SIZE = 4096; // 4KB per page, like InnoDB

    private final Path tableDir;

    public PageManager(Path tableDir) throws IOException {
        this.tableDir = tableDir;
        Files.createDirectories(tableDir);
    }

    public void writeAllPages(List<Row> rows, List<Column> columns) throws IOException {
        // Delete old pages first
        try (var stream = Files.list(tableDir)) {
            for (Path p : stream.filter(p -> p.getFileName().toString().startsWith("page_"))
                    .toList()) {
                Files.delete(p);
            }
        }

        if (rows.isEmpty()) return;

        int pageIndex = 0;
        ByteBuffer page = ByteBuffer.allocate(PAGE_SIZE);

        for (Row row : rows) {
            byte[] rowBytes = serializeRow(row, columns);

            // Each row is prefixed with a 4-byte length header
            int needed = 4 + rowBytes.length;

            if (page.remaining() < needed) {
                // Current page is full — flush and start a new one
                flushPage(page, pageIndex++);
                page = ByteBuffer.allocate(PAGE_SIZE);
            }

            page.putInt(rowBytes.length);    // 4-byte row length
            page.put(rowBytes);              // row data
        }

        // Flush the last page if it has any data
        if (page.position() > 0) {
            flushPage(page, pageIndex);
        }
    }


    public List<Row> readAllPages(List<Column> columns) throws IOException {
        List<Row> rows = new ArrayList<>();

        List<Path> pages;
        try (var stream = Files.list(tableDir)) {
            pages = stream
                    .filter(p -> p.getFileName().toString().startsWith("page_"))
                    .sorted(Comparator.comparingInt(PageManager::pageIndex))
                    .toList();
        }

        for (Path pagePath : pages) {
            byte[] raw = Files.readAllBytes(pagePath);
            ByteBuffer buf = ByteBuffer.wrap(raw);

            while (buf.remaining() >= 4) {
                int rowLen = buf.getInt();
                if (rowLen <= 0 || rowLen > buf.remaining()) break; // padding / end of data

                byte[] rowBytes = new byte[rowLen];
                buf.get(rowBytes);
                rows.add(deserializeRow(rowBytes, columns));
            }
        }

        return rows;
    }
    // ── PAGE FILE HELPERS ────────────────────────────────────────────────────

    private void flushPage(ByteBuffer page, int index) throws IOException {
        Path path = tableDir.resolve("page_" + index + ".bin");
        byte[] full = new byte[PAGE_SIZE];         // always write full 4KB
        System.arraycopy(page.array(), 0, full, 0, page.array().length);
        Files.write(path, full);
    }

    private static int pageIndex(Path p) {
        String name = p.getFileName().toString(); // "page_3.bin"
        return Integer.parseInt(name.replace("page_", "").replace(".bin", ""));
    }

    private byte[] serializeRow(Row row, List<Column> columns) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(columns.size());

        for (Column col : columns) {
            Object val = row.get(col.getName());

            // Column name
            byte[] nameBytes = col.getName().getBytes();
            dos.writeShort(nameBytes.length);
            dos.write(nameBytes);

            // Type tag + value
            if (val == null) {
                dos.writeByte(0); // null marker
            } else {
                switch (col.getType()) {
                    case INTEGER -> { dos.writeByte(1); dos.writeInt((Integer) val); }
                    case DOUBLE  -> { dos.writeByte(2); dos.writeDouble((Double) val); }
                    case BOOLEAN -> { dos.writeByte(3); dos.writeBoolean((Boolean) val); }
                    case STRING  -> {
                        dos.writeByte(4);
                        byte[] strBytes = val.toString().getBytes();
                        dos.writeInt(strBytes.length);
                        dos.write(strBytes);
                    }
                }
            }
        }

        dos.flush();
        return baos.toByteArray( );
    }

    private Row deserializeRow(byte[] bytes, List<Column> columns) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        Map<String, Object> data = new LinkedHashMap<>();

        int colCount = dis.readInt();

        for (int i = 0; i < colCount; i++) {
            // Column name
            int nameLen = dis.readShort();
            byte[] nameBytes = new byte[nameLen];
            dis.readFully(nameBytes);
            String colName = new String(nameBytes);

            // Type tag + value
            byte typeTag = dis.readByte();
            Object value = switch (typeTag) {
                case 0 -> null;
                case 1 -> dis.readInt();
                case 2 -> dis.readDouble();
                case 3 -> dis.readBoolean();
                case 4 -> {
                    int strLen = dis.readInt();
                    byte[] strBytes = new byte[strLen];
                    dis.readFully(strBytes);
                    yield new String(strBytes);
                }
                default -> throw new IOException("Unknown type tag: " + typeTag);
            };

            data.put(colName, value);
        }

        return new Row(data);
    }

}
