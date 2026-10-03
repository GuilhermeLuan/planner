package dev.guilhermeluan.planner.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.guilhermeluan.planner.storage.AccountEntity
import dev.guilhermeluan.planner.storage.PlannerEntity
import dev.guilhermeluan.planner.storage.PlannerDatabase
import dev.guilhermeluan.planner.storage.TaskEntity
import dev.guilhermeluan.planner.storage.RoutineEntity
import dev.guilhermeluan.planner.storage.RoutineOccurrenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import dev.guilhermeluan.planner.storage.SessionMetadataEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BackupExporterTest {
    private lateinit var database: PlannerDatabase

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
    fun `tasks are ordered deterministically by id`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-det", username = "Det", timezone = "America/New_York", mustChangePassword = false),
            PlannerEntity(id = "plan-det", accountId = "acc-det"),
        )
        database.plannerDao().writeLocalTask(
            TaskEntity("t-b", "acc-det", "plan-det", "Segunda", "2026-07-13", null, "PENDING", false, 0, "z"),
        )
        database.plannerDao().writeLocalTask(
            TaskEntity("t-a", "acc-det", "plan-det", "Primeira", "2026-07-12", "10:00", "DONE", false, 0, "a"),
        )

        val tarefas = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-det").getJSONArray("tarefas")

        assertEquals("t-a", tarefas.getJSONObject(0).getString("id"))
        assertEquals("t-b", tarefas.getJSONObject(1).getString("id"))
    }

    @Test
    fun `internal session metadata is not in export`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-excl", username = "Excluído", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-excl", accountId = "acc-excl"),
        )

        database.openHelper.writableDatabase.execSQL(
            """INSERT INTO session_metadata (key, value) VALUES ('some_key', 'should_not_appear')""",
        )

        val export = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-excl")

        val keys = mutableListOf<String>()
        val iter = export.keys()
        while (iter.hasNext()) keys.add(iter.next())

        assertEquals(7, keys.size)
        assertTrue("versao" in keys)
        assertTrue("exportadoEm" in keys)
        assertTrue("conta" in keys)
        assertTrue("planner" in keys)
        assertTrue("tarefas" in keys)
        assertTrue("rotinas" in keys)
        assertTrue("ocorrencias" in keys)
    }

    @Test
    fun `export with no tasks or routines produces empty arrays`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-empty", username = "Vazia", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-empty", accountId = "acc-empty"),
        )
        val exporter = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        )
        val json = exporter.export("acc-empty")
        assertEquals(0, json.getJSONArray("tarefas").length())
        assertEquals(0, json.getJSONArray("rotinas").length())
        assertEquals(0, json.getJSONArray("ocorrencias").length())
    }

    @Test
    fun `export includes account planner tasks routines and occurrences`() = runTest {
        val sessionDao = database.sessionDao()
        val plannerDao = database.plannerDao()
        val routineDao = database.routineDao()

        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-1", username = "Gui", timezone = "America/Sao_Paulo", mustChangePassword = false),
            PlannerEntity(id = "plan-1", accountId = "acc-1"),
        )

        plannerDao.writeLocalTask(
            TaskEntity(
                id = "task-1", accountId = "acc-1", plannerId = "plan-1",
                title = "Comprar pão", day = "2026-07-12", time = "08:00",
                status = "PENDING", archived = false, version = 0, updatedAt = "2026-07-12T06:00:00Z",
            ),
        )
        plannerDao.writeLocalTask(
            TaskEntity(
                id = "task-2", accountId = "acc-1", plannerId = "plan-1",
                title = "Enviar relatório", day = "2026-07-13", time = null,
                status = "DONE", archived = true, version = 0, updatedAt = "2026-07-12T06:00:00Z",
            ),
        )

        routineDao.writeLocalRoutine(
            RoutineEntity(
                id = "rot-1", accountId = "acc-1", plannerId = "plan-1",
                title = "Meditar", weekdays = "1,3,5", startDate = "2026-01-01", time = "07:00",
                status = "ACTIVE", version = 0, updatedAt = "2026-01-01T00:00:00Z",
            ),
        )

        routineDao.writeLocalOccurrence(
            RoutineOccurrenceEntity(
                id = "rot-1:2026-07-13", accountId = "acc-1", routineId = "rot-1",
                title = "Meditar", day = "2026-07-13", time = "07:00",
                status = "DONE", version = 0, updatedAt = "2026-07-13T10:00:00Z",
            ),
        )

        val exporter = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        )

        val json = exporter.export("acc-1")

        assertEquals(1, json.getInt("versao"))
        assertEquals("2026-07-12T12:00:00Z", json.getString("exportadoEm"))

        val conta = json.getJSONObject("conta")
        assertEquals("acc-1", conta.getString("id"))
        assertEquals("Gui", conta.getString("nome"))
        assertEquals("America/Sao_Paulo", conta.getString("fuso"))

        val planner = json.getJSONObject("planner")
        assertEquals("plan-1", planner.getString("id"))

        val tarefas = json.getJSONArray("tarefas")
        assertEquals(2, tarefas.length())

        val tarefa1 = tarefas.getJSONObject(0)
        assertEquals("task-1", tarefa1.getString("id"))
        assertEquals("Comprar pão", tarefa1.getString("titulo"))
        assertEquals("2026-07-12", tarefa1.getString("dia"))
        assertEquals("08:00", tarefa1.getString("horario"))
        assertEquals("PENDING", tarefa1.getString("status"))
        assertEquals(false, tarefa1.getBoolean("arquivada"))

        val tarefa2 = tarefas.getJSONObject(1)
        assertEquals("task-2", tarefa2.getString("id"))
        assertEquals("Enviar relatório", tarefa2.getString("titulo"))
        assertEquals("2026-07-13", tarefa2.getString("dia"))
        assertTrue(tarefa2.isNull("horario"))
        assertEquals("DONE", tarefa2.getString("status"))
        assertEquals(true, tarefa2.getBoolean("arquivada"))

        val rotinas = json.getJSONArray("rotinas")
        assertEquals(1, rotinas.length())

        val rotina = rotinas.getJSONObject(0)
        assertEquals("rot-1", rotina.getString("id"))
        assertEquals("Meditar", rotina.getString("titulo"))
        assertEquals(JSONArray(listOf(1, 3, 5)).toString(), rotina.getJSONArray("diasDaSemana").toString())
        assertEquals("2026-01-01", rotina.getString("dataInicio"))
        assertEquals("07:00", rotina.getString("horario"))
        assertEquals("ACTIVE", rotina.getString("status"))

        val ocorrencias = json.getJSONArray("ocorrencias")
        assertEquals(1, ocorrencias.length())

        val ocorrencia = ocorrencias.getJSONObject(0)
        assertEquals("rot-1:2026-07-13", ocorrencia.getString("id"))
        assertEquals("rot-1", ocorrencia.getString("rotinaId"))
        assertEquals("Meditar", ocorrencia.getString("titulo"))
        assertEquals("2026-07-13", ocorrencia.getString("dia"))
        assertEquals("07:00", ocorrencia.getString("horario"))
        assertEquals("DONE", ocorrencia.getString("status"))
    }

    @Test
    fun `the last saved backup is remembered with the moment it was saved`() = runTest {
        val exporter = BackupExporter(database, Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneOffset.UTC))
        assertEquals(null, exporter.observeLastSaved().first())

        exporter.recordSaved()

        assertEquals(Instant.parse("2026-09-28T15:00:00Z"), exporter.observeLastSaved().first())
    }
}
