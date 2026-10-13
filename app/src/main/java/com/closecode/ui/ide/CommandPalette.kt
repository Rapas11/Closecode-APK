package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/** One palette row: a label, a muted shortcut, and what to run when it is picked. */
data class PaletteCommand(
    val label: String,
    val shortcut: String?,
    val run: () -> Unit,
)

/**
 * Spec 5 command palette: a scrim, a box of min(480, 92% of the screen) with a 14 dp radius and a
 * 12% top margin, a 15 sp search field, and rows that show a label and a muted shortcut.
 * Arrow keys move the selection, Enter runs it, Esc closes.
 */
@Composable
fun CommandPalette(
    commands: List<PaletteCommand>,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    val matched = paletteMatch(commands, query)
    val selected = matched.firstOrNull()?.let { commands.indexOf(it) } ?: 0

    Box(
        modifier
            .fillMaxWidth()
            .background(c.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 40.dp, start = 12.dp, end = 12.dp)
                // Spec 5: width min(480, 92% of the screen).
                .width(480.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(bottom = 8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Type a command or file name…",
                        color = c.mu,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = c.tx,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                    ),
                    cursorBrush = SolidColor(c.ac),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { matched.getOrNull(selected)?.run() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                matched.forEach { command ->
                    val isSel = commands.indexOf(command) == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Spec 5: the selected row is accent-soft with accent text.
                            .background(if (isSel) c.selection else Color.Transparent)
                            .clickable { command.run() }
                            // Spec 5: 11/14 row padding, label left and shortcut right.
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = command.label,
                            color = if (isSel) c.ac else c.tx,
                            fontSize = 15.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (!command.shortcut.isNullOrBlank()) {
                            Text(
                                text = command.shortcut,
                                color = c.mu,
                                fontSize = 12.sp,
                                fontFamily = Mono,
                            )
                        }
                    }
                }
                if (matched.isEmpty()) {
                    Text(
                        text = "No matching commands",
                        color = c.mu,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Spec 5 fuzzy match: every character of the query has to appear in order, so "opm" finds
 * "Open MainActivity". An empty query keeps the declared order.
 */
fun paletteMatch(commands: List<PaletteCommand>, query: String): List<PaletteCommand> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return commands
    return commands.filter { command ->
        val haystack = command.label.lowercase()
        var index = 0
        needle.all { ch ->
            val found = haystack.indexOf(ch, index)
            if (found < 0) {
                false
            } else {
                index = found + 1
                true
            }
        }
    }
}