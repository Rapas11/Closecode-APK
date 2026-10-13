package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.PageColors
import com.closecode.ui.theme.Sans

/** Row height. Fixed so a drop can be decided from a rectangle rather than from measured text. */
private val BlockRowHeight = 42.dp

/** How far each nesting level is indented. */
private val BlockIndent = 16.dp

private enum class DropEdge { Top, Bottom }

/** Where the block under the finger would land: which row it is, and which half it crossed. */
private data class DropHint(val rowId: String, val edge: DropEdge)

/**
 * The block editor.
 *
 * A hold and drag on a block picks it up, a line shows where it would land, and releasing writes
 * the new order back through [onChange]. Only siblings are ever targets, because moving a block
 * across a brace would change what it is rather than where it is — and every drop is a permutation
 * of whole line ranges, so the source on screen and the source in the file stay identical.
 */
@Composable
fun IdeBlockPanel(
    fileName: String,
    language: String,
    source: String,
    onChange: (String) -> Unit,
    onAddBlock: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current

    val tree = remember(source) { buildBlockTree(source) }
    // Collapse state is keyed on the source so a drop cannot leave a stale subtree hidden: paths
    // are the block ids, and a move renumbers them.
    var collapsed by remember(source) { mutableStateOf(setOf<String>()) }
    val rows = remember(tree, collapsed) { flattenBlocks(tree, collapsed) }

    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var hint by remember { mutableStateOf<DropHint?>(null) }
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }

    fun clearDrag() {
        draggedId = null
        hint = null
    }

    fun updateHint() {
        val id = draggedId ?: run {
            hint = null
            return
        }
        val dragIndex = rows.indexOfFirst { it.node.id == id }
        if (dragIndex < 0) {
            hint = null
            return
        }
        val parent = parentPath(rows[dragIndex].path)
        val candidate = rows.firstOrNull { row ->
            row.node.id != id && rowBounds[row.node.id]?.containsY(dragY) == true
        }
        val rect = candidate?.let { rowBounds[it.node.id] }
        hint = when {
            candidate == null || rect == null -> null
            // A row in another subtree: the finger has left the family this block may join.
            parentPath(candidate.path) != parent -> null
            dragY < rect.center.y -> DropHint(candidate.node.id, DropEdge.Top)
            else -> DropHint(candidate.node.id, DropEdge.Bottom)
        }
    }

    fun commitDrag() {
        val id = draggedId
        val target = hint
        clearDrag()
        if (id == null || target == null) return
        val dragIndex = rows.indexOfFirst { it.node.id == id }
        val targetIndex = rows.indexOfFirst { it.node.id == target.rowId }
        if (dragIndex < 0 || targetIndex < 0) return
        val dragPath = rows[dragIndex].path
        val targetPath = rows[targetIndex].path
        val parent = parentPath(dragPath)
        if (parent != parentPath(targetPath)) return
        val siblingIndex = targetPath.last()
        val after = if (target.edge == DropEdge.Top) siblingIndex - 1 else siblingIndex
        val next = reorderBlocks(source, tree, parent, dragPath.last(), after)
        if (next != source) onChange(next)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.editor),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(0.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BLOCKS",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "  hold a block, then drag it",
                color = c.mu.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = Sans,
            )
            Box(Modifier.weight(1f))
            Text(
                text = fileName,
                color = c.ac,
                fontSize = 12.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            // The one way in for a file with nothing to outline yet, and a shortcut for a file
            // that has plenty: either way, it is the same sheet writing the same source.
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(c.panel2)
                    .border(1.dp, c.ln, RoundedCornerShape(7.dp))
                    .clickable(onClick = onAddBlock)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "+ Block",
                    color = c.tx,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }

        if (rows.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (language.isBlank()) {
                        "Nothing to block out yet."
                    } else {
                        "This file can't be projected to blocks yet."
                    },
                    color = c.mu,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.panel2)
                        .border(1.dp, c.ac, RoundedCornerShape(8.dp))
                        .clickable(onClick = onAddBlock)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "+ Block",
                        color = c.ac,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.node.id }) { flat ->
                    val node = flat.node
                    val isDragged = node.id == draggedId
                    val dropHere = hint?.rowId == node.id
                    val isCollapsed = node.id in collapsed
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(BlockRowHeight)
                            .onGloballyPositioned { coords ->
                                rowBounds[node.id] = coords.boundsInWindow()
                            }
                            .pointerInput(node.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val rect = rowBounds[node.id]
                                            ?: return@detectDragGesturesAfterLongPress
                                        draggedId = node.id
                                        dragY = rect.top + offset.y
                                        hint = null
                                    },
                                    onDrag = { _, amount ->
                                        if (draggedId == node.id) {
                                            dragY += amount.y
                                            updateHint()
                                        }
                                    },
                                    onDragEnd = { commitDrag() },
                                    onDragCancel = { clearDrag() },
                                )
                            }
                            .background(if (isDragged) c.panel2 else Color.Transparent)
                            .border(
                                width = if (isDragged) 1.dp else 0.dp,
                                color = if (isDragged) c.ac else Color.Transparent,
                                shape = RoundedCornerShape(7.dp),
                            )
                            .drawBehind {
                                if (!dropHere) return@drawBehind
                                val line = 2.dp.toPx()
                                val y = if (hint?.edge == DropEdge.Top) line / 2f else size.height - line / 2f
                                drawLine(
                                    color = c.ac,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = line,
                                )
                            }
                            .padding(start = 12.dp + BlockIndent * node.depth),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(BlockRowHeight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (node.isContainer) {
                                    // The chevron points right when the subtree is folded away and
                                    // down when it is showing.
                                    CcIcon(
                                        CcIcons.Chevron,
                                        if (isCollapsed) c.mu else c.ac,
                                        14.dp,
                                        Modifier.graphicsLayer {
                                            rotationZ = if (isCollapsed) 0f else 90f
                                        },
                                    )
                                }
                            }

                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.panel2),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = glyphOf(node.kind),
                                    color = colorOf(node.kind, c),
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Text(
                                text = node.title,
                                color = if (isDragged) c.tx else c.tx.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                fontFamily = Mono,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 9.dp),
                            )

                            if (node.isContainer) {
                                Text(
                                    text = "${node.children.size}",
                                    color = c.mu,
                                    fontSize = 11.sp,
                                    fontFamily = Mono,
                                )
                            }
                            Text(
                                text = "L${node.startLine + 1}",
                                color = c.mu.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontFamily = Mono,
                                modifier = Modifier.padding(start = 8.dp),
                            )

                            if (node.isContainer) {
                                Box(
                                    Modifier
                                        .padding(start = 4.dp)
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(c.panel2)
                                        .clickable {
                                            collapsed = if (isCollapsed) collapsed - node.id else collapsed + node.id
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (isCollapsed) "+" else "–",
                                        color = c.tx,
                                        fontSize = 14.sp,
                                        fontFamily = Mono,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // The panel never owns a copy of the text: every drop is handed straight back to Code, so
        // the outline and the buffer are the same bytes at all times. Saying so here is what makes
        // the reorder feel safe rather than destructive.
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(c.panel)
                .border(1.dp, c.ln, RoundedCornerShape(0.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(c.ac),
            )
            Text(
                text = "  live projection · synced with Code",
                color = c.mu.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontFamily = Sans,
            )
        }
    }
}

/** Vertical hit test only: the rows run the full width, so the x coordinate carries no meaning. */
private fun Rect.containsY(y: Float): Boolean = y >= top && y <= bottom

private fun glyphOf(kind: BlockKind): String = when (kind) {
    BlockKind.Declaration -> "C"
    BlockKind.Function -> "ƒ"
    BlockKind.Statement -> "›"
}

private fun colorOf(kind: BlockKind, c: PageColors): Color = when (kind) {
    BlockKind.Declaration -> c.ac
    BlockKind.Function -> c.purple
    BlockKind.Statement -> c.mu
}
