package dev.guilhermeluan.planner.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class QuickConfirmationStoreTest {
    private val store = QuickConfirmationStore(ApplicationProvider.getApplicationContext<Context>())
    private val tap = Instant.parse("2026-10-08T12:00:00Z")

    @Test
    fun `a confirmacao salva vale por 3 segundos e depois some`() {
        val c = QuickText.waterConfirmation(200, 1400, confirmationUntil(tap))
        store.save(c)
        assertEquals(c, store.active(QuickKind.Water, tap.plusSeconds(2)))
        assertNull(store.active(QuickKind.Water, tap.plusSeconds(3)))
    }

    @Test
    fun `cada botao tem a sua confirmacao`() {
        store.save(QuickText.waterConfirmation(200, 1400, confirmationUntil(tap)))
        assertNull(store.active(QuickKind.Medicine, tap))
    }

    @Test
    fun `novo toque substitui e estende a janela`() {
        store.save(QuickText.waterConfirmation(200, 1400, confirmationUntil(tap)))
        val later = tap.plusSeconds(2)
        store.save(QuickText.waterConfirmation(200, 1600, confirmationUntil(later)))
        assertEquals("1,6 L hoje", store.active(QuickKind.Water, tap.plusSeconds(4))?.detail)
    }
}
