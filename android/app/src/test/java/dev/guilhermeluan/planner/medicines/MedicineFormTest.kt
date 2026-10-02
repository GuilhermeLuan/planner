package dev.guilhermeluan.planner.medicines

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1400dp")
class MedicineFormTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val thursday = LocalDate.of(2026, 10, 1)

    private fun show(onSave: (MedicineDraft) -> Unit = {}) = composeRule.setContent {
        PlannerTheme { MedicineForm(initialDay = thursday, onSave = onSave) }
    }

    private fun save() = composeRule.onNodeWithText("Salvar remédio").performScrollTo().performClick()

    @Test
    fun saveStaysDisabledUntilTheMedicineHasAName() {
        show()

        composeRule.onNodeWithText("Salvar remédio").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun savesWithDefaultDoseTimeAndDailyRepeat() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("  Vitamina D ")
        save()

        assertEquals(
            MedicineDraft(
                "Vitamina D", 1, DoseUnit.TABLET, setOf(LocalTime.of(8, 0)), MedicineRepeat.Daily, thursday,
            ),
            saved,
        )
    }

    @Test
    fun doseAmountAndUnitCanBeChosen() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Magnésio")
        composeRule.onNodeWithContentDescription("Aumentar quantidade").performClick()
        composeRule.onNodeWithContentDescription("Aumentar quantidade").performClick()
        composeRule.onNodeWithContentDescription("Diminuir quantidade").performClick()
        composeRule.onNodeWithText("cápsula").performClick()
        save()

        assertEquals(2, saved?.amount)
        assertEquals(DoseUnit.CAPSULE, saved?.unit)
    }

    @Test
    fun timesCanBeAddedThroughThePickerAndRemovedByTappingTheirChip() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Magnésio")
        composeRule.onNodeWithText("+ Adicionar horário").performScrollTo().performClick()
        composeRule.onNodeWithText("Usar horário").performClick()
        save()
        assertEquals(setOf(LocalTime.of(8, 0), LocalTime.of(9, 0)), saved?.times)

        composeRule.onNodeWithText("08:00").performScrollTo().performClick()
        save()
        assertEquals(setOf(LocalTime.of(9, 0)), saved?.times)
    }

    @Test
    fun theLastTimeCannotBeRemoved() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Magnésio")
        composeRule.onNodeWithText("08:00").performScrollTo().performClick()
        save()

        assertEquals(setOf(LocalTime.of(8, 0)), saved?.times)
    }

    @Test
    fun weekdayRepeatStartsOnTheSelectedDayAndNeedsAtLeastOneWeekday() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Anticoncepcional")
        composeRule.onNodeWithText("Dias da semana").performScrollTo().performClick()
        composeRule.onNodeWithText("Seg").performScrollTo().performClick()
        save()
        assertEquals(MedicineRepeat.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), saved?.repeat)

        composeRule.onNodeWithText("Seg").performScrollTo().performClick()
        composeRule.onNodeWithText("Qui").performScrollTo().performClick()
        composeRule.onNodeWithText("Salvar remédio").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun periodRepeatDefaultsToAWeekFromTheSelectedDay() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Antibiótico")
        composeRule.onNodeWithText("Por um período").performScrollTo().performClick()
        composeRule.onNodeWithTag("medicine-period-start").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("medicine-period-end").performScrollTo().assertIsDisplayed()
        save()

        assertEquals(MedicineRepeat.Period(thursday, thursday.plusDays(6)), saved?.repeat)
    }
}
