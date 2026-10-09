package dev.guilhermeluan.planner.widget

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.PlannedDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class QuickActionsTest {
    private val day = LocalDate.of(2026, 10, 8)

    private fun dose(name: String, time: String, status: DoseStatus = DoseStatus.PENDING) = PlannedDose(
        medicineId = name, name = name, amount = 1, unit = DoseUnit.TABLET,
        day = day, time = LocalTime.parse(time), status = status,
    )

    @Test
    fun `a proxima dose pendente e a mais cedo ainda pendente, mesmo fora de ordem`() {
        val doses = listOf(
            dose("C", "20:00"),
            dose("A", "08:00", DoseStatus.TAKEN),
            dose("B", "12:00"),
            dose("D", "07:00", DoseStatus.SKIPPED),
        )
        assertEquals("B", nextPendingDose(doses)?.name)
    }

    @Test
    fun `sem dose pendente nao ha proxima`() {
        assertNull(nextPendingDose(listOf(dose("A", "08:00", DoseStatus.TAKEN))))
        assertNull(nextPendingDose(emptyList()))
    }

    @Test
    fun `o total do dia usa litros com virgula`() {
        assertEquals("1,45 L hoje", QuickText.waterTotal(1450))
        assertEquals("2 L hoje", QuickText.waterTotal(2000))
        assertEquals("0,2 L hoje", QuickText.waterTotal(200))
    }

    @Test
    fun `a confirmacao de agua descreve o resultado`() {
        val c = QuickText.waterConfirmation(glassMl = 250, totalMl = 1450, until = Instant.EPOCH)
        assertEquals(QuickKind.Water, c.kind)
        assertEquals("+250 ml", c.title)
        assertEquals("1,45 L hoje", c.detail)
        assertEquals("Água registrada, 1,45 litro hoje", c.description)
    }

    @Test
    fun `a confirmacao de remedio mostra nome abreviado e horario da dose`() {
        val c = QuickText.doseConfirmation(dose("Vitamina D", "08:00"), until = Instant.EPOCH)
        assertEquals("Tomado", c.title)
        assertEquals("Vit. D · 08:00", c.detail)
        assertEquals("Remédio tomado, Vitamina D às 08:00", c.description)
    }

    @Test
    fun `nomes curtos nao sao abreviados e nomes longos sim`() {
        assertEquals("Dipirona", QuickText.shortName("Dipirona"))
        assertEquals("Vit. D", QuickText.shortName("Vitamina D"))
        assertEquals("Paracet.", QuickText.shortName("Paracetamol"))
        assertEquals("Áci. fólico", QuickText.shortName("Ácido fólico"))
    }

    @Test
    fun `a confirmacao vale ate o instante limite, exclusive`() {
        val until = Instant.parse("2026-10-08T12:00:03Z")
        val c = QuickText.waterConfirmation(250, 250, until)
        assertTrue(c.isActive(until.minusMillis(1)))
        assertFalse(c.isActive(until))
        assertFalse(c.isActive(until.plusSeconds(1)))
    }

    @Test
    fun `toque repetido reinicia a janela de 3 segundos`() {
        val now = Instant.parse("2026-10-08T12:00:00Z")
        assertEquals(now.plusSeconds(3), confirmationUntil(now))
    }

    @Test
    fun `litros no texto de acessibilidade usam singular so para 1`() {
        assertEquals("1 litro", QuickText.litersSpoken(1000))
        assertEquals("1,45 litro", QuickText.litersSpoken(1450))
        assertEquals("2,5 litros", QuickText.litersSpoken(2500))
        assertEquals("200 ml", QuickText.litersSpoken(200))
    }
}
