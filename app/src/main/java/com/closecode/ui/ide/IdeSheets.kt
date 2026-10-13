package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.closecode.ui.components.SpecMenu
import com.closecode.ui.components.SpecMenuItem
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/** Cap on a single project-wide search, so a one-letter query cannot lock the sheet up. */
internal const val MAX_SEARCH_HITS = 200

/** One match in a project-wide search: the file it is in, the 1-based line, and that line. */
data class SearchHit(
    val fileName: String,
    val path: String,
    val line: Int,
    val text: String,
)

/**
 * Case-insensitive substring search over the whole project, in file order and then line order so
 * the results are stable while the user types. Blank lines and folders are skipped, and the run
 * stops at [limit] so a broad query still answers instantly.
 */
/**
 * How a query is matched against a line.
 *
 * A plain substring is the default because it is the one that cannot go wrong: no pattern to
 * mistype, nothing to escape. Case and patterns are the reader's choice.
 */
internal data class SearchMode(
    val pattern: String,
    val caseSensitive: Boolean = false,
    val regex: Boolean = false,
    val wholeWord: Boolean = false,
)

/**
 * Compiles a query into something that can test a line, or null when the pattern is not valid.
 *
 * Null rather than an empty result, because those are very different answers — "you typed it wrong"
 * against "there is none of it here" — and a search that cannot tell them apart sends the reader
 * looking for something that was never there.
 */
internal fun lineMatcher(mode: SearchMode): ((String) -> Boolean)? {
    val needle = mode.pattern.trim()
    if (needle.isEmpty()) return null
    val options = if (mode.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    if (!mode.regex && !mode.wholeWord) {
        return if (mode.caseSensitive) {
            { line -> line.contains(needle) }
        } else {
            { line -> line.contains(needle, ignoreCase = true) }
        }
    }
    // Whole words are word-boundary guards around the needle: a plain needle is escaped first
    // so `(` still searches for a bracket, while a pattern is wrapped untouched.
    val body = if (mode.regex) "(?:$needle)" else Regex.escape(needle)
    val compiled = runCatching { Regex("(?<!\\w)$body(?!\\w)", options) }.getOrNull() ?: return null
    return { line -> compiled.containsMatchIn(line) }
}

/**
 * Search over the whole project, in file order and then line order so the results are stable while
 * the reader types. Blank lines and folders are skipped, and the run stops at [limit] so a broad
 * query still answers instantly.
 *
 * The search always folded case before, which is defensible right up until the day a query for a
 * class name matches the documentation that mentions it, and there was no way to ask for anything a
 * plain string cannot say.
 */
fun findInSources(
    query: String,
    sources: List<Pair<ProjectFile, String>>,
    limit: Int = MAX_SEARCH_HITS,
    caseSensitive: Boolean = false,
    regex: Boolean = false,
    wholeWord: Boolean = false,
): List<SearchHit> {
    val matches = lineMatcher(SearchMode(query, caseSensitive, regex, wholeWord)) ?: return emptyList()
    val hits = ArrayList<SearchHit>()
    for ((file, text) in sources) {
        if (file.kind == FileKind.Folder) continue
        val lines = text.split('\n')
        var index = 0
        while (index < lines.size) {
            if (matches(lines[index])) {
                hits.add(SearchHit(file.name, file.path, index + 1, lines[index].trim()))
                if (hits.size >= limit) return hits
            }
            index++
        }
    }
    return hits
}

/**
 * The surface every bottom sheet in the IDE is drawn on: a full-screen scrim that dismisses on a
 * tap anywhere above the sheet, and the sheet itself pinned to the bottom with a grab handle.
 */
@Composable
fun IdeBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(c.scrim)
            // Above the keyboard: a centered dialog on a small screen would otherwise sit
            // half under it, with the buttons the first thing to go.
            .imePadding()
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                // An enabled no-op, not `enabled = false`: a disabled clickable adds no input
                // handler at all, so taps on the sheet body would fall through to the scrim and
                // dismiss the sheet the user is reading.
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.ln),
            )
            content()
        }
    }
}

