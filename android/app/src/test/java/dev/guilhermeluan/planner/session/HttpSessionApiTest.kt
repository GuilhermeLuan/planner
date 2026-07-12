package dev.guilhermeluan.planner.session

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

class HttpSessionApiTest {
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
    fun `login follows the versioned bootstrap contract`() = runTest {
        server.createContext("/api/v1/auth/login") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            assertEquals("application/json", exchange.requestHeaders.getFirst("Content-Type"))
            val response = """
                {
                  "token": "token-1",
                  "account": {
                    "id": "account-1",
                    "username": "gui",
                    "role": "member",
                    "status": "active",
                    "timezone": "America/Sao_Paulo",
                    "must_change_password": false
                  },
                  "planner": {"id": "planner-1", "account_id": "account-1"}
                }
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        val payload = HttpSessionApi(baseUrl).login("gui", "correct-password")

        assertEquals(
            LoginPayload(
                token = "token-1",
                account = Account("account-1", "gui", "America/Sao_Paulo", false),
                planner = Planner("planner-1", "account-1"),
            ),
            payload,
        )
    }
}
