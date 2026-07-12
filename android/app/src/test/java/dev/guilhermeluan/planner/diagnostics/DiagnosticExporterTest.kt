package dev.guilhermeluan.planner.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipInputStream

class DiagnosticExporterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `export creates a private diagnostic zip that an external consumer can read`() {
        val clock = Clock.fixed(Instant.parse("2026-07-12T20:00:00Z"), ZoneOffset.UTC)
        val logger = FileDiagnosticLogger(temporaryFolder.root, clock, "secret")
        logger.log("reminder_scheduled_inexact", "private-id", mapOf("triggerAt" to "2026-07-12T20:05:00Z"))
        val exporter = DiagnosticExporter(logger, { timezone ->
            JSONObject().put("version", 1).put("accountTimezone", timezone)
        }, clock)

        val entries = unzip(exporter.export("America/Sao_Paulo"))

        assertEquals(setOf("events.jsonl", "diagnostics.json", "README.txt"), entries.keys)
        assertEquals("America/Sao_Paulo", JSONObject(entries.getValue("diagnostics.json")).getString("accountTimezone"))
        assertFalse(entries.values.joinToString().contains("private-id"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                entry = zip.nextEntry
            }
        }
    }
}
