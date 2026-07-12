package dev.guilhermeluan.planner.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigurationTest {
    @Test
    fun `secure server can be configured without a transport warning`() {
        val configuration = ServerConfiguration.parse("  https://planner.example.com/  ")

        assertEquals("https://planner.example.com", configuration.baseUrl)
        assertFalse(configuration.requiresInsecureTransportConfirmation)
    }

    @Test
    fun `local HTTP server requires explicit transport confirmation`() {
        val configuration = ServerConfiguration.parse("http://192.168.1.20:8080")

        assertTrue(configuration.requiresInsecureTransportConfirmation)
    }

    @Test
    fun `malformed transport URL is rejected before persisting`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ServerConfiguration.parse("https://")
        }
    }
}
