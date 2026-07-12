package dev.guilhermeluan.planner.diagnostics

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

interface DiagnosticLogger {
    fun log(event: String, subjectId: String? = null, details: Map<String, String> = emptyMap())
}

object NoOpDiagnosticLogger : DiagnosticLogger {
    override fun log(event: String, subjectId: String?, details: Map<String, String>) = Unit
}

class FileDiagnosticLogger(
    directory: File,
    private val clock: Clock,
    private val secret: String,
    private val maxBytes: Int = 1_000_000,
    private val maxAge: Duration = Duration.ofDays(7),
) : DiagnosticLogger {
    private val file = File(directory, "diagnostic-events.jsonl")

    @Synchronized
    override fun log(event: String, subjectId: String?, details: Map<String, String>) {
        runCatching {
            file.parentFile?.mkdirs()
            val json = JSONObject()
                .put("version", 1)
                .put("timestamp", clock.instant().toString())
                .put("event", event)
            subjectId?.let { json.put("correlationId", correlate(it)) }
            json.put("details", JSONObject(details))
            file.appendText(json.toString() + "\n", Charsets.UTF_8)
            retain()
        }
    }

    @Synchronized
    fun readEvents(): String = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")

    private fun correlate(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$secret:$value".toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun retain() {
        val cutoff = clock.instant().minus(maxAge)
        val fresh = file.readLines().filter { line ->
            runCatching { Instant.parse(JSONObject(line).getString("timestamp")) >= cutoff }.getOrDefault(false)
        }
        val kept = ArrayDeque<String>()
        var bytes = 0
        for (line in fresh.asReversed()) {
            val lineBytes = line.toByteArray().size + 1
            if (bytes + lineBytes > maxBytes) break
            kept.addFirst(line)
            bytes += lineBytes
        }
        file.writeText(kept.joinToString(separator = "\n", postfix = if (kept.isEmpty()) "" else "\n"))
    }
}
