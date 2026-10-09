package dev.guilhermeluan.planner.backup

import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object BackupReader {
    /**
     * Lê um backup exportado; aceita as versões 1 e 2.
     *
     * Lança [IllegalArgumentException], com mensagem em português, se a versão não for suportada,
     * se um campo obrigatório estiver ausente ou se um valor tiver tipo ou formato inválido.
     */
    fun read(json: JSONObject): Backup {
        val version = json.opt("versao")
        if (version !is Int || version !in 1..2) {
            throw IllegalArgumentException("Versão de backup não suportada: ${version ?: "ausente"}")
        }
        val root = JsonNode(json, "")
        val isV2 = version == 2
        return Backup(
            version = version,
            exportedAt = root.instant("exportadoEm"),
            account = readAccount(root.obj("conta")),
            plannerId = root.obj("planner").string("id"),
            tasks = root.objects("tarefas") { readTask(it) },
            routines = root.objects("rotinas") { readRoutine(it) },
            occurrences = root.objects("ocorrencias") { readOccurrence(it) },
            medicines = if (isV2) root.objects("remedios") { readMedicine(it) } else emptyList(),
            doses = if (isV2) root.objects("doses") { readDose(it) } else emptyList(),
            water = if (isV2) readWater(root.obj("agua")) else BackupWater(emptyList(), emptyList(), null),
        )
    }

    /** Lê o texto de um arquivo de backup; texto que não é JSON também vira IllegalArgumentException. */
    fun read(text: String): Backup {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Backup inválido: o arquivo não é um JSON válido", e)
        }
        return read(json)
    }

    private fun readAccount(node: JsonNode) = BackupAccount(
        id = node.string("id"),
        name = node.string("nome"),
        timezone = node.zone("fuso"),
    )

    private fun readTask(node: JsonNode) = BackupTask(
        id = node.string("id"),
        title = node.string("titulo"),
        day = node.date("dia"),
        time = node.optionalTime("horario"),
        status = node.string("status"),
        archived = node.boolean("arquivada"),
    )

    private fun readRoutine(node: JsonNode) = BackupRoutine(
        id = node.string("id"),
        title = node.string("titulo"),
        weekdays = node.ints("diasDaSemana"),
        startDate = node.date("dataInicio"),
        time = node.optionalTime("horario"),
        status = node.string("status"),
    )

    private fun readOccurrence(node: JsonNode) = BackupOccurrence(
        id = node.string("id"),
        routineId = node.string("rotinaId"),
        title = node.string("titulo"),
        day = node.date("dia"),
        time = node.optionalTime("horario"),
        status = node.string("status"),
    )

    private fun readMedicine(node: JsonNode): BackupMedicine {
        val fields = readMedicineFields(node)
        return BackupMedicine(
            id = node.string("id"),
            name = fields.name,
            amount = fields.amount,
            unit = fields.unit,
            times = fields.times,
            repeat = fields.repeat,
            status = node.string("status"),
            stock = node.optionalObj("estoque")?.let { readStock(it) },
            alarmDelayMinutes = node.optionalObj("alarme")?.int("atrasoMinutos"),
            previousVersions = node.objects("versoesAnteriores") { readMedicineVersion(it) },
            archivedPeriods = node.objects("periodosArquivados") { readArchivedPeriod(it) },
        )
    }

    private fun readMedicineVersion(node: JsonNode): BackupMedicineVersion {
        val fields = readMedicineFields(node)
        return BackupMedicineVersion(
            until = node.date("ate"),
            name = fields.name,
            amount = fields.amount,
            unit = fields.unit,
            times = fields.times,
            repeat = fields.repeat,
        )
    }

    /** Campos comuns ao remédio e às versões anteriores, que têm o mesmo formato no JSON. */
    private data class MedicineFields(
        val name: String,
        val amount: Int,
        val unit: String,
        val times: List<LocalTime>,
        val repeat: BackupRepeat,
    )

    private fun readMedicineFields(node: JsonNode): MedicineFields {
        val dose = node.obj("dose")
        return MedicineFields(
            name = node.string("nome"),
            amount = dose.int("quantidade"),
            unit = dose.string("unidade"),
            times = node.times("horarios"),
            repeat = readRepeat(node.obj("repeticao")),
        )
    }

    private fun readRepeat(node: JsonNode) = BackupRepeat(
        kind = node.string("tipo"),
        weekdays = node.ints("diasDaSemana"),
        startDate = node.date("dataInicio"),
        endDate = node.optionalDate("dataFim"),
    )

    private fun readStock(node: JsonNode) = BackupStock(
        amount = node.int("quantidade"),
        capacity = node.optionalInt("capacidade"),
        threshold = node.optionalInt("limiteAviso"),
    )

    private fun readArchivedPeriod(node: JsonNode) = BackupArchivedPeriod(
        from = node.date("de"),
        until = node.optionalDate("ate"),
    )

    private fun readDose(node: JsonNode) = BackupDose(
        medicineId = node.string("remedioId"),
        day = node.date("dia"),
        time = node.time("horario"),
        status = node.string("status"),
        takenAt = node.optionalInstant("tomadaEm"),
        stockDeducted = node.int("estoqueDescontado"),
    )

    private fun readWater(node: JsonNode) = BackupWater(
        goals = node.objects("metas") { readWaterGoal(it) },
        intakes = node.objects("consumo") { readWaterIntake(it) },
        reminder = node.optionalObj("lembrete")?.let { readWaterReminder(it) },
    )

    private fun readWaterGoal(node: JsonNode) = BackupWaterGoal(
        validFrom = node.date("validaDesde"),
        goalMl = node.int("metaMl"),
    )

    private fun readWaterIntake(node: JsonNode) = BackupWaterIntake(
        day = node.date("dia"),
        totalMl = node.int("totalMl"),
    )

    private fun readWaterReminder(node: JsonNode) = BackupWaterReminder(
        enabled = node.boolean("ativo"),
        intervalHours = node.int("intervaloHoras"),
        windowStart = node.time("inicio"),
        windowEnd = node.time("fim"),
    )
}

