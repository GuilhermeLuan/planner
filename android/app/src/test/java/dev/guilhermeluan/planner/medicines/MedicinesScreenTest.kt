package dev.guilhermeluan.planner.medicines

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.MedicineStatus
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.PlannerMedicine
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class MedicinesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 1)
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun medicine(id: String, name: String, vararg times: LocalTime, unit: DoseUnit = DoseUnit.CAPSULE) =
        PlannerMedicine(
            id, "account-1", "planner-1", name, 1, unit, times.toSet(), MedicineRepeat.Daily, today, MedicineStatus.ACTIVE,
        )

    private fun dose(
        medicineId: String,
        name: String,
        time: LocalTime,
        status: DoseStatus = DoseStatus.PENDING,
        takenAt: Instant? = null,
        amount: Int = 1,
        unit: DoseUnit = DoseUnit.CAPSULE,
        day: LocalDate = today,
    ) = PlannedDose(medicineId, name, amount, unit, day, time, status, takenAt)

    private val contraceptive = dose(
        "m1", "Anticoncepcional", LocalTime.of(8, 0), DoseStatus.TAKEN, Instant.parse("2026-10-01T11:04:00Z"),
        unit = DoseUnit.TABLET,
    )
    private val vitamin = dose("m2", "Vitamina D", LocalTime.of(13, 0))
    private val magnesium = dose("m3", "Magnésio", LocalTime.of(21, 30), amount = 2, unit = DoseUnit.TABLET)

    private val medicines = listOf(
        medicine("m1", "Anticoncepcional", LocalTime.of(8, 0)),
        medicine("m2", "Vitamina D", LocalTime.of(13, 0)),
        medicine("m3", "Magnésio", LocalTime.of(21, 30)),
    )

    private fun show(
        doses: List<PlannedDose> = listOf(contraceptive, vitamin, magnesium),
        medicines: List<PlannerMedicine> = this.medicines,
        selectedDay: LocalDate = today,
        clockToday: LocalDate = today,
        onCreateMedicine: (MedicineDraft) -> Unit = {},
        onSetDoseStatus: (PlannedDose, DoseStatus) -> Unit = { _, _ -> },
    ) = composeRule.setContent {
        PlannerTheme {
            MedicinesScreen(
                state = MedicinesUiState(selectedDay, doses, medicines),
                today = clockToday,
                zone = zone,
                onSetDoseStatus = onSetDoseStatus,
                onCreateMedicine = onCreateMedicine,
            )
        }
    }

    @Test
    fun titleCountsTakenDosesAndListIsInTimeOrderWithTakenInstant() {
        show()

        composeRule.onNodeWithText("1 de 3 doses tomadas hoje").assertIsDisplayed()
        composeRule.onNodeWithText("Tomado às 08:04").assertIsDisplayed()
        composeRule.onNodeWithText("2 comprimidos").assertIsDisplayed()
    }

    @Test
    fun nextPendingDoseIsHighlightedAndCanBeTakenWithoutSnoozeYet() {
        var change: Pair<PlannedDose, DoseStatus>? = null
        show { dose, status -> change = dose to status }

        composeRule.onNodeWithText("Próxima dose às 13:00").assertIsDisplayed()
        composeRule.onNodeWithText("Adiar").assertDoesNotExist()
        composeRule.onNodeWithText("Marcar como tomado").performClick()

        assertEquals(vitamin to DoseStatus.TAKEN, change)
    }

    @Test
    fun withoutMedicinesTheTabInvitesTheFirstOne() {
        show(doses = emptyList(), medicines = emptyList())

        composeRule.onNodeWithText("Nenhum remédio ainda").assertIsDisplayed()
        composeRule.onNodeWithText("Novo remédio", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun yourMedicinesListsEachWithItsTimes() {
        show()

        composeRule.onNodeWithText("1x ao dia · 13:00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theCircleTakesAPendingDoseAndUndoesATakenOne() {
        val changes = mutableListOf<Pair<PlannedDose, DoseStatus>>()
        show { dose, status -> changes += dose to status }

        composeRule.onNodeWithContentDescription("Marcar Magnésio das 21:30 como tomado").performClick()
        composeRule.onNodeWithContentDescription("Desmarcar Anticoncepcional das 08:00").performClick()

        assertEquals(listOf(magnesium to DoseStatus.TAKEN, contraceptive to DoseStatus.PENDING), changes)
    }

    @Test
    fun aDoseCanBeSkippedFromItsMenuAndASkippedDoseCanBeUndone() {
        val changes = mutableListOf<Pair<PlannedDose, DoseStatus>>()
        val skipped = vitamin.copy(status = DoseStatus.SKIPPED)
        show(doses = listOf(skipped, magnesium)) { dose, status -> changes += dose to status }

        composeRule.onNodeWithText("Pulada").assertIsDisplayed()
        composeRule.onNodeWithTag("dose-m3-21:30").performClick()
        composeRule.onNodeWithText("Pular dose").performClick()
        composeRule.onNodeWithTag("dose-m2-13:00").performClick()
        composeRule.onNodeWithText("Desfazer").performClick()

        assertEquals(listOf(magnesium to DoseStatus.SKIPPED, skipped to DoseStatus.PENDING), changes)
    }

    @Test
    fun whenEveryDoseIsTakenTheHighlightSaysSo() {
        show(doses = listOf(contraceptive))

        composeRule.onNodeWithText("Tudo certo por hoje").assertIsDisplayed()
        composeRule.onNodeWithText("Marcar como tomado").assertDoesNotExist()
        composeRule.onNodeWithText("1 de 1 dose tomada hoje").assertIsDisplayed()
    }

    @Test
    fun anotherSelectedDayShowsItsOwnDosesAndWording() {
        val tomorrow = today.plusDays(1)
        show(doses = listOf(dose("m2", "Vitamina D", LocalTime.of(13, 0), day = tomorrow)), selectedDay = tomorrow)

        composeRule.onNodeWithText("0 de 1 dose tomada neste dia").assertIsDisplayed()
        composeRule.onNodeWithText("Doses de este dia").assertIsDisplayed()
    }

    @Test
    fun newMedicineButtonOpensTheFormAndHandsBackTheDraft() {
        var created: MedicineDraft? = null
        show(doses = emptyList(), medicines = emptyList(), onCreateMedicine = { created = it })

        composeRule.onNodeWithTag("new-medicine-fab").performClick()
        composeRule.onNodeWithTag("medicine-name").performTextInput("Vitamina D")
        composeRule.onNodeWithText("Salvar remédio").performScrollTo().performClick()

        assertEquals("Vitamina D", created?.name)
        composeRule.onNodeWithTag("medicine-name").assertDoesNotExist()
    }

    @Test
    fun wordingFollowsTheCurrentDayWhenMidnightPassesWhileTheScreenIsOpen() {
        val yesterdaysDose = dose("m2", "Vitamina D", LocalTime.of(13, 0), day = today)
        show(doses = listOf(yesterdaysDose), selectedDay = today, clockToday = today.plusDays(1))

        composeRule.onNodeWithText("0 de 1 dose tomada neste dia").assertIsDisplayed()
    }

    @Test
    fun yourMedicinesShowsWeekdaysAndTheFullEndDateOfAPeriod() {
        val weekly = medicine("m4", "Ferro", LocalTime.of(7, 0))
            .copy(repeat = MedicineRepeat.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        val course = medicine("m5", "Antibiótico", LocalTime.of(9, 0), LocalTime.of(21, 0))
            .copy(repeat = MedicineRepeat.Period(today, LocalDate.of(2026, 10, 8)))
        show(doses = emptyList(), medicines = listOf(weekly, course))

        composeRule.onNodeWithText("1x ao dia · 07:00 · seg, qui").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2x ao dia · 09:00, 21:00 · até 08/10/2026").performScrollTo().assertIsDisplayed()
    }
}