/** One row in the More sheet: a glyph, a label, and what tapping it does. */
data class MoreAction(
    val label: String,
    val glyph: String,
    val onClick: () -> Unit,
)

/**
 * The surface More and Search sit on: a scrim over everything above the bottom bar, and the
 * sheet pinned to the bar's top edge with no gap, growing upward to at most 60% of the screen.
 * The bar stays visible and tappable underneath (its active item stays highlighted), content
 * taller than the cap scrolls inside its own region, and a tap outside, a downward drag or
 * Back closes the sheet.
 *
 * [barHeight] is the measured bottom bar (0 while the keyboard hides it); the sheet and the
 * scrim both stop at the bar's top so neither can cover it.
 */
@Composable
fun IdeAnchoredSheet(
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
    val dismissPx = with(density) { 96.dp.toPx() }
    val dragOffset = remember { Animatable(0f) }
    Box(
        modifier
            .fillMaxSize()
            // Stop at the bar: no gap above it, nothing over it.
            .padding(bottom = barHeight)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // The scrim ends where the sheet begins and where the bar begins: taps above the
        // sheet dismiss, taps on the bar reach the bar.
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .graphicsLayer { translationY = dragOffset.value }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { dy ->
                        scope.launch { dragOffset.snapTo((dragOffset.value + dy).coerceAtLeast(0f)) }
                    },
                    onDragStopped = {
                        scope.launch {
                            if (dragOffset.value > dismissPx) onDismiss()
                            else dragOffset.animateTo(0f)
                        }
                    },
                )
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                // An enabled no-op, not `enabled = false`: a disabled clickable adds no input
                // handler at all, so taps on the sheet body would fall through to the scrim
                // and dismiss the sheet the user is reading.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.ln),
            )
            content()
        }
    }
}

/**
 * The rows the bottom More sheet draws, in order. Kept as data so a unit test can pin them without
 * rendering anything — and so every row the editor does not own has one home here rather than
 * turning up in the header's overflow as well.
 */
internal val MoreLabels = listOf(
    "Logcat",
    "Build",
    "Settings",
    "Toggle theme",
)

/**
 * The More sheet: the panels and preferences that are not about the file that is open, in one list
 * so the bottom bar can stay at three items. The rows that act on the open file — search, format —
 * live in the header's overflow.
 */
