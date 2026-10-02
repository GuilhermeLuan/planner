package dev.guilhermeluan.planner.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlannerPaletteTest {
    @Test
    fun lightPaletteFollowsTheSpec() {
        val p = PlannerPalette.Light
        assertEquals(Color(0xFFFFF7FA), p.porcelain)
        assertEquals(Color(0xFFFFE3EE), p.blush)
        assertEquals(Color(0xFFA83262), p.raspberry)
        assertEquals(Color(0xFF2B1821), p.wine)
        assertEquals(Color(0xFF3E1B30), p.nightPlum)
        assertEquals(Color(0xFF5868B0), p.waterLavender)
        assertEquals(Color(0xFFFDE6DA), p.peach)
        assertEquals(Color(0xFFC2410C), p.alertAmber)
    }

    @Test
    fun darkPaletteKeepsTheExistingNightTokens() {
        val p = PlannerPalette.Dark
        assertEquals(Color(0xFF1B1116), p.porcelain)
        assertEquals(Color(0xFF35212A), p.blush)
        assertEquals(Color(0xFFFF8FBA), p.raspberry)
        assertEquals(Color(0xFFFFE8F0), p.wine)
        assertNotEquals(PlannerPalette.Light.porcelain, p.porcelain)
    }
}
