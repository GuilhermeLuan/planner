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
import dev.guilhermeluan.planner.storage.MedicineEntity
import dev.guilhermeluan.planner.storage.MedicineTimeEntity
import dev.guilhermeluan.planner.storage.DoseAlarmSnoozeEntity
import dev.guilhermeluan.planner.storage.DoseRecordEntity
import dev.guilhermeluan.planner.storage.DoseSnoozeEntity
import dev.guilhermeluan.planner.storage.MedicinePreviousVersionEntity
import dev.guilhermeluan.planner.storage.WaterGoalEntity
import dev.guilhermeluan.planner.storage.WaterIntakeEntity
import dev.guilhermeluan.planner.storage.WaterReminderSettingsEntity
import dev.guilhermeluan.planner.tasks.MedicineStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import dev.guilhermeluan.planner.storage.SessionMetadataEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(10, keys.size)
        assertTrue("versao" in keys)
        assertTrue("exportadoEm" in keys)
        assertTrue("conta" in keys)
        assertTrue("planner" in keys)
        assertTrue("tarefas" in keys)
        assertTrue("rotinas" in keys)
        assertTrue("ocorrencias" in keys)
        assertTrue("remedios" in keys)
        assertTrue("doses" in keys)
        assertTrue("agua" in keys)
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

        assertEquals(2, json.getInt("versao"))
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
    fun `medicine with times, dose, repeat, stock and alarm is exported`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-med", username = "Ana", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-med", accountId = "acc-med"),
        )
        database.medicineDao().writeLocalMedicine(
            MedicineEntity(
                id = "med-1", accountId = "acc-med", plannerId = "plan-med",
                name = "Vitamina D", amount = 2, unit = "TABLET",
                repeatKind = "WEEKDAYS", repeatWeekdays = "1,3,5", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
                stockAmount = 28, stockCapacity = 30, stockThreshold = 5, alarmDelayMinutes = 10,
            ),
            listOf(MedicineTimeEntity("med-1", "20:00"), MedicineTimeEntity("med-1", "08:00")),
        )

        val json = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-med")

        val remedio = json.getJSONArray("remedios").getJSONObject(0)
        assertEquals("med-1", remedio.getString("id"))
        assertEquals("Vitamina D", remedio.getString("nome"))

        val dose = remedio.getJSONObject("dose")
        assertEquals(2, dose.getInt("quantidade"))
        assertEquals("TABLET", dose.getString("unidade"))

        assertEquals(JSONArray(listOf("08:00", "20:00")).toString(), remedio.getJSONArray("horarios").toString())

        val repeticao = remedio.getJSONObject("repeticao")
        assertEquals("WEEKDAYS", repeticao.getString("tipo"))
        assertEquals(JSONArray(listOf(1, 3, 5)).toString(), repeticao.getJSONArray("diasDaSemana").toString())
        assertEquals("2026-07-01", repeticao.getString("dataInicio"))
        assertEquals(JSONObject.NULL, repeticao.get("dataFim"))

        assertEquals("ACTIVE", remedio.getString("status"))

        val estoque = remedio.getJSONObject("estoque")
        assertEquals(28, estoque.getInt("quantidade"))
        assertEquals(30, estoque.getInt("capacidade"))
        assertEquals(5, estoque.getInt("limiteAviso"))

        assertEquals(10, remedio.getJSONObject("alarme").getInt("atrasoMinutos"))
    }

    @Test
    fun `medicine without stock and alarm exports them as null`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-sem-estoque", username = "Bia", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-sem-estoque", accountId = "acc-sem-estoque"),
        )
        database.medicineDao().writeLocalMedicine(
            MedicineEntity(
                id = "med-2", accountId = "acc-sem-estoque", plannerId = "plan-sem-estoque",
                name = "Magnésio", amount = 1, unit = "CAPSULE",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
            ),
            listOf(MedicineTimeEntity("med-2", "21:30")),
        )

        val remedio = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-sem-estoque").getJSONArray("remedios").getJSONObject(0)

        assertEquals(JSONObject.NULL, remedio.get("estoque"))
        assertEquals(JSONObject.NULL, remedio.get("alarme"))
        assertEquals(0, remedio.getJSONObject("repeticao").getJSONArray("diasDaSemana").length())
    }

    @Test
    fun `stock without capacity and threshold exports them as null`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-estoque-parcial", username = "Caio", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-estoque-parcial", accountId = "acc-estoque-parcial"),
        )
        database.medicineDao().writeLocalMedicine(
            MedicineEntity(
                id = "med-3", accountId = "acc-estoque-parcial", plannerId = "plan-estoque-parcial",
                name = "Ferro", amount = 1, unit = "TABLET",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
                stockAmount = 7,
            ),
            listOf(MedicineTimeEntity("med-3", "09:00")),
        )

        val estoque = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-estoque-parcial").getJSONArray("remedios").getJSONObject(0).getJSONObject("estoque")

        assertEquals(7, estoque.getInt("quantidade"))
        assertEquals(JSONObject.NULL, estoque.get("capacidade"))
        assertEquals(JSONObject.NULL, estoque.get("limiteAviso"))
    }

    @Test
    fun `medicines are ordered deterministically by id`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-ordem", username = "Lu", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-ordem", accountId = "acc-ordem"),
        )
        val medicineDao = database.medicineDao()
        medicineDao.writeLocalMedicine(
            MedicineEntity(
                id = "med-b", accountId = "acc-ordem", plannerId = "plan-ordem",
                name = "Magnésio", amount = 1, unit = "CAPSULE",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
            ),
            listOf(MedicineTimeEntity("med-b", "21:30")),
        )
        medicineDao.writeLocalMedicine(
            MedicineEntity(
                id = "med-a", accountId = "acc-ordem", plannerId = "plan-ordem",
                name = "Vitamina D", amount = 1, unit = "TABLET",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
            ),
            listOf(MedicineTimeEntity("med-a", "08:00")),
        )

        val remedios = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-ordem").getJSONArray("remedios")

        assertEquals("med-a", remedios.getJSONObject(0).getString("id"))
        assertEquals("med-b", remedios.getJSONObject(1).getString("id"))
    }

    @Test
    fun `recorded doses are exported with the moment taken and the stock deducted`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-doses", username = "Lia", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-doses", accountId = "acc-doses"),
        )
        val medicineDao = database.medicineDao()
        medicineDao.writeLocalMedicine(
            MedicineEntity(
                id = "med-1", accountId = "acc-doses", plannerId = "plan-doses",
                name = "Vitamina D", amount = 2, unit = "TABLET",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
                stockAmount = 28, stockCapacity = 30, stockThreshold = 5,
            ),
            listOf(MedicineTimeEntity("med-1", "08:00"), MedicineTimeEntity("med-1", "20:00")),
        )
        medicineDao.upsertDoseRecord(
            DoseRecordEntity(
                medicineId = "med-1", day = "2026-07-13", time = "08:00", accountId = "acc-doses",
                status = "TAKEN", takenAt = "2026-07-13T11:05:00Z", updatedAt = "2026-07-13T11:05:00Z",
                stockDeducted = 2,
            ),
        )
        medicineDao.upsertDoseRecord(
            DoseRecordEntity(
                medicineId = "med-1", day = "2026-07-12", time = "20:00", accountId = "acc-doses",
                status = "SKIPPED", takenAt = null, updatedAt = "2026-07-12T20:00:00Z",
                stockDeducted = 0,
            ),
        )

        val doses = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-doses").getJSONArray("doses")

        assertEquals(2, doses.length())

        val pulada = doses.getJSONObject(0)
        assertEquals("med-1", pulada.getString("remedioId"))
        assertEquals("2026-07-12", pulada.getString("dia"))
        assertEquals("20:00", pulada.getString("horario"))
        assertEquals("SKIPPED", pulada.getString("status"))
        assertEquals(JSONObject.NULL, pulada.get("tomadaEm"))
        assertEquals(0, pulada.getInt("estoqueDescontado"))

        val tomada = doses.getJSONObject(1)
        assertEquals("med-1", tomada.getString("remedioId"))
        assertEquals("2026-07-13", tomada.getString("dia"))
        assertEquals("08:00", tomada.getString("horario"))
        assertEquals("TAKEN", tomada.getString("status"))
        assertEquals("2026-07-13T11:05:00Z", tomada.getString("tomadaEm"))
        assertEquals(2, tomada.getInt("estoqueDescontado"))
    }

    @Test
    fun `previous versions and archived periods of a medicine are exported`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-historico", username = "Rui", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-historico", accountId = "acc-historico"),
        )
        val medicineDao = database.medicineDao()
        val horarios = listOf(MedicineTimeEntity("med-1", "08:00"), MedicineTimeEntity("med-1", "20:00"))
        val vitaminaC = MedicineEntity(
            id = "med-1", accountId = "acc-historico", plannerId = "plan-historico",
            name = "Vitamina C", amount = 1, unit = "TABLET",
            repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
            status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
        )
        val vitaminaD = vitaminaC.copy(
            name = "Vitamina D", amount = 2, repeatKind = "WEEKDAYS", repeatWeekdays = "1,3,5",
            updatedAt = "2026-07-10T09:00:00Z",
        )
        medicineDao.writeLocalMedicine(vitaminaC, horarios)
        medicineDao.writeEditedMedicine(
            MedicinePreviousVersionEntity(
                medicineId = "med-1", until = "2026-07-10", name = "Vitamina C", amount = 1, unit = "TABLET",
                times = "20:00,08:00", repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
            ),
            vitaminaD,
            horarios,
        )
        medicineDao.writeEditedMedicine(
            MedicinePreviousVersionEntity(
                medicineId = "med-1", until = "2026-07-20", name = "Vitamina D", amount = 2, unit = "TABLET",
                times = "08:00,20:00", repeatKind = "WEEKDAYS", repeatWeekdays = "1,3,5", startDate = "2026-07-01", endDate = null,
            ),
            vitaminaD.copy(amount = 3, updatedAt = "2026-07-20T09:00:00Z"),
            horarios,
        )
        medicineDao.writeStatusChange("med-1", MedicineStatus.ARCHIVED, "2026-07-15T08:00:00Z", "2026-07-15")
        medicineDao.writeStatusChange("med-1", MedicineStatus.ACTIVE, "2026-07-18T08:00:00Z", "2026-07-18")
        medicineDao.writeStatusChange("med-1", MedicineStatus.ARCHIVED, "2026-08-01T08:00:00Z", "2026-08-01")

        val remedio = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-historico").getJSONArray("remedios").getJSONObject(0)

        val versoes = remedio.getJSONArray("versoesAnteriores")
        assertEquals(2, versoes.length())

        val primeira = versoes.getJSONObject(0)
        assertEquals("2026-07-10", primeira.getString("ate"))
        assertEquals("Vitamina C", primeira.getString("nome"))
        assertEquals(1, primeira.getJSONObject("dose").getInt("quantidade"))
        assertEquals("TABLET", primeira.getJSONObject("dose").getString("unidade"))
        assertEquals(JSONArray(listOf("08:00", "20:00")).toString(), primeira.getJSONArray("horarios").toString())
        assertEquals("DAILY", primeira.getJSONObject("repeticao").getString("tipo"))
        assertEquals(0, primeira.getJSONObject("repeticao").getJSONArray("diasDaSemana").length())
        assertEquals("2026-07-01", primeira.getJSONObject("repeticao").getString("dataInicio"))
        assertEquals(JSONObject.NULL, primeira.getJSONObject("repeticao").get("dataFim"))

        val segunda = versoes.getJSONObject(1)
        assertEquals("2026-07-20", segunda.getString("ate"))
        assertEquals("Vitamina D", segunda.getString("nome"))
        assertEquals(2, segunda.getJSONObject("dose").getInt("quantidade"))
        assertEquals(JSONArray(listOf(1, 3, 5)).toString(), segunda.getJSONObject("repeticao").getJSONArray("diasDaSemana").toString())

        val periodos = remedio.getJSONArray("periodosArquivados")
        assertEquals(2, periodos.length())
        assertEquals("2026-07-15", periodos.getJSONObject(0).getString("de"))
        assertEquals("2026-07-18", periodos.getJSONObject(0).getString("ate"))
        assertEquals("2026-08-01", periodos.getJSONObject(1).getString("de"))
        assertEquals(JSONObject.NULL, periodos.getJSONObject(1).get("ate"))
    }

    @Test
    fun `water goals, daily intakes and reminder are exported in order`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-agua", username = "Ivo", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-agua", accountId = "acc-agua"),
        )
        val waterDao = database.waterDao()
        waterDao.upsertGoal(WaterGoalEntity(accountId = "acc-agua", validFrom = "2026-08-01", goalMl = 2500))
        waterDao.upsertGoal(WaterGoalEntity(accountId = "acc-agua", validFrom = "2026-07-01", goalMl = 2000))
        waterDao.upsertIntake(
            WaterIntakeEntity(accountId = "acc-agua", day = "2026-07-12", totalMl = 1500, updatedAt = "2026-07-12T20:00:00Z"),
        )
        waterDao.upsertIntake(
            WaterIntakeEntity(accountId = "acc-agua", day = "2026-07-10", totalMl = 750, updatedAt = "2026-07-10T18:00:00Z"),
        )
        waterDao.upsertReminderSettings(
            WaterReminderSettingsEntity(
                accountId = "acc-agua", enabled = true, intervalHours = 2, windowStart = "08:00", windowEnd = "22:00",
            ),
        )

        val agua = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-agua").getJSONObject("agua")

        val metas = agua.getJSONArray("metas")
        assertEquals(2, metas.length())
        assertEquals("2026-07-01", metas.getJSONObject(0).getString("validaDesde"))
        assertEquals(2000, metas.getJSONObject(0).getInt("metaMl"))
        assertEquals("2026-08-01", metas.getJSONObject(1).getString("validaDesde"))
        assertEquals(2500, metas.getJSONObject(1).getInt("metaMl"))

        val consumo = agua.getJSONArray("consumo")
        assertEquals(2, consumo.length())
        assertEquals("2026-07-10", consumo.getJSONObject(0).getString("dia"))
        assertEquals(750, consumo.getJSONObject(0).getInt("totalMl"))
        assertEquals("2026-07-12", consumo.getJSONObject(1).getString("dia"))
        assertEquals(1500, consumo.getJSONObject(1).getInt("totalMl"))

        val lembrete = agua.getJSONObject("lembrete")
        assertEquals(true, lembrete.getBoolean("ativo"))
        assertEquals(2, lembrete.getInt("intervaloHoras"))
        assertEquals("08:00", lembrete.getString("inicio"))
        assertEquals("22:00", lembrete.getString("fim"))
    }

    @Test
    fun `account without medicines or water exports empty arrays and no reminder`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-sem-remedio-agua", username = "Dani", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-sem-remedio-agua", accountId = "acc-sem-remedio-agua"),
        )

        val json = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-sem-remedio-agua")

        assertEquals(0, json.getJSONArray("remedios").length())
        assertEquals(0, json.getJSONArray("doses").length())
        val agua = json.getJSONObject("agua")
        assertEquals(0, agua.getJSONArray("metas").length())
        assertEquals(0, agua.getJSONArray("consumo").length())
        assertEquals(JSONObject.NULL, agua.get("lembrete"))
    }

    @Test
    fun `data of another account never appears in the export`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-a", username = "Ana", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-a", accountId = "acc-a"),
        )
        sessionDao.saveActive(
            AccountEntity(id = "acc-b", username = "Beto", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-b", accountId = "acc-b"),
        )

        val medicineDao = database.medicineDao()
        medicineDao.writeLocalMedicine(
            MedicineEntity(
                id = "med-a", accountId = "acc-a", plannerId = "plan-a",
                name = "Vitamina D", amount = 1, unit = "TABLET",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
            ),
            listOf(MedicineTimeEntity("med-a", "08:00")),
        )
        medicineDao.writeLocalMedicine(
            MedicineEntity(
                id = "med-b", accountId = "acc-b", plannerId = "plan-b",
                name = "Magnésio", amount = 1, unit = "CAPSULE",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
            ),
            listOf(MedicineTimeEntity("med-b", "21:30")),
        )
        medicineDao.upsertDoseRecord(
            DoseRecordEntity(
                medicineId = "med-a", day = "2026-07-12", time = "08:00", accountId = "acc-a",
                status = "TAKEN", takenAt = "2026-07-12T08:05:00Z", updatedAt = "2026-07-12T08:05:00Z",
            ),
        )
        medicineDao.upsertDoseRecord(
            DoseRecordEntity(
                medicineId = "med-b", day = "2026-07-12", time = "21:30", accountId = "acc-b",
                status = "SKIPPED", takenAt = null, updatedAt = "2026-07-12T21:30:00Z",
            ),
        )

        val waterDao = database.waterDao()
        waterDao.upsertGoal(WaterGoalEntity(accountId = "acc-a", validFrom = "2026-07-01", goalMl = 2000))
        waterDao.upsertGoal(WaterGoalEntity(accountId = "acc-b", validFrom = "2026-07-01", goalMl = 3000))
        waterDao.upsertIntake(
            WaterIntakeEntity(accountId = "acc-a", day = "2026-07-12", totalMl = 500, updatedAt = "2026-07-12T12:00:00Z"),
        )
        waterDao.upsertIntake(
            WaterIntakeEntity(accountId = "acc-b", day = "2026-07-12", totalMl = 900, updatedAt = "2026-07-12T12:00:00Z"),
        )
        waterDao.upsertReminderSettings(
            WaterReminderSettingsEntity(
                accountId = "acc-a", enabled = true, intervalHours = 1, windowStart = "09:00", windowEnd = "18:00",
            ),
        )
        waterDao.upsertReminderSettings(
            WaterReminderSettingsEntity(
                accountId = "acc-b", enabled = false, intervalHours = 3, windowStart = "10:00", windowEnd = "20:00",
            ),
        )

        val json = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-a")

        assertEquals(1, json.getJSONArray("remedios").length())
        assertEquals("med-a", json.getJSONArray("remedios").getJSONObject(0).getString("id"))
        assertEquals(1, json.getJSONArray("doses").length())
        assertEquals("med-a", json.getJSONArray("doses").getJSONObject(0).getString("remedioId"))
        val agua = json.getJSONObject("agua")
        assertEquals(1, agua.getJSONArray("metas").length())
        assertEquals(2000, agua.getJSONArray("metas").getJSONObject(0).getInt("metaMl"))
        assertEquals(1, agua.getJSONArray("consumo").length())
        assertEquals(500, agua.getJSONArray("consumo").getJSONObject(0).getInt("totalMl"))
        assertEquals(1, agua.getJSONObject("lembrete").getInt("intervaloHoras"))
        assertFalse(json.toString().contains("med-b"))
    }

    @Test
    fun `dose snoozes are not in export`() = runTest {
        val sessionDao = database.sessionDao()
        sessionDao.createLocalPlanner(
            AccountEntity(id = "acc-soneca", username = "Edu", timezone = "UTC", mustChangePassword = false),
            PlannerEntity(id = "plan-soneca", accountId = "acc-soneca"),
        )
        val medicineDao = database.medicineDao()
        medicineDao.writeLocalMedicine(
            MedicineEntity(
                id = "med-1", accountId = "acc-soneca", plannerId = "plan-soneca",
                name = "Vitamina D", amount = 1, unit = "TABLET",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
            ),
            listOf(MedicineTimeEntity("med-1", "08:00")),
        )
        medicineDao.upsertDoseSnooze(
            DoseSnoozeEntity(
                medicineId = "med-1", day = "2026-07-12", time = "08:00", accountId = "acc-soneca",
                snoozedUntil = "2026-07-12T08:31:00Z",
            ),
        )
        medicineDao.upsertDoseAlarmSnooze(
            DoseAlarmSnoozeEntity(
                medicineId = "med-1", day = "2026-07-12", time = "08:00", accountId = "acc-soneca",
                snoozedUntil = "2026-07-12T08:47:00Z",
            ),
        )

        val json = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-soneca")

        assertEquals(0, json.getJSONArray("doses").length())
        assertFalse(json.toString().contains("2026-07-12T08:31:00Z"))
        assertFalse(json.toString().contains("2026-07-12T08:47:00Z"))
    }

    @Test
    fun `the last saved backup is remembered with the moment it was saved`() = runTest {
        val exporter = BackupExporter(database, Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneOffset.UTC))
        assertEquals(null, exporter.observeLastSaved().first())

        exporter.recordSaved()

        assertEquals(Instant.parse("2026-09-28T15:00:00Z"), exporter.observeLastSaved().first())
    }

    @Test
    fun `an exported backup is read back with medicines doses and water`() = runTest {
        database.sessionDao().createLocalPlanner(
            AccountEntity(id = "acc-rt", username = "Ida", timezone = "America/Sao_Paulo", mustChangePassword = false),
            PlannerEntity(id = "plan-rt", accountId = "acc-rt"),
        )
        database.medicineDao().writeLocalMedicine(
            MedicineEntity(
                id = "med-rt", accountId = "acc-rt", plannerId = "plan-rt",
                name = "Vitamina D", amount = 1, unit = "CAPSULE",
                repeatKind = "DAILY", repeatWeekdays = "", startDate = "2026-07-01", endDate = null,
                status = "ACTIVE", updatedAt = "2026-07-01T10:00:00Z",
                stockAmount = 10, stockCapacity = 30, stockThreshold = 5, alarmDelayMinutes = 30,
            ),
            listOf(MedicineTimeEntity("med-rt", "08:00")),
        )
        database.medicineDao().upsertDoseRecord(
            DoseRecordEntity(
                medicineId = "med-rt", day = "2026-07-02", time = "08:00", accountId = "acc-rt",
                status = "TAKEN", takenAt = "2026-07-02T11:04:00Z", updatedAt = "2026-07-02T11:04:00Z",
                stockDeducted = 1,
            ),
        )
        database.waterDao().upsertGoal(WaterGoalEntity(accountId = "acc-rt", validFrom = "2026-07-01", goalMl = 2000))
        database.waterDao().upsertIntake(
            WaterIntakeEntity(accountId = "acc-rt", day = "2026-07-02", totalMl = 1400, updatedAt = "2026-07-02T18:00:00Z"),
        )
        database.waterDao().upsertReminderSettings(
            WaterReminderSettingsEntity(accountId = "acc-rt", enabled = true, intervalHours = 2, windowStart = "08:00", windowEnd = "22:00"),
        )

        val exported = BackupExporter(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
        ).export("acc-rt")
        val backup = BackupReader.read(JSONObject(exported.toString()))

        assertEquals(2, backup.version)
        assertEquals(
            BackupMedicine(
                id = "med-rt", name = "Vitamina D", amount = 1, unit = "CAPSULE",
                times = listOf(LocalTime.of(8, 0)),
                repeat = BackupRepeat("DAILY", emptyList(), LocalDate.of(2026, 7, 1), null),
                status = "ACTIVE",
                stock = BackupStock(amount = 10, capacity = 30, threshold = 5),
                alarmDelayMinutes = 30,
                previousVersions = emptyList(),
                archivedPeriods = emptyList(),
            ),
            backup.medicines.single(),
        )
        assertEquals(
            BackupDose("med-rt", LocalDate.of(2026, 7, 2), LocalTime.of(8, 0), "TAKEN", Instant.parse("2026-07-02T11:04:00Z"), 1),
            backup.doses.single(),
        )
        assertEquals(
            BackupWater(
                goals = listOf(BackupWaterGoal(LocalDate.of(2026, 7, 1), 2000)),
                intakes = listOf(BackupWaterIntake(LocalDate.of(2026, 7, 2), 1400)),
                reminder = BackupWaterReminder(true, 2, LocalTime.of(8, 0), LocalTime.of(22, 0)),
            ),
            backup.water,
        )
    }
}
