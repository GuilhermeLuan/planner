package dev.guilhermeluan.planner.backup

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

@RunWith(RobolectricTestRunner::class)
class BackupReaderTest {
    @Test
    fun `v1 backup reads account planner and export instant`() {
        val backup = BackupReader.read(fixture("backup-v1.json"))

        assertEquals(1, backup.version)
        assertEquals(Instant.parse("2026-07-14T11:05:30.123456Z"), backup.exportedAt)
        assertEquals(BackupAccount(id = "acc-1", name = "Ana", timezone = "America/Sao_Paulo"), backup.account)
        assertEquals("plan-1", backup.plannerId)
    }

    @Test
    fun `v1 backup reads tasks including one without time`() {
        val tasks = BackupReader.read(fixture("backup-v1.json")).tasks

        assertEquals(
            listOf(
                BackupTask(
                    id = "task-1",
                    title = "Comprar pão",
                    day = LocalDate.of(2026, 7, 12),
                    time = LocalTime.of(8, 0),
                    status = "PENDING",
                    archived = false,
                ),
                BackupTask(
                    id = "task-2",
                    title = "Enviar relatório",
                    day = LocalDate.of(2026, 7, 13),
                    time = null,
                    status = "DONE",
                    archived = true,
                ),
            ),
            tasks,
        )
    }

    @Test
    fun `v1 backup reads routines and occurrences`() {
        val backup = BackupReader.read(fixture("backup-v1.json"))

        assertEquals(
            listOf(
                BackupRoutine(
                    id = "rot-1",
                    title = "Meditar",
                    weekdays = listOf(1, 3, 5),
                    startDate = LocalDate.of(2026, 1, 1),
                    time = LocalTime.of(7, 0),
                    status = "ACTIVE",
                ),
                BackupRoutine(
                    id = "rot-2",
                    title = "Ler",
                    weekdays = listOf(2, 4),
                    startDate = LocalDate.of(2026, 2, 1),
                    time = null,
                    status = "ARCHIVED",
                ),
            ),
            backup.routines,
        )
        assertEquals(
            listOf(
                BackupOccurrence(
                    id = "rot-1:2026-07-13",
                    routineId = "rot-1",
                    title = "Meditar",
                    day = LocalDate.of(2026, 7, 13),
                    time = LocalTime.of(7, 0),
                    status = "DONE",
                ),
            ),
            backup.occurrences,
        )
    }

    @Test
    fun `v1 backup has no medicines doses or water`() {
        val backup = BackupReader.read(fixture("backup-v1.json"))

        assertEquals(emptyList<BackupMedicine>(), backup.medicines)
        assertEquals(emptyList<BackupDose>(), backup.doses)
        assertEquals(BackupWater(emptyList(), emptyList(), null), backup.water)
    }

    @Test
    fun `v2 backup reads medicine with stock and alarm`() {
        val medicine = BackupReader.read(fixture("backup-v2.json")).medicines[0]

        assertEquals(
            BackupMedicine(
                id = "med-1",
                name = "Vitamina C",
                amount = 1,
                unit = "TABLET",
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                repeat = BackupRepeat(
                    kind = "WEEKDAYS",
                    weekdays = listOf(1, 2, 3, 4, 5),
                    startDate = LocalDate.of(2026, 7, 1),
                    endDate = null,
                ),
                status = "ACTIVE",
                stock = BackupStock(amount = 20, capacity = null, threshold = 5),
                alarmDelayMinutes = 10,
                previousVersions = emptyList(),
                archivedPeriods = emptyList(),
            ),
            medicine,
        )
    }

    @Test
    fun `v2 backup reads medicine without stock or alarm with previous version and archived period`() {
        val medicine = BackupReader.read(fixture("backup-v2.json")).medicines[1]

        assertEquals(
            BackupMedicine(
                id = "med-2",
                name = "Antibiótico",
                amount = 2,
                unit = "CAPSULE",
                times = listOf(LocalTime.of(12, 0)),
                repeat = BackupRepeat(
                    kind = "PERIOD",
                    weekdays = emptyList(),
                    startDate = LocalDate.of(2026, 7, 5),
                    endDate = LocalDate.of(2026, 8, 4),
                ),
                status = "ACTIVE",
                stock = null,
                alarmDelayMinutes = null,
                previousVersions = listOf(
                    BackupMedicineVersion(
                        until = LocalDate.of(2026, 7, 4),
                        name = "Antibiótico (dose antiga)",
                        amount = 1,
                        unit = "TABLET",
                        times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        repeat = BackupRepeat(
                            kind = "DAILY",
                            weekdays = emptyList(),
                            startDate = LocalDate.of(2026, 6, 1),
                            endDate = null,
                        ),
                    ),
                ),
                archivedPeriods = listOf(
                    BackupArchivedPeriod(from = LocalDate.of(2026, 6, 1), until = null),
                ),
            ),
            medicine,
        )
    }

