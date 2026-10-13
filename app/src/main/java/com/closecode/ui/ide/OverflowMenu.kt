package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

/**
 * One overflow row. [onClick] carries the row's own behaviour; when it is null the menu falls back
 * to [OverflowMenu]'s `onPick`, which is how the older label-matched rows still work.
 */
data class OverflowAction(
    val label: String,
    val glyph: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * The rows the header's overflow draws, in order. Kept as data so a unit test can pin them without
 * rendering anything.
 *
 * Both rows act on the file that is open, which is why they sit under the file's own header. Search
 * moved here off the top bar, and the rows that leave the editor — settings, the theme switch —
 * live in the bottom More sheet instead, so every row has exactly one home rather than two menus
 * that overlap.
 */
internal val OverflowLabels = listOf(
    "Search and commands",
    "Format file",
)

@Composable
fun OverflowMenu(
    actions: List<OverflowAction>,
    onPick: (OverflowAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            )
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 66.dp, end = 8.dp)
                // Spec 4.1: min width 210, radius 12, a 1 dp border and a panel fill.
                .width(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(12.dp))
        ) {
            actions.forEach { action ->
                Text(
                    text = action.label,
                    color = c.tx,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { action.onClick?.invoke() ?: onPick(action) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
        }
    }
}