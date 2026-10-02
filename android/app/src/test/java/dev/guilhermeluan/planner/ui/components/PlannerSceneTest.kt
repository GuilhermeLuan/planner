package dev.guilhermeluan.planner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import dev.guilhermeluan.planner.ui.navigation.PlannerTab
import dev.guilhermeluan.planner.ui.theme.PlannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlannerSceneTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersWithTheColorsOfEveryTab() {
        composeRule.setContent {
            PlannerTheme {
                Column {
                    PlannerTab.entries.forEach { PlannerScene(SceneColors.forTab(it)) }
                }
            }
        }
        composeRule.onAllNodesWithTag("planner-scene").assertCountEquals(PlannerTab.entries.size)
    }

    @Test
    fun eachTabHasItsOwnSky() {
        val skies = listOf(PlannerTab.Today, PlannerTab.Medicines, PlannerTab.Water).map { SceneColors.forTab(it).skyBottom }
        assertEquals(3, skies.toSet().size)
        assertNotEquals(SceneColors.forTab(PlannerTab.Today).skyBottom, SceneColors.forTab(PlannerTab.Water).skyBottom)
    }
}
