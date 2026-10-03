package dev.guilhermeluan.planner.medicines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextReplacement
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
import dev.guilhermeluan.planner.tasks.MedicineStatus
import dev.guilhermeluan.planner.tasks.MedicineStock
import dev.guilhermeluan.planner.tasks.PlannerMedicine
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

    @Test
    fun stockAndWarningThresholdAreOptional() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Vitamina D")
        save()

        assertEquals(null, saved?.stock)
        assertEquals(null, saved?.stockThreshold)
    }

    @Test
    fun stockAndThresholdAreSavedWithTheMedicine() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Vitamina D")
        composeRule.onNodeWithTag("medicine-stock").performScrollTo().performTextInput("60")
        composeRule.onNodeWithTag("medicine-stock-threshold").performScrollTo().performTextInput("5")
        save()

        assertEquals(60, saved?.stock)
        assertEquals(5, saved?.stockThreshold)
    }

    @Test
    fun nonDigitsAreIgnoredInTheStockFields() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Vitamina D")
        composeRule.onNodeWithTag("medicine-stock").performScrollTo().performTextInput("6a0")
        save()

        assertEquals(60, saved?.stock)
    }

    @Test
    fun blankThresholdIsLeftForTheRepositoryDefault() {
        var saved: MedicineDraft? = null
        show { saved = it }

        composeRule.onNodeWithTag("medicine-name").performTextInput("Vitamina D")
        composeRule.onNodeWithTag("medicine-stock").performScrollTo().performTextInput("60")
        save()

        assertEquals(60, saved?.stock)
        assertEquals(null, saved?.stockThreshold)
    }

    private val ferro = PlannerMedicine(
        "m9", "account-1", "planner-1", "Ferro", 2, DoseUnit.TABLET,
        setOf(LocalTime.of(7, 0), LocalTime.of(19, 30)),
        MedicineRepeat.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), thursday, MedicineStatus.ACTIVE,
        stock = MedicineStock(12, 30, 5),
    )

    @Test
    fun editingStartsFromTheCurrentMedicineAndSavesChangesBackAsADraft() {
        var saved: MedicineDraft? = null
        composeRule.setContent {
            PlannerTheme { MedicineForm(initialDay = thursday, onSave = { saved = it }, medicine = ferro) }
        }

        composeRule.onNodeWithText("Editar remédio").assertIsDisplayed()
        composeRule.onNodeWithTag("medicine-name").assertTextContains("Ferro")
        composeRule.onNodeWithTag("medicine-name").performTextReplacement("Ferro quelato")
        composeRule.onNodeWithTag("medicine-stock").performTextReplacement("20")
        save()

        assertEquals(
            MedicineDraft(
                "Ferro quelato", 2, DoseUnit.TABLET, setOf(LocalTime.of(7, 0), LocalTime.of(19, 30)),
                MedicineRepeat.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), thursday,
                stock = 20, stockThreshold = 5, stockAsShown = 12,
            ),
            saved,
        )
    }

    @Test
    fun stockAsShownStaysTheOneFromWhenTheFormOpenedEvenIfADoseIsTakenMeanwhile() {
        var saved: MedicineDraft? = null
        var medicine by mutableStateOf(ferro)
        composeRule.setContent {
            PlannerTheme { MedicineForm(initialDay = thursday, onSave = { saved = it }, medicine = medicine) }
        }

        medicine = ferro.copy(stock = MedicineStock(10, 30, 5))
        composeRule.waitForIdle()
        save()

        assertEquals(12, saved?.stock)
        assertEquals(12, saved?.stockAsShown)
    }

    @Test
    fun editingOffersToArchiveWhileCreatingDoesNot() {
        var archived = false
        composeRule.setContent {
            PlannerTheme { MedicineForm(initialDay = thursday, onSave = {}, medicine = ferro, onArchive = { archived = true }) }
        }

        composeRule.onNodeWithText("Arquivar remédio").performScrollTo().performClick()

        assertEquals(true, archived)
    }

    @Test
    fun creatingShowsNoArchiveAction() {
        show()

        composeRule.onNodeWithText("Arquivar remédio").assertDoesNotExist()
        composeRule.onNodeWithText("Novo remédio").assertIsDisplayed()
    }
}
