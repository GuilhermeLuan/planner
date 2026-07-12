package dev.guilhermeluan.planner.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FileDiagnosticLoggerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `events remain ordered and correlate without exposing the real id`() {
        val logger = FileDiagnosticLogger(
            directory = temporaryFolder.root,
            clock = Clock.fixed(Instant.parse("2026-07-12T20:00:00Z"), ZoneOffset.UTC),
            secret = "installation-secret",
        )

        logger.log("reminder_schedule_requested", "private-task-id", mapOf("method" to "pending"))
        logger.log("reminder_scheduled_inexact", "private-task-id", mapOf("method" to "inexact"))

        val events = logger.readEvents().lines().filter(String::isNotBlank)
        assertEquals(2, events.size)
        assertEquals("reminder_schedule_requested", org.json.JSONObject(events[0]).getString("event"))
        assertEquals(
            org.json.JSONObject(events[0]).getString("correlationId"),
            org.json.JSONObject(events[1]).getString("correlationId"),
        )
        assertFalse(logger.readEvents().contains("private-task-id"))
    }

    @Test
    fun `retention removes old events and keeps the log within its byte limit`() {
        FileDiagnosticLogger(
            temporaryFolder.root,
            Clock.fixed(Instant.parse("2026-07-01T20:00:00Z"), ZoneOffset.UTC),
            "secret",
            maxBytes = 420,
        ).log("old_event", details = mapOf("payload" to "old"))
        val current = FileDiagnosticLogger(
            temporaryFolder.root,
            Clock.fixed(Instant.parse("2026-07-12T20:00:00Z"), ZoneOffset.UTC),
            "secret",
            maxBytes = 420,
        )

        repeat(10) { current.log("new_event_$it", details = mapOf("payload" to "x".repeat(40))) }

        val exported = current.readEvents()
        assertFalse(exported.contains("old_event"))
        assertFalse(exported.toByteArray().size > 420)
        assertEquals(true, exported.contains("new_event_9"))
    }
}
