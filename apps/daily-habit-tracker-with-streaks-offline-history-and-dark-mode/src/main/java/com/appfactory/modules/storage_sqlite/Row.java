package com.appfactory.modules.storage_sqlite;

import java.util.List;

/** Immutable row + minimal query helper built on raw SQLite rows (pure). */
public final class Row {
    public static final class Cursor {
        private final java.util.List<Row> rows;
        private int index = -1;
        public Cursor(List<Row> rows) { this.rows = rows; }
        public boolean moveToNext() {
            if (index + 1 >= rows.size()) return false;
            index++;
            return true;
        }
        public Row row() { return rows.get(index); }
        public int count() { return rows.size(); }
    }

    private final java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();

    public Row put(String col, Object v) { values.put(col, v); return this; }
    public String str(String col) { Object v = values.get(col); return v == null ? null : String.valueOf(v); }
    public long lng(String col) {
        Object v = values.get(col);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }
    public boolean bool(String col) {
        Object v = values.get(col);
        return Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v));
    }
    public int size() { return values.size(); }
}