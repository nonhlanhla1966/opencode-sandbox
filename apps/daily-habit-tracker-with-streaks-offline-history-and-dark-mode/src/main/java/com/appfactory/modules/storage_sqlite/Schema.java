package com.appfactory.modules.storage_sqlite;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic schema builder producing CREATE TABLE / CREATE INDEX SQL.
 * Table class wraps DDL generation from a mutable column list — useful for
 * generated apps and simple in-app databases.
 */
public final class Schema {

    public enum Type { TEXT, INTEGER, REAL, BLOB }

    public static final class Column {
        public final String name;
        public final Type type;
        public final boolean notNull;
        public final boolean primaryKey;
        public final boolean autoIncrement;
        public final String defaultValue;

        public Column(String name, Type type) {
            this(name, type, false, false, false, null);
        }
        private Column(String name, Type type, boolean notNull, boolean pk,
                       boolean ai, String def) {
            this.name = name; this.type = type; this.notNull = notNull;
            this.primaryKey = pk; this.autoIncrement = ai; this.defaultValue = def;
        }
        public Column notNull() { return new Column(name, type, true, primaryKey, autoIncrement, defaultValue); }
        public Column primaryKey() { return new Column(name, type, notNull, true, autoIncrement, defaultValue); }
        public Column autoIncrement() { return new Column(name, type, notNull, primaryKey, true, defaultValue); }
        public Column defaults(String def) { return new Column(name, type, notNull, primaryKey, autoIncrement, def); }

        public String sql() {
            StringBuilder sb = new StringBuilder(name).append(' ').append(type.name());
            if (primaryKey) {
                sb.append(" PRIMARY KEY");
                if (autoIncrement) sb.append(" AUTOINCREMENT");
            }
            if (notNull) sb.append(" NOT NULL");
            if (defaultValue != null) sb.append(" DEFAULT ").append(defaultValue);
            return sb.toString();
        }
    }

    public static final class Index {
        public final String name;
        public final String table;
        public final String[] columns;
        public final boolean unique;

        public Index(String name, String table, boolean unique, String... columns) {
            this.name = name; this.table = table; this.unique = unique;
            this.columns = columns;
        }
        public String sql() {
            return "CREATE " + (unique ? "UNIQUE " : "") + "INDEX IF NOT EXISTS "
                    + name + " ON " + table + "(" + String.join(",", columns) + ")";
        }
    }

    public interface Table {
        String name();
        Column[] columns();
        default Index[] indexes() { return new Index[0]; }

        default String createSql() {
            List<String> cols = new ArrayList<>();
            for (Column c : columns()) cols.add(c.sql());
            return "CREATE TABLE IF NOT EXISTS " + name()
                    + " (" + String.join(", ", cols) + ")";
        }
    }

    private Schema() { }
}