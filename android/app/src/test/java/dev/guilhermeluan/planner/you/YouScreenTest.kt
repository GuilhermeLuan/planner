package dev.guilhermeluan.planner.you

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.guilhermeluan.planner.tasks.ArchivedItems
import dev.guilhermeluan.planner.tasks.DoseUnit
import dev.guilhermeluan.planner.tasks.MedicineRepeat
import dev.guilhermeluan.planner.tasks.MedicineStatus
import dev.guilhermeluan.planner.tasks.PlannerMedicine
import dev.guilhermeluan.planner.tasks.PlannerRoutine
import dev.guilhermeluan.planner.tasks.PlannerTask
import dev.guilhermeluan.planner.tasks.RoutineStatus
import dev.guilhermeluan.planner.tasks.TaskStatus
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
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp")
class YouScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val september = LocalDate.of(2026, 9, 30)

    private fun state(
        consistency: Consistency? = Consistency(waterStreakDays = 12, onTimeDosePercent = 96, routinesDoneThisMonth = 41),
        notificationsEnabled: Boolean = true,
        lastBackup: LocalDate? = LocalDate.of(2026, 9, 28),
        archived: ArchivedItems = ArchivedItems(),
    ) = YouUiState(
        name = "Guilherme",
        timezone = "America/Sao_Paulo",
        memberSince = YearMonth.of(2026, 7),
        today = september,
        consistency = consistency,
        notificationsEnabled = notificationsEnabled,
        lastBackup = lastBackup,
        archived = archived,
    )

    private fun show(
        state: YouUiState = state(),
        onSaveName: (String) -> Unit = {},
        onSaveTimezone: (String) -> Unit = {},
        onOpenNotificationSettings: () -> Unit = {},
        onExportBackup: () -> Unit = {},
        onImportBackup: () -> Unit = {},
        onRestoreTask: (String) -> Unit = {},
        onRestoreRoutine: (String) -> Unit = {},
        onRestoreMedicine: (String) -> Unit = {},
    ) = composeRule.setContent {
        PlannerTheme(darkTheme = false) {
            YouTab(
                state = state,
                onSaveName = onSaveName,
                onSaveTimezone = onSaveTimezone,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                onRestoreTask = onRestoreTask,
                onRestoreRoutine = onRestoreRoutine,
                onRestoreMedicine = onRestoreMedicine,
                detectedTimezone = "America/Sao_Paulo",
            )
        }
    }

    private fun openSettings() = composeRule.onNodeWithText("Configurações").performClick()

    @Test
    fun profileShowsTheInitialTheNameAndSinceWhenThePersonUsesThePlanner() {
        show()

        composeRule.onNodeWithText("G").assertIsDisplayed()
        composeRule.onNodeWithText("Guilherme").assertIsDisplayed()
        composeRule.onNodeWithText("No Planner desde julho").assertIsDisplayed()
    }

    @Test
    fun consistencyShowsTheWaterStreakTheOnTimeDosesAndTheRoutinesOfTheMonth() {
        show()

        composeRule.onNodeWithText("Sua constância").assertIsDisplayed()
        composeRule.onNodeWithText("12 dias").assertIsDisplayed()
        composeRule.onNodeWithText("seguidos batendo a meta de água").assertIsDisplayed()
        composeRule.onNodeWithText("96%").assertIsDisplayed()
        composeRule.onNodeWithText("das doses de setembro tomadas na hora").assertIsDisplayed()
        composeRule.onNodeWithText("41").assertIsDisplayed()
        composeRule.onNodeWithText("rotinas concluídas este mês").assertIsDisplayed()
    }

    @Test
    fun consistencySaysADayInTheSingularAndADashWhenNoDoseIsDueYet() {
        show(state(Consistency(waterStreakDays = 1, onTimeDosePercent = null, routinesDoneThisMonth = 0)))

        composeRule.onNodeWithText("1 dia").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun settingsOpenFromTheRowAndGroupAccountAndRemindersAndData() {
        show()

        openSettings()

        composeRule.onNodeWithText("Conta, lembretes e dados").assertIsDisplayed()
        composeRule.onNodeWithText("Conta").assertIsDisplayed()
        composeRule.onNodeWithText("Nome").assertIsDisplayed()
        composeRule.onNodeWithText("Fuso da Conta").assertIsDisplayed()
        composeRule.onNodeWithText("Lembretes e dados").assertIsDisplayed()
        composeRule.onNodeWithText("Notificações").assertIsDisplayed()
        composeRule.onNodeWithText("Itens arquivados").assertIsDisplayed()
        composeRule.onNodeWithText("Tarefas, rotinas e remédios").assertIsDisplayed()
        composeRule.onNodeWithText("Backup").assertIsDisplayed()
    }

    @Test
    fun settingsAlsoOpenFromTheGearButtonAndGoBackToTheProfile() {
        show()

        composeRule.onNodeWithContentDescription("Abrir configurações").performClick()
        composeRule.onNodeWithText("Lembretes e dados").assertIsDisplayed()
        composeRule.onNodeWithText("Você").performClick()

        composeRule.onNodeWithText("Sua constância").assertIsDisplayed()
    }

    @Test
    fun notificationsStatusLinksToTheSystemSettings() {
        var opened = 0
        show(onOpenNotificationSettings = { opened++ })
        openSettings()

        composeRule.onNodeWithText("Ativas").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun disabledNotificationsAreSaidSo() {
        show(state(notificationsEnabled = false))
        openSettings()

        composeRule.onNodeWithText("Desativadas").assertIsDisplayed()
    }

    @Test
    fun personEditsTheirName() {
        val names = mutableListOf<String>()
        show(onSaveName = { names += it })
        openSettings()

        composeRule.onNodeWithText("Editar").performClick()
        composeRule.onNodeWithTag("account-name").performTextReplacement("  Gui ")
        composeRule.onNodeWithText("Salvar nome").performClick()

        assertEquals(listOf("Gui"), names)
    }

    @Test
    fun nameCannotBeEmpty() {
        show()
        openSettings()

        composeRule.onNodeWithText("Editar").performClick()
        composeRule.onNodeWithTag("account-name").performTextReplacement("  ")

        composeRule.onNodeWithText("Salvar nome").assertIsNotEnabled()
    }

    @Test
    fun personChangesTheAccountTimezoneAndInvalidOnesAreRefused() {
        val zones = mutableListOf<String>()
        show(onSaveTimezone = { zones += it })
        openSettings()

        composeRule.onNodeWithText("Alterar").performClick()
        composeRule.onNodeWithTag("account-timezone").performTextReplacement("Marte/Olympus")
        composeRule.onNodeWithText("Fuso da Conta inválido").assertIsDisplayed()
        composeRule.onNodeWithText("Salvar fuso").assertIsNotEnabled()
        composeRule.onNodeWithTag("account-timezone").performTextReplacement("America/Manaus")
        composeRule.onNodeWithText("Salvar fuso").performClick()

        assertEquals(listOf("America/Manaus"), zones)
    }

    @Test
    fun backupShowsWhenTheLastOneWasSavedAndExportsOnTap() {
        var exported = 0
        show(onExportBackup = { exported++ })
        openSettings()

        composeRule.onNodeWithText("Último em 28 de setembro").assertIsDisplayed()
        composeRule.onNodeWithText("Exportar").performClick()

        assertEquals(1, exported)
    }

    @Test
    fun backupSaysWhenNoneWasExportedYet() {
        show(state(lastBackup = null))
        openSettings()

        composeRule.onNodeWithText("Nenhum backup ainda").assertIsDisplayed()
    }

    @Test
    fun importBackupShowsInSettingsAndImportsOnTap() {
        var imported = 0
        show(onImportBackup = { imported++ })
        openSettings()

        composeRule.onNodeWithText("Importar backup").assertIsDisplayed()
        composeRule.onNodeWithText("Importar").performClick()

        assertEquals(1, imported)
    }

    private val task = PlannerTask("t1", "a", "p", "Pagar conta", LocalDate.of(2026, 9, 3), LocalTime.of(9, 0), TaskStatus.PENDING, true, 0)
    private val routine = PlannerRoutine("r1", "a", "p", "Alongar", setOf(DayOfWeek.MONDAY), LocalDate.of(2026, 8, 1), null, RoutineStatus.ARCHIVED, 0)
    private val medicine = PlannerMedicine(
        "m1", "a", "p", "Losartana", 1, DoseUnit.TABLET, setOf(LocalTime.of(8, 0)), MedicineRepeat.Daily,
        LocalDate.of(2026, 8, 1), MedicineStatus.ARCHIVED,
    )

    @Test
    fun archivedItemsGatherTasksRoutinesAndMedicinesAndRestoreEach() {
        val restored = mutableListOf<String>()
        show(
            state(archived = ArchivedItems(listOf(task), listOf(routine), listOf(medicine))),
            onRestoreTask = { restored += "tarefa:$it" },
            onRestoreRoutine = { restored += "rotina:$it" },
            onRestoreMedicine = { restored += "remedio:$it" },
        )
        openSettings()

        composeRule.onNodeWithText("Ver").performClick()
        composeRule.onNodeWithText("Pagar conta").assertIsDisplayed()
        composeRule.onNodeWithText("Alongar").assertIsDisplayed()
        composeRule.onNodeWithText("Losartana").assertIsDisplayed()
        composeRule.onNodeWithTag("restore-task-t1").performClick()
        composeRule.onNodeWithTag("restore-routine-r1").performClick()
        composeRule.onNodeWithTag("restore-medicine-m1").performClick()

        assertEquals(listOf("tarefa:t1", "rotina:r1", "remedio:m1"), restored)
    }

    @Test
    fun archivedItemsSayWhenThereIsNothingToRestore() {
        show()
        openSettings()

        composeRule.onNodeWithText("Ver").performClick()

        composeRule.onNodeWithText("Nada arquivado").assertIsDisplayed()
    }
}
