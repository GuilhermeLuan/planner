package dev.guilhermeluan.planner.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.guilhermeluan.planner.ui.theme.PlannerExtras

@Composable
fun PlannerTabBar(
    selected: PlannerTab,
    onSelect: (PlannerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = barShape,
            ambientColor = Color(0x1A7A2E50),
            spotColor = Color(0x1A7A2E50),
        ),
        shape = barShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PlannerTab.entries.forEach { tab ->
                TabItem(tab, tab == selected) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun TabItem(tab: PlannerTab, isSelected: Boolean, onClick: () -> Unit) {
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else PlannerExtras.palette.mutedInk
    Column(
        modifier = Modifier
            .testTag("tab-${tab.tag}")
            .clip(RoundedCornerShape(18.dp))
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = tab.label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}
