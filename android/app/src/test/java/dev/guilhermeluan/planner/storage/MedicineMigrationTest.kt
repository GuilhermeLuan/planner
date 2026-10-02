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
class MedicineMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun versionThreeDatabase(): SupportSQLiteDatabase {
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
        return helper.writableDatabase
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
    fun migrationThreeToFourKeepsExistingDataAndAddsMedicineTables() {
        val db = versionThreeDatabase()
        db.execSQL("INSERT INTO routines (id, title) VALUES ('routine-1', 'Caminhar')")

        PlannerDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT title FROM routines WHERE id = 'routine-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Caminhar", c.getString(0))
        }
        listOf("medicines", "medicine_times", "dose_records").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun migratedMedicineTablesMatchTheSchemaRoomGenerates() {
        val db = versionThreeDatabase()
        PlannerDatabase.MIGRATION_3_4.migrate(db)
        val room = Room.inMemoryDatabaseBuilder(context, PlannerDatabase::class.java).build()
        val fresh = room.openHelper.writableDatabase

        listOf("medicines", "medicine_times", "dose_records").forEach { table ->
            assertEquals(table, shape(fresh, table), shape(db, table))
        }
        room.close()
    }
}
