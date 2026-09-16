package com.appfactory.modules.storage_sqlite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Versioned migration runner. Migrations a a list of (version, ordered sql
 * statements) shipped inside {@code Migrations}. Applies migrate(from+1..to)
 * respecting a per-schema user_version value.
 */
public final class Migrations {

    public interface Migration {
        int version();
        List<String> statements();
    }

    public interface VersionStore {
        int appliedVersion();
        void setAppliedVersion(int version);
    }

    public static final class Builder {
        private final List<Migration> migrations = new ArrayList<>();
        public Builder add(int version, String... sql) {
            List<String> list = new ArrayList<>();
            for (String s : sql) list.add(s);
            migrations.add(new Migration() {
                public int version() { return version; }
                public List<String> statements() { return list; }
            });
            return this;
        }
        public List<Migration> build() {
            migrations.sort(Comparator.comparingInt(Migration::version));
            return migrations;
        }
    }

    /**
     * Returns the ordered list of statements to run to move the store from
     * {@code from} to target version. Throws if a gap in versions exists.
     */
    public static List<String> plan(List<Migration> migrations, int from, int to) {
        List<String> out = new ArrayList<>();
        if (to <= from) return out;
        for (Migration m : migrations) {
            if (m.version() > from && m.version() <= to) {
                out.addAll(m.statements());
            }
        }
        // gap detection
        Migration last = null;
        for (int v = from + 1; v <= to; v++) {
            boolean found = false;
            for (Migration m : migrations) if (m.version() == v) { found = true; break; }
            if (!found) throw new IllegalArgumentException("missing migration version " + v);
        }
        return out;
    }

    /** Execute the plan against an executor and record the new version. */
    public interface Executor {
        void execute(String sql);
    }

    public static void apply(List<Migration> migrations, VersionStore store,
                             int to, Executor executor) {
        int from = store.appliedVersion();
        for (String sql : plan(migrations, from, to)) executor.execute(sql);
        store.setAppliedVersion(to);
    }

    private Migrations() { }
}