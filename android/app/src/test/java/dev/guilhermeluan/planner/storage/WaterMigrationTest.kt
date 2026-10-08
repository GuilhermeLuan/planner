package dev.guilhermeluan.planner.storage

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WaterMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Banco na versão 7, com as tabelas anteriores que a migração precisa preservar. */
    private fun versionSevenDatabase(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE accounts (id TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE planners (id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE routines (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .name(null)
                .build(),
        )
        return helper.writableDatabase.also { db ->
            listOf(
                PlannerDatabase.MIGRATION_3_4, PlannerDatabase.MIGRATION_4_5,
                PlannerDatabase.MIGRATION_5_6, PlannerDatabase.MIGRATION_6_7,
            ).forEach { it.migrate(db) }
        }
    }

    /** Banco na versão 9, com as tabelas que a migração 9→10 precisa preservar. */
    private fun versionNineDatabase(): SupportSQLiteDatabase {
        val db = versionSevenDatabase()
        listOf(PlannerDatabase.MIGRATION_7_8, PlannerDatabase.MIGRATION_8_9).forEach { it.migrate(db) }
        return db
    }

    private fun shape(db: SupportSQLiteDatabase, table: String): List<String> {
        val columns = db.query("PRAGMA table_info($table)").use { c ->
            generateSequence { if (c.moveToNext()) "${c.getString(1)}:${c.getString(2)}:${c.getInt(3)}:${c.getInt(5)}" else null }.toList()
        }
        val foreignKeys = db.query("PRAGMA foreign_key_list($table)").use { c ->
            generateSequence { if (c.moveToNext()) "fk:${c.getString(2)}:${c.getString(3)}:${c.getString(4)}:${c.getString(6)}" else null }.toList()
        }
        val indices = db.query("PRAGMA index_list($table)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.filter { !it.startsWith("sqlite_") }.toList()
        }
        return columns.sorted() + foreignKeys.sorted() + indices.sorted()
    }

    @Test
    fun migrationSevenToEightKeepsExistingDataAndStartsWithoutWater() {
        val db = versionSevenDatabase()
        db.execSQL("INSERT INTO accounts (id, username) VALUES ('a', 'ana')")
        db.execSQL("INSERT INTO routines (id, title) VALUES ('routine-1', 'Caminhar')")

        PlannerDatabase.MIGRATION_7_8.migrate(db)

        db.query("SELECT title FROM routines WHERE id = 'routine-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Caminhar", c.getString(0))
        }
        listOf("water_goals", "water_intakes").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun migratedWaterTablesMatchTheSchemaRoomGenerates() {
        val db = versionSevenDatabase()
        PlannerDatabase.MIGRATION_7_8.migrate(db)
        val room = Room.inMemoryDatabaseBuilder(context, PlannerDatabase::class.java).build()
        val fresh = room.openHelper.writableDatabase

        listOf("water_goals", "water_intakes").forEach { table ->
            assertEquals(table, shape(fresh, table), shape(db, table))
        }
        room.close()
    }

    @Test
    fun migrationNineToTenKeepsExistingDataAndStartsWithoutReminderSettings() {
        val db = versionNineDatabase()
        db.execSQL("INSERT INTO accounts (id, username) VALUES ('a', 'ana')")
        db.execSQL("INSERT INTO water_goals (accountId, validFrom, goalMl) VALUES ('a', '2026-10-01', 2500)")

        PlannerDatabase.MIGRATION_9_10.migrate(db)

        db.query("SELECT goalMl FROM water_goals WHERE accountId = 'a'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2500, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM water_reminder_settings").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migratedReminderSettingsTableMatchesTheSchemaRoomGenerates() {
        val db = versionNineDatabase()
        PlannerDatabase.MIGRATION_9_10.migrate(db)
        val room = Room.inMemoryDatabaseBuilder(context, PlannerDatabase::class.java).build()
        val fresh = room.openHelper.writableDatabase

        assertEquals("water_reminder_settings", shape(fresh, "water_reminder_settings"), shape(db, "water_reminder_settings"))
        room.close()
    }
}