@Composable
fun IdeMoreSheet(
    actions: List<MoreAction>,
    onDismiss: () -> Unit,
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        Text(
            text = "MORE",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        actions.forEach { action ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        action.onClick()
                        onDismiss()
                    }
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = action.glyph,
                    color = c.ac,
                    fontSize = 14.sp,
                    fontFamily = Mono,
                    modifier = Modifier.size(width = 20.dp, height = 18.dp),
                )
                Text(
                    text = action.label,
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = "CloseCode v1.0 (build 2 · 8e20f1)",
            color = c.textFaint,
            fontSize = 11.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/**
 * One match line with every query occurrence highlighted: matchBg behind matchText,
 * so the eye lands on why the line matched. Plain queries match case-insensitively
 * unless asked otherwise; patterns reuse the search's own matcher.
 */
internal fun highlightedMatch(
    text: String,
    mode: SearchMode,
    matchBg: androidx.compose.ui.graphics.Color,
    matchFg: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val ranges: List<IntRange> = runCatching {
        if (!mode.regex && !mode.wholeWord) {
            val needle = if (mode.caseSensitive) mode.pattern else mode.pattern.lowercase()
            if (needle.isEmpty()) return@runCatching emptyList<IntRange>()
            val haystack = if (mode.caseSensitive) text else text.lowercase()
            val out = mutableListOf<IntRange>()
            var from = 0
            while (true) {
                val at = haystack.indexOf(needle, from)
                if (at < 0) break
                out.add(at until at + needle.length)
                from = at + needle.length
            }
            out
        } else {
            val options = if (mode.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val body = if (mode.regex) "(?:${mode.pattern})" else Regex.escape(mode.pattern)
            val compiled = Regex("(?<!\\w)$body(?!\\w)", options)
            compiled.findAll(text).map { it.range }.toList()
        }
    }.getOrElse { emptyList() }
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            if (range.first > cursor) append(text.substring(cursor, range.first))
            withStyle(SpanStyle(background = matchBg, color = matchFg)) {
                append(text.substring(range.first, (range.last + 1).coerceAtMost(text.length)))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/**
 * The Search sheet: find in project. It sits above the editor rather than replacing it, so the
 * results and the code they came from are on screen together, and a tap on the scrim dismisses it.
 */
@Composable
fun IdeSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    hits: List<SearchHit>,
    onOpen: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
    caseSensitive: Boolean = false,
    regex: Boolean = false,
    onCaseSensitiveChange: (Boolean) -> Unit = {},
    onRegexChange: (Boolean) -> Unit = {},
    wholeWord: Boolean = false,
    onWholeWordChange: (Boolean) -> Unit = {},
    barHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val c = LocalPageColors.current
    var optionsOpen by remember { mutableStateOf(false) }
    IdeAnchoredSheet(onDismiss = onDismiss, barHeight = barHeight) {
        // Rounded field: search icon, query, sliders options at the end.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.fieldBg)
                .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CcIcon(CcIcons.Search, c.mu, 16.dp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Find in project…",
                        color = c.mu,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = c.tx,
                        fontSize = 14.sp,
                        fontFamily = Sans,
                    ),
                    cursorBrush = SolidColor(c.ac),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Search,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // The options live behind one sliders icon at the field's end.
            Box {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { optionsOpen = true }
                        .semantics { contentDescription = "Search options" },
                    contentAlignment = Alignment.Center,
                ) {
                    CcIcon(
                        CcIcons.Sliders,
                        if (caseSensitive || regex || wholeWord) c.ac else c.mu,
                        18.dp,
                    )
                }
                if (optionsOpen) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { optionsOpen = false },
                            ),
                    )
                    SpecMenu(
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        SpecMenuItem(
                            icon = CcIcons.Check,
                            label = (if (caseSensitive) "✓ " else "") + "Match case",
                            onClick = { optionsOpen = false; onCaseSensitiveChange(!caseSensitive) },
                        )
                        SpecMenuItem(
                            icon = CcIcons.Check,
                            label = (if (wholeWord) "✓ " else "") + "Whole word",
                            onClick = { optionsOpen = false; onWholeWordChange(!wholeWord) },
                        )
                        SpecMenuItem(
                            icon = CcIcons.Check,
                            label = (if (regex) "✓ " else "") + "Regex",
                            onClick = { optionsOpen = false; onRegexChange(!regex) },
                        )
                    }
                }
            }
        }
        // A broken pattern says so under the field, where the eye already is after typing.
        val patternBroken = regex && query.isNotBlank() &&
            lineMatcher(SearchMode(query, caseSensitive, regex, wholeWord)) == null
        if (patternBroken) {
            Text(
                text = "Not a valid pattern",
                color = c.amber,
                fontSize = 12.sp,
                fontFamily = Sans,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))
        when {
            query.isBlank() -> Text(
                text = "Type to search every file in the project.",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(18.dp),
            )

            hits.isEmpty() -> Text(
                text = "No matches for \"$query\"",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(18.dp),
            )

            else -> Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                // "N results in M files", then one group per file: icon, name, folder
                // and a count badge, with each match line highlighted under it.
                Text(
                    text = "${hits.size} results in ${hits.map { it.path }.distinct().size} files",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
                val mode = SearchMode(query, caseSensitive, regex, wholeWord)
                hits.groupBy { it.path }.forEach { (path, group) ->
                    val first = group.first()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CcIcon(CcIcons.Document, c.mu, 15.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = first.fileName,
                            color = c.tx,
                            fontSize = 13.sp,
                            fontFamily = Sans,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = path.substringBeforeLast('/', ""),
                            color = c.mu,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(c.chipBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${group.size}",
                                color = c.ac,
                                fontSize = 11.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    group.forEach { hit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(hit) }
                                .padding(start = 41.dp, end = 18.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${hit.line}",
                                color = c.mu,
                                fontSize = 12.sp,
                                fontFamily = Mono,
                                modifier = Modifier.width(34.dp),
                            )
                            Text(
                                text = highlightedMatch(hit.text, mode, c.spec.matchBg, c.spec.matchText),
                                color = c.tx,
                                fontSize = 13.sp,
                                fontFamily = Mono,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The add-a-block sheet. One row per [BlockTemplate]; tapping a row writes that template's lines
 * into the buffer at the caret and closes. The sheet owns no text — it only chooses what
 * [insertBlockAt] should write, so the whole transaction is one call back to the screen.
 */
@Composable
fun IdeAddBlockSheet(
    onInsert: (BlockTemplate) -> Unit,
    onDismiss: () -> Unit,
    templates: List<BlockTemplate> = StatementSlots,
) {
    val c = LocalPageColors.current
    IdeBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "statement slot",
            color = c.mu,
            fontSize = 11.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            templates.forEach { template ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onInsert(template) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = template.label,
                        color = c.ac,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        text = template.lines.joinToString(" "),
                        color = c.tx.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * A one-field prompt for naming a new file or folder. Kept as its own dialog rather than a sheet
 * because it is answered once and then gets out of the way.
 *
 * [initial] and [confirmLabel] are what let the same dialog rename and move: creating starts blank
 * with "Create", renaming starts on the leaf with "Rename", and moving starts on the whole path
 * with "Move". One field, one set of keys, one thing to get right.
 */
@Composable
fun IdeNameDialog(
    title: String,
    hint: String,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    confirmLabel: String = "Create",
) {
    val c = LocalPageColors.current
    // Keyed on the value it starts with: a dialog that swaps one target for another without
    // leaving composition would otherwise keep the previous name in the field.
    val value = remember(initial) { mutableStateOf(initial) }
    val trimmed = value.value.trim()
    val changed = trimmed.isNotEmpty() && trimmed != initial

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                // Above the keyboard: a centered dialog on a small screen would otherwise sit
                // half under it, with the buttons the first thing to go.
                .imePadding()
                .clip(RoundedCornerShape(14.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(14.dp))
                // Enabled no-op: a disabled clickable would let the tap fall through to the scrim
                // and dismiss the dialog before the field took focus.
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.editor)
                    .border(1.dp, c.ln, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.value.isEmpty()) {
                    Text(
                        text = hint,
                        color = c.mu,
                        fontSize = 14.sp,
                        fontFamily = Mono,
                    )
                }
                BasicTextField(
                    value = value.value,
                    onValueChange = { value.value = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = c.tx,
                        fontSize = 14.sp,
                        fontFamily = Mono,
                    ),
                    cursorBrush = SolidColor(c.ac),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Plain text, never a filled square: Cancel walks away, it does nothing.
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        color = c.link,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        maxLines = 1,
                    )
                }
                com.closecode.ui.components.PrimaryButton(
                    label = confirmLabel,
                    enabled = changed,
                    onClick = { onCreate(trimmed) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A yes-or-no question, asked before anything is destroyed. Delete and overwrite both take
 * something the app cannot hand back — the trash is not a place this editor keeps — so neither is
 * allowed to happen on the same tap that offered it.
 */
@Composable
fun IdeConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {},
                )
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = message,
                color = c.mu,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontFamily = Sans,
            )
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetButton(label = "Cancel", fill = false, onClick = onDismiss)
                SheetButton(
                    label = confirmLabel,
                    fill = true,
                    destructive = true,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun RowScope.SheetButton(
    label: String,
    fill: Boolean,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val resting = when {
        !enabled -> c.panel2
        destructive -> c.red
        else -> c.ac
    }
    Box(
        Modifier
            .then(if (fill) Modifier.weight(1f, fill = true) else Modifier)
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(resting)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) c.af else c.mu,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** One row of the search options menu: a plain word and a check mark when it is on. */
@Composable
private fun SearchOptionItem(
    label: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalPageColors.current
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (on) "✓" else "",
                    color = c.ac,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    text = label,
                    color = c.tx,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            }
        },
        onClick = onToggle,
    )
}
