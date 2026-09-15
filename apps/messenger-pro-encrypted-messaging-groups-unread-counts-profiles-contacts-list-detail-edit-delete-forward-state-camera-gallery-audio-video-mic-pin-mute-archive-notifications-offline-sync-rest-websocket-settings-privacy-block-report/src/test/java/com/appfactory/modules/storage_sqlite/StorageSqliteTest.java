package com.appfactory.modules.storage_sqlite;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static com.appfactory.modules.storage_sqlite.Schema.Type.*;
import com.appfactory.modules.storage_sqlite.Migrations.Migration;

public class StorageSqliteTest {

    @Test public void tableDdl() {
        Schema.Table t = new Schema.Table() {
            public String name() { return "items"; }
            public Schema.Column[] columns() {
                return new Schema.Column[] {
                    new Schema.Column("id", INTEGER).primaryKey().autoIncrement(),
                    new Schema.Column("title", TEXT).notNull(),
                    new Schema.Column("detail", TEXT)
                };
            }
        };
        String sql = t.createSql();
        assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS"));
        assertTrue(sql.contains("id INTEGER PRIMARY KEY AUTOINCREMENT"));
        assertTrue(sql.contains("title TEXT NOT NULL"));
        assertTrue(sql.contains("detail TEXT"));
    }

    @Test public void indexDdl() {
        Schema.Index idx = new Schema.Index("idx_items_title", "items", false, "title", "detail");
        assertEquals("CREATE INDEX IF NOT EXISTS idx_items_title ON items(title,detail)",
                idx.sql());
    }

    @Test public void migrationPlanAcrossVersions() {
        Migrations.Builder b = new Migrations.Builder();
        b.add(1, "CREATE TABLE t1(id INTEGER)");
        b.add(2, "ALTER TABLE t1 ADD COLUMN name TEXT");
        List<Migration> ms = b.build();
        List<String> plan = Migrations.plan(ms, 0, 2);
        assertEquals(2, plan.size());
        assertTrue(plan.get(0).startsWith("CREATE TABLE"));
        assertTrue(plan.get(1).startsWith("ALTER TABLE"));
    }

    @Test public void incrementalMigration() {
        Migrations.Builder b = new Migrations.Builder();
        b.add(1, "CREATE TABLE a(x INTEGER)");
        b.add(2, "CREATE TABLE b(y INTEGER)");
        List<Migration> ms = b.build();
        // from version 1 -> 2 only runs the second
        List<String> plan = Migrations.plan(ms, 1, 2);
        assertEquals(1, plan.size());
        assertTrue(plan.get(0).contains("TABLE b"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void detectsMissingMigration() {
        Migrations.Builder b = new Migrations.Builder();
        b.add(1, "x");
        b.add(3, "y");
        Migrations.plan(b.build(), 0, 3);
    }
}