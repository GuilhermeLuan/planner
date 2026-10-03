package dev.guilhermeluan.planner.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlannerTabHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setHost() = composeRule.setContent {
        PlannerTheme {
            PlannerTabHost(
                today = { Text("conteúdo do dia") },
                water = { Text("conteúdo da água") },
                you = { Text("conteúdo dos ajustes") },
            )
        }
    }

    @Test
    fun opensOnTodayWithTodayTabHighlighted() {
        setHost()

        composeRule.onNodeWithText("conteúdo do dia").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-hoje").assertIsSelected()
        composeRule.onNodeWithTag("tab-remedios").assertIsNotSelected()
    }

    @Test
    fun barOffersTheFourTabs() {
        setHost()

        listOf("Hoje", "Remédios", "Água", "Você").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun medicinesTabShowsEmptyStateInvitingToStart() {
        setHost()

        composeRule.onNodeWithTag("tab-remedios").performClick()

        composeRule.onNodeWithTag("tab-remedios").assertIsSelected()
        composeRule.onNodeWithTag("empty-remedios").assertIsDisplayed()
        composeRule.onNodeWithText("Nenhum remédio ainda").assertIsDisplayed()
    }

    @Test
    fun waterTabShowsTheProvidedContent() {
        setHost()

        composeRule.onNodeWithTag("tab-agua").performClick()

        composeRule.onNodeWithTag("tab-agua").assertIsSelected()
        composeRule.onNodeWithText("conteúdo da água").assertIsDisplayed()
    }

    @Test
    fun youTabShowsSettingsAndTodayComesBack() {
        setHost()

        composeRule.onNodeWithTag("tab-voce").performClick()
        composeRule.onNodeWithText("conteúdo dos ajustes").assertIsDisplayed()

        composeRule.onNodeWithTag("tab-hoje").performClick()
        composeRule.onNodeWithText("conteúdo do dia").assertIsDisplayed()
    }

    @Test
    fun medicinesTabShowsTheProvidedContent() {
        composeRule.setContent {
            PlannerTheme {
                PlannerTabHost(
                    today = { Text("conteúdo do dia") },
                    water = { Text("conteúdo da água") },
                    you = { Text("conteúdo dos ajustes") },
                    medicines = { Text("conteúdo dos remédios") },
                )
            }
        }

        composeRule.onNodeWithTag("tab-remedios").performClick()

        composeRule.onNodeWithText("conteúdo dos remédios").assertIsDisplayed()
    }
}
