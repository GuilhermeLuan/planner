package dev.guilhermeluan.planner.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        PlannerEntity::class,
        SessionMetadataEntity::class,
        TaskEntity::class,
        RoutineEntity::class,
        RoutineOccurrenceEntity::class,
        MedicineEntity::class,
        MedicineTimeEntity::class,
        DoseRecordEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun plannerDao(): PlannerDao
    abstract fun routineDao(): RoutineDao
    abstract fun medicineDao(): MedicineDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS routines (
                        id TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT NOT NULL,
                        plannerId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        weekdays TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        time TEXT,
                        status TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        updatedAt TEXT NOT NULL,
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(plannerId) REFERENCES planners(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routines_accountId ON routines(accountId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routines_plannerId ON routines(plannerId)")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS routine_occurrences (
                        id TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT NOT NULL,
                        routineId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        day TEXT NOT NULL,
                        time TEXT,
                        status TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        updatedAt TEXT NOT NULL,
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_occurrences_accountId ON routine_occurrences(accountId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_occurrences_routineId ON routine_occurrences(routineId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_occurrences_accountId_day ON routine_occurrences(accountId, day)")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS outbox")
                database.execSQL("DROP TABLE IF EXISTS sync_state")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS medicines (
                        id TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT NOT NULL,
                        plannerId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        unit TEXT NOT NULL,
                        repeatKind TEXT NOT NULL,
                        repeatWeekdays TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT,
                        status TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(plannerId) REFERENCES planners(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_medicines_accountId ON medicines(accountId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_medicines_plannerId ON medicines(plannerId)")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS medicine_times (
                        medicineId TEXT NOT NULL,
                        time TEXT NOT NULL,
                        PRIMARY KEY(medicineId, time),
                        FOREIGN KEY(medicineId) REFERENCES medicines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_medicine_times_medicineId ON medicine_times(medicineId)")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS dose_records (
                        medicineId TEXT NOT NULL,
                        day TEXT NOT NULL,
                        time TEXT NOT NULL,
                        accountId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        takenAt TEXT,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(medicineId, day, time),
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(medicineId) REFERENCES medicines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_dose_records_accountId_day ON dose_records(accountId, day)")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE medicines ADD COLUMN stockAmount INTEGER")
                database.execSQL("ALTER TABLE medicines ADD COLUMN stockCapacity INTEGER")
                database.execSQL("ALTER TABLE medicines ADD COLUMN stockThreshold INTEGER")
                database.execSQL("ALTER TABLE dose_records ADD COLUMN stockDeducted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