    @Test
    fun `v2 backup reads taken and skipped doses`() {
        val doses = BackupReader.read(fixture("backup-v2.json")).doses

        assertEquals(
            listOf(
                BackupDose(
                    medicineId = "med-1",
                    day = LocalDate.of(2026, 7, 13),
                    time = LocalTime.of(8, 0),
                    status = "TAKEN",
                    takenAt = Instant.parse("2026-07-13T11:02:15Z"),
                    stockDeducted = 1,
                ),
                BackupDose(
                    medicineId = "med-1",
                    day = LocalDate.of(2026, 7, 13),
                    time = LocalTime.of(20, 0),
                    status = "SKIPPED",
                    takenAt = null,
                    stockDeducted = 0,
                ),
            ),
            doses,
        )
    }

    @Test
    fun `v2 backup reads water goals intakes and reminder`() {
        val water = BackupReader.read(fixture("backup-v2.json")).water

        assertEquals(
            BackupWater(
                goals = listOf(
                    BackupWaterGoal(validFrom = LocalDate.of(2026, 7, 1), goalMl = 2000),
                    BackupWaterGoal(validFrom = LocalDate.of(2026, 7, 15), goalMl = 2500),
                ),
                intakes = listOf(
                    BackupWaterIntake(day = LocalDate.of(2026, 7, 13), totalMl = 1500),
                    BackupWaterIntake(day = LocalDate.of(2026, 7, 14), totalMl = 750),
                ),
                reminder = BackupWaterReminder(
                    enabled = true,
                    intervalHours = 2,
                    windowStart = LocalTime.of(8, 0),
                    windowEnd = LocalTime.of(22, 0),
                ),
            ),
            water,
        )
    }

    @Test
    fun `unsupported backup version is rejected`() {
        val json = fixture("backup-v1.json").put("versao", 3)

        val error = assertThrows(IllegalArgumentException::class.java) { BackupReader.read(json) }

        assertEquals("Versão de backup não suportada: 3", error.message)
    }

    @Test
    fun `v2 backup without water reminder reads reminder as null`() {
        val json = fixture("backup-v2.json")
        json.getJSONObject("agua").put("lembrete", JSONObject.NULL)

        val backup = BackupReader.read(json)

        assertNull(backup.water.reminder)
    }

    @Test
    fun `v2 backup reads stock without threshold as null`() {
        val json = fixture("backup-v2.json")
        json.getJSONArray("remedios").getJSONObject(0).getJSONObject("estoque")
            .put("capacidade", 30)
            .put("limiteAviso", JSONObject.NULL)

        val stock = BackupReader.read(json).medicines[0].stock

        assertEquals(BackupStock(amount = 20, capacity = 30, threshold = null), stock)
    }

    @Test
    fun `v2 backup without medicines fails with message naming the missing field`() {
        val json = fixture("backup-v2.json").apply { remove("remedios") }

        val error = assertThrows(IllegalArgumentException::class.java) { BackupReader.read(json) }

        assertEquals("Backup inválido: campo obrigatório ausente: remedios", error.message)
    }

    @Test
    fun `missing nested field is reported with its path`() {
        val json = fixture("backup-v2.json")
        json.getJSONArray("remedios").getJSONObject(0).getJSONObject("dose").remove("quantidade")

        val error = assertThrows(IllegalArgumentException::class.java) { BackupReader.read(json) }

        assertEquals("Backup inválido: campo obrigatório ausente: remedios[0].dose.quantidade", error.message)
    }

    @Test
    fun `invalid date is reported as invalid value with the original cause`() {
        val json = fixture("backup-v2.json")
        json.getJSONArray("doses").getJSONObject(0).put("dia", "amanhã")

        val error = assertThrows(IllegalArgumentException::class.java) { BackupReader.read(json) }

        assertEquals("Backup inválido: valor inválido em doses[0].dia", error.message)
        assertTrue(error.cause is DateTimeParseException)
    }

    @Test
    fun `invalid number is reported as invalid value with the original cause`() {
        val json = fixture("backup-v2.json")
        json.getJSONArray("remedios").getJSONObject(0).getJSONObject("dose").put("quantidade", "dois")

        val error = assertThrows(IllegalArgumentException::class.java) { BackupReader.read(json) }

        assertEquals("Backup inválido: valor inválido em remedios[0].dose.quantidade", error.message)
        assertTrue(error.cause is JSONException)
    }

    @Test
    fun `invalid element in an array is reported with its index`() {
        val json = fixture("backup-v2.json")
        json.getJSONArray("remedios").getJSONObject(0).getJSONObject("repeticao")
            .getJSONArray("diasDaSemana").put(1, "terça")

        val error = assertThrows(IllegalArgumentException::class.java) { BackupReader.read(json) }

        assertEquals("Backup inválido: valor inválido em remedios[0].repeticao.diasDaSemana[1]", error.message)
    }

    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/backup/$name")!!.readText())
}
