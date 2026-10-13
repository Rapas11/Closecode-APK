package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.ide.model.AgentChips
import com.closecode.ui.ide.model.AgentIntro
import com.closecode.ui.ide.model.AgentLine
import com.closecode.ui.ide.model.AgentModes
import com.closecode.ui.ide.model.AgentWelcome
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono

@Composable
fun AgentPanel(projectName: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val lines = remember { mutableStateListOf<AgentLine>().apply { addAll(AgentIntro) } }
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(0) }
    var tokens by remember { mutableStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(c.panel)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "✻", color = c.ac, fontSize = 13.sp, fontFamily = Mono)
            Text(
                text = "Terminal agent",
                color = c.tx,
                fontSize = 13.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = " ~/$projectName",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Mono,
            )
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
        ) {
            itemsIndexedCompat(lines) { item ->
                AgentRow(item)
            }
            if (!answered && lines.size == AgentIntro.size) {
                itemsIndexedCompat(AgentWelcome) { item ->
                    AgentRow(item)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.panel)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AgentChips) { chip ->
                    Box(
                        Modifier
                            .border(1.dp, c.ln, RoundedCornerShape(50))
                            .clickable {
                                lines.add(AgentLine.User(chip))
                                if (chip.startsWith("fix")) {
                                    lines.add(AgentLine.Answer("Working through the task."))
                                    lines.add(
                                        AgentLine.Todo(
                                            listOf(
                                                "Find unsafe !! calls" to true,
                                                "Replace with safe fallbacks" to false,
                                                "Run tests" to false,
                                            )
                                        )
                                    )
                                    lines.add(AgentLine.Tool("Read", "NoteRepository.kt"))
                                    lines.add(AgentLine.ToolResult("Read 17 lines"))
                                    lines.add(AgentLine.Tool("Edit", "NoteRepository.kt"))
                                    lines.add(
                                        AgentLine.Diff(
                                            listOf(
                                                ' ' to "fun count(): Int {",
                                                '-' to "  return cache!!.size",
                                                '+' to "  return (cache ?: emptyList()).size",
                                                ' ' to "}",
                                            )
                                        )
                                    )
                                } else {
                                    lines.add(
                                        AgentLine.Answer(
                                            "Try: fix the null crashes, write unit tests, " +
                                                "or rename a symbol."
                                        )
                                    )
                                }
                                tokens += 180
                                answered = true
                            }
                            .padding(horizontal = 11.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = chip,
                            color = c.mu,
                            fontSize = 12.sp,
                            fontFamily = Mono,
                        )
                    }
                }
            }

            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.panel2)
                    .border(1.dp, c.ln, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = ">", color = c.ac, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (input.isEmpty()) {
                        Text(
                            text = "Ask for a change, or type /help",
                            color = c.mu,
                            fontSize = 13.sp,
                            fontFamily = Mono,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            color = c.tx,
                            fontSize = 13.sp,
                            fontFamily = Mono,
                        ),
                        cursorBrush = SolidColor(c.ac),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (input.isNotBlank()) {
                                lines.add(AgentLine.User(input.trim()))
                                lines.add(
                                    AgentLine.Answer(
                                        "This is a UI demo. Try a suggestion chip above."
                                    )
                                )
                                tokens += 40
                                input = ""
                            }
                        }
                        .background(c.ac, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "↵",
                        color = c.bg,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AgentModes[mode],
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    modifier = Modifier.clickable { mode = (mode + 1) % AgentModes.size },
                )
                Box(Modifier.weight(1f))
                Text(
                    text = "$tokens tokens · $${"%.2f".format(tokens * 0.000009)}",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(
    items: List<AgentLine>,
    content: @Composable (AgentLine) -> Unit,
) = items(items.size) { index -> content(items[index]) }

@Composable
private fun AgentRow(item: AgentLine) {
    val c = LocalPageColors.current

    when (item) {
        is AgentLine.User -> Row(Modifier.padding(top = 12.dp)) {
            Text(
                text = "> ${item.text}",
                color = c.tx,
                fontSize = 13.5.sp,
                fontFamily = Mono,
            )
        }

        is AgentLine.Answer -> Row(Modifier.padding(top = 8.dp)) {
            Text(text = "● ", color = c.ac, fontSize = 13.5.sp, fontFamily = Mono)
            Text(
                text = item.text,
                color = c.tx,
                fontSize = 13.5.sp,
                fontFamily = Mono,
            )
        }

        is AgentLine.Note -> Text(
            text = item.text,
            color = c.mu,
            fontSize = 13.5.sp,
            fontFamily = Mono,
            lineHeight = 21.6.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        is AgentLine.Tool -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "● ", color = c.string, fontSize = 13.5.sp, fontFamily = Mono)
            Text(
                text = item.name,
                color = c.tx,
                fontSize = 13.5.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "(${item.arg})",
                color = c.tx,
                fontSize = 13.5.sp,
                fontFamily = Mono,
            )
        }

        is AgentLine.ToolResult -> Text(
            text = "⎿  ${item.text}",
            color = c.mu,
            fontSize = 13.5.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(start = 2.dp),
        )

        is AgentLine.Todo -> Column(Modifier.padding(start = 14.dp, top = 4.dp)) {
            item.items.forEach { (label, done) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (done) "☒" else "☐",
                        color = if (done) c.string else c.mu,
                        fontSize = 13.5.sp,
                        fontFamily = Mono,
                    )
                    Text(
                        text = label,
                        color = c.tx,
                        fontSize = 13.5.sp,
                        fontFamily = Mono,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                }
            }
        }

        is AgentLine.Diff -> Column(
            Modifier
                .padding(start = 42.dp, top = 6.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, c.ln, RoundedCornerShape(8.dp))
        ) {
            item.rows.forEach { (marker, text) ->
                val bg = when (marker) {
                    '+' -> c.string.copy(alpha = 0.14f)
                    '-' -> c.red.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$marker $text",
                        color = c.tx,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        fontFamily = Mono,
                        softWrap = false,
                    )
                }
            }
        }

        is AgentLine.Permission -> Column(
            Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.panel2)
                .border(1.dp, c.ac, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = item.title,
                color = c.tx,
                fontSize = 13.5.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
            )
            item.options.forEachIndexed { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${index + 1}. $option",
                        color = if (index == 0) c.ac else c.tx,
                        fontSize = 13.5.sp,
                        fontFamily = Mono,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}