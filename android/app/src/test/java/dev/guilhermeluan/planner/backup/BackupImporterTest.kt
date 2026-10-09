package dev.guilhermeluan.planner.backup

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.storage.AccountEntity
import dev.guilhermeluan.planner.storage.DoseAlarmSnoozeEntity
import dev.guilhermeluan.planner.storage.DoseRecordEntity
import dev.guilhermeluan.planner.storage.DoseSnoozeEntity
import dev.guilhermeluan.planner.storage.MedicineEntity
import dev.guilhermeluan.planner.storage.MedicineTimeEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.PlannerEntity
import dev.guilhermeluan.planner.storage.RoutineEntity
import dev.guilhermeluan.planner.storage.TaskEntity
import dev.guilhermeluan.planner.storage.WaterIntakeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BackupImporterTest {
    private lateinit var database: PlannerDatabase
    private val clock = Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PlannerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `v1 backup on an empty device becomes the local planner`() = runTest {
        val expected = fixture("backup-v1.json")

        BackupImporter(database, clock).import(BackupReader.read(expected))

        val exported = BackupExporter(database, clock).export("acc-1")
        for (key in listOf("conta", "planner", "tarefas", "rotinas", "ocorrencias")) {
            assertEquals(expected.get(key).canonical(), exported.get(key).canonical())
        }
        assertEquals(0, exported.getJSONArray("remedios").length())
        assertEquals(0, exported.getJSONArray("doses").length())
        val agua = exported.getJSONObject("agua")
        assertEquals(0, agua.getJSONArray("metas").length())
        assertEquals(0, agua.getJSONArray("consumo").length())
        assertEquals(JSONObject.NULL, agua.get("lembrete"))
        assertEquals("acc-1", database.sessionDao().activeAccountId())
    }

    @Test
    fun `v2 backup on an empty device exports back the same backup`() = runTest {
        BackupImporter(database, clock).import(BackupReader.read(fixture("backup-v2.json")))

        val exported = BackupExporter(database, clock).export("acc-1")

        assertEquals(fixture("backup-v2.json").comparableContent(), exported.comparableContent())
    }

    @Test
    fun `import replaces the previous planner and returns the ids of its tasks`() = runTest {
        seedOldPlanner()

        val removedTaskIds = BackupImporter(database, clock).import(BackupReader.read(fixture("backup-v2.json")))

        assertEquals(listOf("task-old"), removedTaskIds)
        assertNull(database.sessionDao().account("old-acc"))
        assertNull(database.sessionDao().planner("old-acc"))
        assertEquals(emptyList<TaskEntity>(), database.plannerDao().tasksByAccount("old-acc"))
        assertEquals(emptyList<RoutineEntity>(), database.routineDao().routinesByAccount("old-acc"))
        assertEquals(emptyList<MedicineEntity>(), database.medicineDao().medicinesByAccount("old-acc"))
        assertEquals(emptyList<DoseRecordEntity>(), database.medicineDao().doseRecordsByAccount("old-acc"))
        assertEquals(emptyList<WaterIntakeEntity>(), database.waterDao().intakesByAccount("old-acc"))
        assertEquals(emptyList<DoseSnoozeEntity>(), database.medicineDao().observeDoseSnoozes("old-acc", "2026-07-12").first())
        assertEquals(
            emptyList<DoseAlarmSnoozeEntity>(),
            database.medicineDao().observeDoseAlarmSnoozes("old-acc", "2026-07-12").first(),
        )
    }

    @Test
    fun `importing the same backup twice keeps a single copy of each item`() = runTest {
        val backup = BackupReader.read(fixture("backup-v2.json"))
        val importer = BackupImporter(database, clock)

        importer.import(backup)
        importer.import(backup)

        val exported = BackupExporter(database, clock).export("acc-1")
        assertEquals(fixture("backup-v2.json").comparableContent(), exported.comparableContent())
    }

    @Test
    fun `imported account takes the timezone of the backup even when the previous account had another`() = runTest {
        database.sessionDao().createLocalPlanner(
            AccountEntity(id = "acc-1", username = "Ana", timezone = "Europe/Lisbon", mustChangePassword = false),
            PlannerEntity(id = "plan-1", accountId = "acc-1"),
        )

        BackupImporter(database, clock).import(BackupReader.read(fixture("backup-v2.json")))

        assertEquals("America/Sao_Paulo", database.sessionDao().account("acc-1")?.timezone)
    }

    @Test
    fun `imported tasks get version 0 and the import moment, and doses keep the moment taken`() = runTest {
        BackupImporter(database, clock).import(BackupReader.read(fixture("backup-v2.json")))

        val tarefa = checkNotNull(database.plannerDao().task("acc-1", "task-1"))
        assertEquals(0L, tarefa.version)
        assertEquals("2026-07-14T12:00:00Z", tarefa.updatedAt)
        val dose = BackupExporter(database, clock).export("acc-1").getJSONArray("doses").getJSONObject(0)
        assertEquals("2026-07-13T11:02:15Z", dose.getString("tomadaEm"))
    }

    @Test
    fun `failed import keeps the previous planner and the active account`() = runTest {
        seedOldPlanner()
        val before = BackupExporter(database, clock).export("old-acc")
        val backup = BackupReader.read(fixture("backup-v2.json")).copy(
            doses = listOf(
                BackupDose(
                    medicineId = "remedio-inexistente",
                    day = LocalDate.of(2026, 7, 13),
                    time = LocalTime.of(8, 0),
                    status = "TAKEN",
                    takenAt = null,
                    stockDeducted = 0,
                ),
            ),
        )

        val error = runCatching { BackupImporter(database, clock).import(backup) }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        assertEquals(before.comparableContent(), BackupExporter(database, clock).export("old-acc").comparableContent())
        assertEquals("old-acc", database.sessionDao().activeAccountId())
    }

    private suspend fun seedOldPlanner() {
        database.sessionDao().createLocalPlanner(
            AccountEntity(id = "old-acc", username = "Antiga", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-old", accountId = "old-acc"),
        )
        database.plannerDao().writeLocalTask(
            TaskEntity(
                id = "task-old", accountId = "old-acc", plannerId = "plan-old",
                title = "Tarefa antiga", day = "2026-07-12", time = "09:00",
                status = "PENDING", archived = false, version = 0, updatedAt = "2026-07-01T10:00:00Z",
            ),
        )
        database.routineDao().writeLocalRoutine(
            RoutineEntity(
                id = "rot-old", accountId = "old-acc", plannerId = "plan-old",
                title = "Rotina antiga", weekdays = "1", startDate = "2026-07-01", time = null,
                status = "ACTIVE", version = 0, updatedAt = "2026-07-01T10:00:00Z",
            ),
        )
        database.medicineDao().writeLocalMedicine(
            MedicineEntity(
                id = "med-old", accountId = "old-acc", plannerId = "plan-old",
                name = "Remédio antigo", amount = 1, unit = "TABLET",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
            ),
            listOf(MedicineTimeEntity("med-old", "08:00")),
        )
        database.medicineDao().upsertDoseRecord(
            DoseRecordEntity(
                medicineId = "med-old", day = "2026-07-11", time = "08:00", accountId = "old-acc",
                status = "TAKEN", takenAt = "2026-07-11T08:05:00Z", updatedAt = "2026-07-11T08:05:00Z",
            ),
        )
        database.medicineDao().upsertDoseSnooze(
            DoseSnoozeEntity(
                medicineId = "med-old", day = "2026-07-12", time = "08:00", accountId = "old-acc",
                snoozedUntil = "2026-07-12T08:31:00Z",
            ),
        )
        database.medicineDao().upsertDoseAlarmSnooze(
            DoseAlarmSnoozeEntity(
                medicineId = "med-old", day = "2026-07-12", time = "08:00", accountId = "old-acc",
                snoozedUntil = "2026-07-12T08:47:00Z",
            ),
        )
        database.waterDao().upsertIntake(
            WaterIntakeEntity(accountId = "old-acc", day = "2026-07-12", totalMl = 500, updatedAt = "2026-07-12T12:00:00Z"),
        )
    }

    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/backup/$name")!!.readText())
}

/** Forma comparável de um JSON: chaves em ordem alfabética, arrays na ordem original. */
private fun Any?.canonical(): Any? = when (this) {
    is JSONObject -> keys().asSequence().sorted().associateWith { key -> opt(key).canonical() }
    is JSONArray -> List(length()) { index -> opt(index).canonical() }
    else -> this
}

/** Conteúdo do JSON para comparar: sem [exportadoEm], que muda a cada export. */
private fun JSONObject.comparableContent(): Any? = apply { remove("exportadoEm") }.canonical()
