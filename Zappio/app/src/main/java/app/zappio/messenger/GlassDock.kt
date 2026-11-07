package app.zappio.messenger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class DockTab(val icon: ImageVector, val label: String)

private val tabs = listOf(
    DockTab(Icons.Rounded.Home, "Главная"),
    DockTab(Icons.Rounded.Contacts, "Контакты"),
    DockTab(Icons.Rounded.Person, "Профиль")
)

/** Плавающий док-бар в стиле glassmorphism. */
@Composable
fun GlassDock(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        cs.surfaceContainerHighest.copy(alpha = 0.62f),
                        cs.surfaceContainerHigh.copy(alpha = 0.38f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))
                ),
                shape
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            DockItem(tab.icon, tab.label, index == selected) { onSelect(index) }
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) cs.primary.copy(alpha = 0.92f) else Color.Transparent,
        label = "dockBg"
    )
    val fg by animateColorAsState(
        if (selected) cs.onPrimary else cs.onSurface,
        label = "dockFg"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = fg)
        AnimatedVisibility(visible = selected) {
            Text(
                label,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