data class Backup(
    val version: Int,
    val exportedAt: Instant,
    val account: BackupAccount,
    val plannerId: String,
    val tasks: List<BackupTask>,
    val routines: List<BackupRoutine>,
    val occurrences: List<BackupOccurrence>,
    val medicines: List<BackupMedicine>,
    val doses: List<BackupDose>,
    val water: BackupWater,
)

data class BackupAccount(val id: String, val name: String, val timezone: String)
data class BackupTask(val id: String, val title: String, val day: LocalDate, val time: LocalTime?, val status: String, val archived: Boolean)
data class BackupRoutine(val id: String, val title: String, val weekdays: List<Int>, val startDate: LocalDate, val time: LocalTime?, val status: String)
data class BackupOccurrence(val id: String, val routineId: String, val title: String, val day: LocalDate, val time: LocalTime?, val status: String)
data class BackupMedicine(
    val id: String,
    val name: String,
    val amount: Int,
    val unit: String,
    val times: List<LocalTime>,
    val repeat: BackupRepeat,
    val status: String,
    val stock: BackupStock?,
    val alarmDelayMinutes: Int?,
    val previousVersions: List<BackupMedicineVersion>,
    val archivedPeriods: List<BackupArchivedPeriod>,
)
data class BackupRepeat(val kind: String, val weekdays: List<Int>, val startDate: LocalDate, val endDate: LocalDate?)
data class BackupStock(val amount: Int, val capacity: Int?, val threshold: Int?)
data class BackupMedicineVersion(val until: LocalDate, val name: String, val amount: Int, val unit: String, val times: List<LocalTime>, val repeat: BackupRepeat)
data class BackupArchivedPeriod(val from: LocalDate, val until: LocalDate?)
data class BackupDose(val medicineId: String, val day: LocalDate, val time: LocalTime, val status: String, val takenAt: Instant?, val stockDeducted: Int)
data class BackupWater(val goals: List<BackupWaterGoal>, val intakes: List<BackupWaterIntake>, val reminder: BackupWaterReminder?)
data class BackupWaterGoal(val validFrom: LocalDate, val goalMl: Int)
data class BackupWaterIntake(val day: LocalDate, val totalMl: Int)
data class BackupWaterReminder(val enabled: Boolean, val intervalHours: Int, val windowStart: LocalTime, val windowEnd: LocalTime)

