package dev.guilhermeluan.planner.sync

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class HttpSyncApiTest {
    private lateinit var server: HttpServer

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `push and pull follow the versioned incremental contract`() = runTest {
        server.createContext("/api/v1/sync/push") { exchange ->
            assertEquals("Bearer token-1", exchange.requestHeaders.getFirst("Authorization"))
            val request = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
            assertEquals("operation-1", request.getJSONArray("operations").getJSONObject(0).getString("operation_id"))
            val response = """{"results":[{"operation_id":"operation-1","status":"accepted","version":4}]}"""
                .toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/api/v1/sync/pull") { exchange ->
            assertEquals("cursor=3&limit=2", exchange.requestURI.rawQuery)
            val response = """
                {
                  "next_cursor":4,
                  "has_more":false,
                  "changes":[{
                    "cursor":4,
                    "operation_id":"operation-1",
                    "entity_type":"task",
                    "entity_id":"task-1",
                    "kind":"upsert",
                    "payload":{"title":"Enviar documentos"},
                    "version":4,
                    "updated_at":"2026-07-11T12:00:01Z"
                  }]
                }
            """.trimIndent().toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        val api = HttpSyncApi("http://127.0.0.1:${server.address.port}")
        val operation = SyncOperation(
            operationId = "operation-1",
            entityType = "task",
            entityId = "task-1",
            kind = "upsert",
            payloadJson = """{"title":"Enviar documentos"}""",
            clientUpdatedAt = "2026-07-11T12:00:00Z",
        )

        val pushed = api.push("token-1", listOf(operation))
        val pulled = api.pull("token-1", cursor = 3, limit = 2)

        assertEquals(listOf(PushResult("operation-1", PushResultStatus.ACCEPTED, 4)), pushed)
        assertEquals(4, pulled.nextCursor)
        assertEquals("task-1", pulled.changes.single().entityId)
        assertEquals("""{"title":"Enviar documentos"}""", pulled.changes.single().payloadJson)
    }
}
