package com.closecode.ui.ide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.lang.Severity
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Why screen previews are static drawings and not renders.
 *
 * Spiked properly before this was written: true @Preview rendering needs layoutlib, which is
 * not published on Maven (it ships inside Android Studio and needs a desktop JVM with AWT —
 * ART has neither), and even compiling on-device (kotlin-compiler-embeddable does resolve)
 * would still end at the same missing renderer, after adding tens of megabytes to the APK
 * and minutes of phone CPU per save. So every @Preview function gets a card drawn from what
 * it declares — its texts and its button — with Done, Rendering and Failed states, instead of
 * pixels that cannot exist here.
 */

/** One @Preview function: its name and the 1-based line it starts on. */
internal data class PreviewFunction(val name: String, val line: Int)

/**
 * Every function marked @Preview in [source], in file order. A @Preview annotation only counts
 * when it sits directly above a function — anything else is a decoration on something this
 * panel does not draw.
 */
internal fun previewFunctionsOf(source: String): List<PreviewFunction> {
    val out = ArrayList<PreviewFunction>()
    val lines = source.lines()
    var i = 0
    while (i < lines.size) {
        if (lines[i].trimStart().startsWith("@Preview")) {
            var j = i + 1
            // Annotations can stack (@Preview @Composable): walk past all of them.
            while (j < lines.size && lines[j].trimStart().startsWith("@")) j++
            val match = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)""").find(lines.getOrElse(j) { "" })
            if (match != null) {
                out.add(PreviewFunction(match.groupValues[1], j + 1))
            }
            i = j + 1
        } else {
            i++
        }
    }
    return out
}

/** One rendered card: which function, from which file, saying what, and what broke. */
internal data class ScreenPreview(
    val function: String,
    val path: String,
    val line: Int,
    val title: String,
    val subtitle: String?,
    val button: String?,
    val error: ProjectDiagnostic?,
)

/**
 * The error that belongs to one preview: an error in the same file between the function's
 * first line and the next preview's (or the file end). A red card must point at a real line,
 * not at whatever the file's first problem happens to be.
 */
internal fun previewErrorFor(
    errors: List<ProjectDiagnostic>,
    fileName: String,
    fromLine: Int,
    endLine: Int,
): ProjectDiagnostic? = errors.firstOrNull { row ->
    row.fileName == fileName && row.diagnostic.line in fromLine..endLine &&
        row.diagnostic.severity == Severity.ERROR
}

private enum class ScreensView { All, Split, Full }

/** Card widths per device, in dp. Heights follow the 132-by-268 ratio. */
private fun screensCardWidth(device: String): Int = when (device) {
    "Small phone" -> 120
    "Pixel Fold" -> 160
    "Tablet" -> 190
    else -> 132
}

/**
 * Every @Preview function in the project as a card, with Done, Rendering and Failed states
 * across All screens, Split and Full screen views. The pictures are static drawings from what
 * each function declares — see the note on [previewFunctionsOf] for why they are not renders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.ScreensPanel(
    files: List<ProjectFile>,
    loader: (String) -> String?,
    refreshToken: Int,
    activePath: String,
    errors: List<ProjectDiagnostic>,
    onOpenFile: (String, Int) -> Unit,
) {
    val c = LocalPageColors.current
    var view by remember { mutableStateOf(ScreensView.All) }
    var device by remember { mutableStateOf("Pixel 8") }
    var devicesOpen by remember { mutableStateOf(false) }
    var darkCards by remember { mutableStateOf(false) }
    var fullIndex by remember { mutableStateOf(0) }
    var localRefresh by remember { mutableStateOf(0) }
    var refreshedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            tick = System.currentTimeMillis()
        }
    }
    val previews by produceState<List<ScreenPreview>?>(
        initialValue = null,
        files,
        refreshToken,
        localRefresh,
    ) {
        value = withContext(Dispatchers.IO) {
            val out = ArrayList<ScreenPreview>()
            for (file in files) {
                val source = loader(file.path) ?: continue
                val fns = previewFunctionsOf(source)
                fns.forEachIndexed { index, fn ->
                    // Texts and errors come from inside this function's own braces, never from
                    // the file around it: the next function's button is not this card's button,
                    // and a neighbour's broken line is not this card's failure.
                    val bodyRange = functionBodyOf(source, fn.line)
                    val body = bodyRange?.let { source.substring(it.first, it.last + 1) } ?: ""
                    val content = extractComposePreview(body, fn.name)
                    val (fromLine, endLine) = if (bodyRange != null) {
                        // The declaration line belongs to the card too: a fault in the
                        // parameter list fails this function, not the file around it. The
                        // range is the inner span, so its last + 1 is the closing brace.
                        fn.line to lineOfOffset(source, bodyRange.last + 1)
                    } else {
                        fn.line to (
                            fns.getOrNull(index + 1)?.line?.minus(1)
                                ?: (source.lines().size + 1)
                            )
                    }
                    out.add(
                        ScreenPreview(
                            function = fn.name,
                            path = file.path,
                            line = fn.line,
                            title = content.title,
                            subtitle = content.subtitle,
                            button = content.button,
                            error = previewErrorFor(errors, file.name, fromLine, endLine),
                        ),
                    )
                }
            }
            refreshedAt = System.currentTimeMillis()
            out
        }
    }
    val ago = remember(tick, refreshedAt) { updatedAgo(refreshedAt, tick) }
    val cardW = screensCardWidth(device)

    // The top row: device, theme, and how fresh the cards are.
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.panel)
            .border(1.dp, c.divider, RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.chipBg)
                    .clickable { devicesOpen = true }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CcIcon(CcIcons.Phone, c.textMuted, 13.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(text = device, color = c.text, fontSize = 12.sp, fontFamily = Sans)
                    Spacer(Modifier.width(4.dp))
                    CcIcon(CcIcons.Down, c.textMuted, 12.dp)
                }
            }
            androidx.compose.material3.DropdownMenu(
                expanded = devicesOpen,
                onDismissRequest = { devicesOpen = false },
            ) {
                listOf("Pixel 8", "Pixel Fold", "Small phone", "Tablet").forEach { name ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name, fontFamily = Sans) },
                        onClick = {
                            devicesOpen = false
                            device = name
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.chipBg)
                .clickable { darkCards = !darkCards }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CcIcon(
                    if (darkCards) CcIcons.Moon else CcIcons.Sun,
                    c.textMuted,
                    13.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (darkCards) "Dark" else "Light",
                    color = c.text,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(7.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(c.success),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = ago, color = c.textMuted, fontSize = 11.sp, fontFamily = Sans)
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { localRefresh++ }
                .semantics { contentDescription = "Refresh previews" },
            contentAlignment = Alignment.Center,
        ) {
            CcIcon(CcIcons.Refresh, c.textMuted, 15.dp)
        }
    }

 com.closecode.ui.components.AppSegmentedControl(
        items = listOf(
            com.closecode.ui.components.SegmentItem("All screens"),
            com.closecode.ui.components.SegmentItem("Split view"),
            com.closecode.ui.components.SegmentItem("Full screen"),
        ),
        selected = view.ordinal,
        onSelect = { view = ScreensView.entries[it] },
        modifier = Modifier.padding(top = 8.dp),
    )

    val current = previews
    // While parsing after a save, the previous cards stay up dimmed with a spinner instead of
    // vanishing: a blank panel on every save reads as a flicker, and the names with their
    // clocks say what is being redrawn.
    var lastGood by remember { mutableStateOf(emptyList<ScreenPreview>()) }
    LaunchedEffect(current) {
        if (current != null) lastGood = current
    }
    val shown = current ?: lastGood
    // Errors the cards did not claim, by file: a file whose problems all sit outside every
    // preview body keeps normal cards and says so in a banner, instead of failing a card
    // whose own body is clean.
    val claimed = remember(shown) { shown.mapNotNull { it.error }.toSet() }
    val banners = remember(shown, errors) {
        files.mapNotNull { file ->
            val outside = errors.filter { row ->
                row.fileName == file.name &&
                    row.diagnostic.severity == Severity.ERROR &&
                    row !in claimed
            }
            if (outside.isEmpty()) null else file to outside
        }
    }
    banners.forEach { (file, outside) ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.warnPillBg)
                .clickable {
                    onOpenFile(file.path, outside.first().diagnostic.line.coerceAtLeast(1))
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${file.name} has ${outside.size} " +
                    "${if (outside.size == 1) "error" else "errors"} · View errors",
                color = c.warnPillTx,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val rendering = current == null
    when {
        rendering && lastGood.isEmpty() -> {
            // Still parsing after a save: dim placeholders with a spinner, never a blank panel.
            ScreensGridPlaceholder(cardW = cardW)
        }
        shown.isEmpty() -> {
            NoPreviews(onCopy = {})
        }
        view == ScreensView.All -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(shown.size) { index ->
                    val preview = shown[index]
                    ScreenCardView(
                        preview = preview,
                        widthDp = cardW,
                        dark = darkCards,
                        dimmed = rendering,
                        onTap = {
                            fullIndex = index
                            view = ScreensView.Full
                        },
                        onLongPress = { onOpenFile(preview.path, preview.line) },
                        onGoToError = {
                            val line = preview.error?.diagnostic?.line ?: preview.line
                            onOpenFile(preview.path, line.coerceAtLeast(1))
                        },
                    )
                }
            }
        }
        view == ScreensView.Split -> {
            // The code sits above this panel already, so split is a horizontal row of smaller
            // cards that scrolls to the open file's function. No drag handle: the panel cannot
            // resize its parent, so the ratio is fixed instead of dragged.
            val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(activePath, shown) {
                val at = shown.indexOfFirst { it.path == activePath }.coerceAtLeast(0)
                rowState.scrollToItem(at)
            }
            androidx.compose.foundation.lazy.LazyRow(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                items(shown.size) { index ->
                    val preview = shown[index]
                    ScreenCardView(
                        preview = preview,
                        widthDp = (cardW * 0.82).toInt(),
                        dark = darkCards,
                        dimmed = rendering,
                        onTap = {
                            fullIndex = index
                            view = ScreensView.Full
                        },
                        onLongPress = { onOpenFile(preview.path, preview.line) },
                        onGoToError = {
                            val line = preview.error?.diagnostic?.line ?: preview.line
                            onOpenFile(preview.path, line.coerceAtLeast(1))
                        },
                    )
                }
            }
        }
        else -> {
            val preview = shown.getOrNull(fullIndex.coerceIn(0, shown.size - 1))
            if (preview != null) {
                FullScreenPreview(
                    preview = preview,
                    widthDp = (cardW * 1.32).toInt(),
                    dark = darkCards,
                    position = fullIndex.coerceIn(0, shown.size - 1) + 1,
                    total = shown.size,
                    onPrev = { fullIndex = (fullIndex - 1 + shown.size) % shown.size },
                    onNext = { fullIndex = (fullIndex + 1) % shown.size },
                    onOpenCode = { onOpenFile(preview.path, preview.line) },
                    onGoToError = {
                        val line = preview.error?.diagnostic?.line ?: preview.line
                        onOpenFile(preview.path, line.coerceAtLeast(1))
                    },
                )
            }
        }
    }
}

/** "Updated X ago" for the top row, ticking with the panel. */
internal fun updatedAgo(refreshedAt: Long, now: Long): String {
    val sec = ((now - refreshedAt) / 1_000).coerceAtLeast(0)
    return when {
        sec < 5 -> "Updated just now"
        sec < 60 -> "Updated $sec s ago"
        else -> "Updated ${sec / 60} min ago"
    }
}

/** Dimmed placeholders while parsing after a save. */
@Composable
private fun ColumnScope.ScreensGridPlaceholder(cardW: Int) {
    val c = LocalPageColors.current
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(4) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(cardW.dp)
                        .height((cardW * 2.03).toInt().dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.panel2.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = c.textMuted,
                        strokeWidth = 2.dp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Rendering…",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
        }
    }
}

/** No @Preview functions anywhere: what to add, with a copyable example. */
@Composable
private fun ColumnScope.NoPreviews(onCopy: () -> Unit) {
    val c = LocalPageColors.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No previews found",
            color = c.text,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Mark a function with @Preview and it shows up here after the next save.",
            color = c.textMuted,
            fontSize = 12.sp,
            fontFamily = Sans,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.fieldBg)
                .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Text(
                text = "@Preview\n@Composable\nfun HomeScreen() {\n    Text(\"Hello\")\n}",
                color = c.text,
                fontSize = 12.sp,
                fontFamily = Mono,
            )
        }
        Spacer(Modifier.height(12.dp))
        com.closecode.ui.components.OutlineButton(
            label = "Copy example",
            onClick = {
                clipboard.setText(
                    androidx.compose.ui.text.AnnotatedString(
                        "@Preview\n@Composable\nfun HomeScreen() {\n    Text(\"Hello\")\n}",
                    ),
                )
                onCopy()
            },
        )
    }
}

/**
 * One screen card: a phone frame with the function's declared texts, or the dark-red failure
 * panel with a way to the error. A tap opens it full screen; a long press jumps to its code.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ScreenCardView(
    preview: ScreenPreview,
    widthDp: Int,
    dark: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onGoToError: () -> Unit = onLongPress,
    // While re-parsing after a save: the old card under a 62% dark layer with the error
    // red reserved for real failures, never for "busy".
    dimmed: Boolean = false,
) {
    val c = LocalPageColors.current
    val heightDp = (widthDp * 2.03).toInt()
    val frame = Modifier
        .width(widthDp.dp)
        .height(heightDp.dp)
        .clip(RoundedCornerShape(16.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // The dim layer stacks over the old card while re-parsing: same frame, 62% dark,
        // spinner centered. Drawn as a sibling over the frame, never inside it, so the
        // stale drawing underneath stays untouched.
        Box(contentAlignment = Alignment.TopCenter) {
            Box(
                frame
                    .background(
                        if (preview.error != null) {
                            // A failed card is a dark red panel in either theme: the color is
                            // the alarm, and theming it away would disarm it.
                            Color(0xFF2A1519)
                        } else if (dark) {
                            c.surface
                        } else {
                            androidx.compose.ui.graphics.Color.White
                        },
                    )
                    .border(3.dp, Color(0xFF232938), RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                    .padding(12.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
            if (preview.error != null) {
                val error = preview.error
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CcIcon(CcIcons.Warning, Color(0xFFEF5B5B), 22.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Preview failed",
                        color = Color(0xFFF2A0A0),
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = error.diagnostic.message,
                        color = c.textMuted,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${error.fileName}:${error.diagnostic.line}",
                        color = c.textMuted,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.popupSelected)
                            .clickable { onGoToError() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Go to error",
                            color = c.text,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                MockPhoneContent(
                    title = preview.title,
                    subtitle = preview.subtitle,
                    button = preview.button,
                    dark = dark,
                )
            }
            // The dim layer over the old card while re-parsing: 62% dark, spinner centered.
            // A sibling stacked after the frame inside the same box, so it covers the stale
            // drawing without touching it.
            if (dimmed) {
                Box(
                    frame
                        .background(Color(0x9E0B0D12)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = c.textMuted,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        }
        Spacer(Modifier.height(6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview.function,
                    color = c.text,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                if (dimmed) {
                    CcIcon(CcIcons.Clock, c.textMuted, 12.dp)
                } else if (preview.error != null) {
                    CcIcon(CcIcons.Cross, Color(0xFFEF5B5B), 12.dp)
                } else {
                    CcIcon(CcIcons.Check, c.success, 12.dp)
                }
            }
            // The drawings are sketches from declared strings, not renders: the card says so.
            Text(
                text = "Approximate preview",
                color = c.textMuted,
                fontSize = 10.sp,
                fontFamily = Sans,
                maxLines = 1,
            )
        }
    }
}

/** The static drawing: title, subtitle and button from what the function declares. */
@Composable
private fun MockPhoneContent(title: String, subtitle: String?, button: String?, dark: Boolean) {
    val titleColor = if (dark) Color(0xFFE7ECF5) else Color(0xFF101826)
    val subColor = if (dark) Color(0xFF8B98B0) else Color(0xFF66728A)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status dots, so the frame reads as a phone rather than a blank card.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(subColor.copy(alpha = 0.5f)),
                )
            }
        }
        Text(
            text = title,
            color = titleColor,
            fontSize = 15.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        subtitle?.let {
            Text(
                text = it,
                color = subColor,
                fontSize = 11.sp,
                fontFamily = Sans,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        button?.let {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2F6FED))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One card large, with arrows, dots and Open in code. Pinch zooms from 1x to 3x; the zoom
 * resets when the card changes, because a magnified stranger is not what the arrows promise.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.FullScreenPreview(
    preview: ScreenPreview,
    widthDp: Int,
    dark: Boolean,
    position: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenCode: () -> Unit,
    onGoToError: () -> Unit,
) {
    val c = LocalPageColors.current
    var zoom by remember(preview) { mutableStateOf(1f) }
    val zoomState = androidx.compose.foundation.gestures.rememberTransformableState { factor, _, _ ->
        zoom = (zoom * factor).coerceIn(1f, 3f)
    }
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.transformable(zoomState),
        ) {
            ScreenCardView(
                preview = preview,
                widthDp = (widthDp * zoom).toInt(),
                dark = dark,
                onTap = {},
                onLongPress = {},
                onGoToError = onGoToError,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onPrev),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.ChevronLeft, c.textMuted, 18.dp)
            }
            Text(
                text = preview.function,
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.ChevronRight, c.textMuted, 18.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(total.coerceAtMost(12)) { index ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (index == position - 1) c.accent else c.divider),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        com.closecode.ui.components.OutlineButton(
            label = "Open in code",
            onClick = onOpenCode,
        )
    }
}