/** Objeto do backup com o caminho até ele (ex.: remedios[0].dose), usado nas mensagens de erro. */
private class JsonNode(private val json: JSONObject, private val path: String) {
    fun string(name: String): String = required(name) { getString(it) }
    fun int(name: String): Int = required(name) { getInt(it) }
    fun boolean(name: String): Boolean = required(name) { getBoolean(it) }
    fun date(name: String): LocalDate = required(name) { LocalDate.parse(getString(it)) }
    fun time(name: String): LocalTime = required(name) { LocalTime.parse(getString(it)) }
    fun instant(name: String): Instant = required(name) { Instant.parse(getString(it)) }
    fun zone(name: String): String = required(name) { getString(it).also { id -> ZoneId.of(id) } }

    fun optionalInt(name: String): Int? = optional(name) { getInt(it) }
    fun optionalDate(name: String): LocalDate? = optional(name) { LocalDate.parse(getString(it)) }
    fun optionalTime(name: String): LocalTime? = optional(name) { LocalTime.parse(getString(it)) }
    fun optionalInstant(name: String): Instant? = optional(name) { Instant.parse(getString(it)) }
    fun optionalObj(name: String): JsonNode? = optional(name) { getJSONObject(it) }?.let { JsonNode(it, childPath(name)) }

    fun obj(name: String): JsonNode = JsonNode(required(name) { getJSONObject(it) }, childPath(name))

    fun <T> objects(name: String, transform: (JsonNode) -> T): List<T> {
        val array = required(name) { getJSONArray(it) }
        return List(array.length()) { index ->
            val elementPath = "${childPath(name)}[$index]"
            transform(JsonNode(readAt(elementPath) { array.getJSONObject(index) }, elementPath))
        }
    }

    fun ints(name: String): List<Int> {
        val array = required(name) { getJSONArray(it) }
        return List(array.length()) { index -> readAt("${childPath(name)}[$index]") { array.getInt(index) } }
    }

    fun times(name: String): List<LocalTime> {
        val array = required(name) { getJSONArray(it) }
        return List(array.length()) { index ->
            readAt("${childPath(name)}[$index]") { LocalTime.parse(array.getString(index)) }
        }
    }

    private fun childPath(name: String): String = if (path.isEmpty()) name else "$path.$name"

    /** Campo obrigatório: ausente ou null vira IllegalArgumentException. */
    private fun <T> required(name: String, read: JSONObject.(String) -> T): T {
        if (json.isNull(name)) {
            throw IllegalArgumentException("Backup inválido: campo obrigatório ausente: ${childPath(name)}")
        }
        return readAt(childPath(name)) { json.read(name) }
    }

    /** Campo opcional: ausente ou null vira null. */
    private fun <T> optional(name: String, read: JSONObject.(String) -> T): T? =
        if (json.isNull(name)) null else readAt(childPath(name)) { json.read(name) }

    /** Lê o valor em [valuePath] e traduz erros de JSON e de data para IllegalArgumentException. */
    private fun <T> readAt(valuePath: String, block: () -> T): T =
        try {
            block()
        } catch (e: JSONException) {
            throw IllegalArgumentException("Backup inválido: valor inválido em $valuePath", e)
        } catch (e: DateTimeException) {
            throw IllegalArgumentException("Backup inválido: valor inválido em $valuePath", e)
        }
}
