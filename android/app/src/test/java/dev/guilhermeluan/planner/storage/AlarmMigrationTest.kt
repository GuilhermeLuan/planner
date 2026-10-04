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
class AlarmMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Banco na versão 8, com Remédios já cadastrados. */
    private fun versionEightDatabase(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE accounts (id TEXT NOT NULL PRIMARY KEY, username TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE planners (id TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .name(null)
                .build(),
        )
        return helper.writableDatabase.also { db ->
            listOf(
                PlannerDatabase.MIGRATION_3_4, PlannerDatabase.MIGRATION_4_5,
                PlannerDatabase.MIGRATION_5_6, PlannerDatabase.MIGRATION_6_7, PlannerDatabase.MIGRATION_7_8,
            ).forEach { it.migrate(db) }
        }
    }

    private fun columns(db: SupportSQLiteDatabase): List<String> =
        db.query("PRAGMA table_info(medicines)").use { c ->
            generateSequence { if (c.moveToNext()) "${c.getString(1)}:${c.getString(2)}:${c.getInt(3)}:${c.getInt(5)}" else null }
                .toList().sorted()
        }

    @Test
    fun migrationEightToNineKeepsMedicinesWithTheAlarmOff() {
        val db = versionEightDatabase()
        db.execSQL("INSERT INTO accounts (id, username) VALUES ('a', 'ana')")
        db.execSQL("INSERT INTO planners (id, accountId) VALUES ('p', 'a')")
        db.execSQL(
            "INSERT INTO medicines (id, accountId, plannerId, name, amount, unit, repeatKind, repeatWeekdays, startDate, status, updatedAt) " +
                "VALUES ('m', 'a', 'p', 'Magnésio', 2, 'TABLET', 'DAILY', '', '2026-10-01', 'ACTIVE', '2026-10-01T00:00:00Z')",
        )

        PlannerDatabase.MIGRATION_8_9.migrate(db)

        db.query("SELECT name, alarmDelayMinutes FROM medicines WHERE id = 'm'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Magnésio", c.getString(0))
            assertTrue("sem alarme", c.isNull(1))
        }
    }

    @Test
    fun migratedMedicinesTableMatchesTheSchemaRoomGenerates() {
        val db = versionEightDatabase()
        PlannerDatabase.MIGRATION_8_9.migrate(db)
        val room = Room.inMemoryDatabaseBuilder(context, PlannerDatabase::class.java).build()

        assertEquals(columns(room.openHelper.writableDatabase), columns(db))
        room.close()
    }

    @Test
    fun migrationEightToNineStartsWithoutAlarmSnoozes() {
        val db = versionEightDatabase()

        PlannerDatabase.MIGRATION_8_9.migrate(db)

        db.query("SELECT COUNT(*) FROM dose_alarm_snoozes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }
}
