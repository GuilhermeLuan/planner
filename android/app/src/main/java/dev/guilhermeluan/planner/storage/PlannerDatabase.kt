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
    ],
    version = 3,
    exportSchema = false,
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun plannerDao(): PlannerDao
    abstract fun routineDao(): RoutineDao

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
    }
}
