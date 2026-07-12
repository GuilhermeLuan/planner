package dev.guilhermeluan.planner.diagnostics

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun interface DiagnosticSnapshotProvider {
    fun snapshot(accountTimezone: String): JSONObject
}

class DiagnosticExporter(
    private val logger: FileDiagnosticLogger,
    private val snapshotProvider: DiagnosticSnapshotProvider,
    private val clock: Clock,
) {
    fun export(accountTimezone: String): ByteArray {
        val files = linkedMapOf(
            "events.jsonl" to logger.readEvents(),
            "diagnostics.json" to snapshotProvider.snapshot(accountTimezone).toString(2),
            "README.txt" to README,
        )
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = clock.millis() })
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
    }

    private companion object {
        const val README = """Diagnóstico do Planner

Este pacote contém somente informações técnicas. Não contém nomes, títulos de Tarefas ou Rotinas, backup ou banco de dados.

Em dispositivos Samsung, verifique também: Não perturbe, categoria Lembretes do Planner e Suspensão profunda.
"""
    }
}
