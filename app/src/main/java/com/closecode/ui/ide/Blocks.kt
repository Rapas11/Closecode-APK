package com.closecode.ui.ide

/**
 * The block editor's view of a source file.
 *
 * Everything here is pure and line based, on purpose: the block editor never owns a copy of the
 * text, it only ever *reorders whole line ranges*. Reordering a partition of consecutive ranges is
 * always lossless, so a construct the model does not understand — a string containing a brace, an
 * annotation, a build script — comes back byte for byte instead of being rewritten by a pretty
 * printer that has never seen it.
 */

/** What a block was cut from. Drives the glyph and the weight of the row, never the logic. */
enum class BlockKind { Declaration, Function, Statement }

/**
 * One block. [startLine] and [endLine] are absolute, 0-based, and inclusive, and together with
 * [children] they describe the block completely — no text is kept here.
 *
 * [id] is the block's path from the root ("0", "0-2", "0-2-1"), which makes it a stable key for
 * both the list and the collapse state even when two blocks share a title.
 */
data class BlockNode(
    val id: String,
    val startLine: Int,
    val endLine: Int,
    val depth: Int,
    val kind: BlockKind,
    val title: String,
    val children: List<BlockNode>,
) {
    val isContainer: Boolean get() = children.isNotEmpty()
    val lineCount: Int get() = endLine - startLine + 1
}

/**
 * Brace and parenthesis depth at the start of every line, plus a final entry holding the depth the
 * file ends on. Sizes are always `lineCount + 1`.
 *
 * The scan is aware of line comments, block comments, char literals, escaped strings and Kotlin's
 * raw strings, because a `}` inside any of those must not close a block. String templates are
 * deliberately left inside the string: a `${…}` is an expression in the *content*, not structure.
 */
internal class LineDepths(
    val brace: IntArray,
    val paren: IntArray,
) {
    val lineCount: Int get() = brace.size - 1
}

private const val ST_CODE = 0
private const val ST_LINE_COMMENT = 1
private const val ST_BLOCK_COMMENT = 2
private const val ST_STRING = 3
private const val ST_CHAR = 4
private const val ST_RAW = 5

internal fun computeDepths(text: String): LineDepths {
    val brace = ArrayList<Int>(64)
    val paren = ArrayList<Int>(64)
    var b = 0
    var p = 0
    var state = ST_CODE
    var blockNesting = 0
    var i = 0
    val n = text.length
    brace.add(0)
    paren.add(0)

    while (i < n) {
        val ch = text[i]
        when (state) {
            ST_CODE -> when {
                ch == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    state = ST_LINE_COMMENT
                    i += 2
                }

                ch == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    state = ST_BLOCK_COMMENT
                    blockNesting = 1
                    i += 2
                }

                ch == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    state = ST_RAW
                    i += 3
                }

                ch == '"' -> {
                    state = ST_STRING
                    i += 1
                }

                ch == '\'' -> {
                    state = ST_CHAR
                    i += 1
                }

                else -> {
                    when (ch) {
                        '{' -> b++
                        '}' -> b--
                        '(' -> p++
                        ')' -> p--
                        '\n' -> {
                            brace.add(b)
                            paren.add(p)
                        }
                    }
                    i += 1
                }
            }

            ST_LINE_COMMENT -> {
                if (ch == '\n') {
                    brace.add(b)
                    paren.add(p)
                    state = ST_CODE
                }
                i += 1
            }

            ST_BLOCK_COMMENT -> {
                if (ch == '/' && i + 1 < n && text[i + 1] == '*') {
                    blockNesting++
                    i += 2
                } else if (ch == '*' && i + 1 < n && text[i + 1] == '/') {
                    blockNesting--
                    i += 2
                    if (blockNesting <= 0) state = ST_CODE
                } else {
                    if (ch == '\n') {
                        brace.add(b)
                        paren.add(p)
                    }
                    i += 1
                }
            }

            ST_STRING -> when {
                ch == '\\' && i + 1 < n -> i += 2
                ch == '"' -> {
                    state = ST_CODE
                    i += 1
                }

                ch == '\n' -> {
                    brace.add(b)
                    paren.add(p)
                    i += 1
                }

                else -> i += 1
            }

            ST_CHAR -> when {
                ch == '\\' && i + 1 < n -> i += 2
                ch == '\'' -> {
                    state = ST_CODE
                    i += 1
                }

                ch == '\n' -> {
                    brace.add(b)
                    paren.add(p)
                    i += 1
                }

                else -> i += 1
            }

            ST_RAW -> when {
                ch == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    state = ST_CODE
                    i += 3
                }

                ch == '\n' -> {
                    brace.add(b)
                    paren.add(p)
                    i += 1
                }

                else -> i += 1
            }
        }
    }

    // One entry is recorded per newline, which is the depth at the start of the following line.
    // The file still needs one more slot for the depth after its last line, and that slot is
    // required whether or not the file ends with a newline.
    brace.add(b)
    paren.add(p)
    return LineDepths(brace.toIntArray(), paren.toIntArray())
}

/** Characters that make the *next* line a continuation of this one rather than a new block. */
private val ContinuationChars = "+-*/%=&|,.<>?:!~^"

private fun isContinuation(line: String): Boolean {
    val t = line.trim().trimEnd() // trailing whitespace only
    if (t.isEmpty()) return false
    if (t.endsWith("&&") || t.endsWith("||") || t.endsWith("->") || t.endsWith("::")) return true
    return t.last() in ContinuationChars
}

private fun isMeaningful(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    if (t.startsWith("//")) return false
    if (t.startsWith("/*")) return false
    if (t.startsWith("*")) return false
    if (t.startsWith("#!")) return false
    // A lone closing brace is structure, never a block of its own.
    if (t.startsWith("}")) return false
    return true
}

private val DeclarationKeywords = setOf(
    "class", "object", "interface", "fun", "val", "var", "package", "import",
    "enum", "data", "sealed", "annotation", "companion", "typealias",
)

private val ModifierWords = setOf(
    "public", "private", "internal", "protected", "open", "abstract", "override",
    "suspend", "inline", "operator", "infix", "external", "actual", "expect",
    "final", "const", "lateinit", "tailrec", "vararg", "noinline", "crossinline",
    "reified", "inner", "value", "sealed", "data", "enum",
)

/**
 * The declaration keyword at the head of a line, after annotations and modifiers are skipped.
 * Empty when the line is an ordinary statement.
 */
internal fun declarationKeywordOf(line: String): String {
    var rest = line.trim()
    // Drop leading annotations, including ones with arguments: `@Composable(content = {})`.
    while (rest.startsWith("@")) {
        rest = when {
            rest.contains(' ') -> rest.substringAfter(' ').trim()
            else -> ""
        }
    }
    while (true) {
        val space = rest.indexOf(' ')
        if (space < 0) break
        val word = rest.substring(0, space)
        if (word in ModifierWords) {
            rest = rest.substring(space + 1).trim()
        } else {
            break
        }
    }
    val head = rest.takeWhile { it.isLetterOrDigit() || it == '_' }
    return if (head in DeclarationKeywords) head else ""
}

private fun kindOfLine(line: String): BlockKind {
    val keyword = declarationKeywordOf(line)
    return when (keyword) {
        "fun" -> BlockKind.Function
        "class", "object", "interface", "enum", "data", "sealed", "annotation",
        "companion", "typealias", "val", "var", "package", "import",
        -> BlockKind.Declaration

        else -> BlockKind.Statement
    }
}

/** The label a block shows: its declaration line with the trailing brace and excess space removed. */
internal fun titleOf(lines: List<String>, start: Int, end: Int): String {
    // Annotations above the declaration are skipped rather than prefixed, so the row reads
    // "fun Greeting()" instead of "@Composable @Preview".
    var index = start
    while (index <= end) {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty() || trimmed.startsWith("@") ||
            trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
        ) {
            index++
        } else {
            break
        }
    }
    val base = if (index <= end) lines[index].trim() else lines[start].trim()
    return base
        .removeSuffix("{")
        .removeSuffix("=")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(80)
        .ifEmpty { lines[start].trim().take(80) }
}

/**
 * The deepest nesting the editor draws: declaration, member, statement. Anything further would be
 * a fold inside a fold, which on a phone is a column of slivers rather than an outline.
 */
private const val MAX_NESTING = 2

/**
 * Cuts [source] into a tree of blocks.
 *
 * A line starts a block when its brace depth is exactly the depth being scanned for, it is not a
 * continuation of the line above, and it is not a comment or a lone `}`. A block then runs to just
 * before the next start, and a block that opens a brace grows children from between its own
 * braces. The three rules together guarantee that concatenating every block's lines reproduces the
 * file exactly, which is what makes drag-and-drop safe on code the model does not parse.
 */
fun buildBlockTree(source: String): List<BlockNode> {
    val lines = source.split('\n')
    if (lines.isEmpty()) return emptyList()
    val depths = computeDepths(source)
    var roots = segment(lines, depths, 0, lines.lastIndex, 0, "", allowChildren = true)
    if (roots.isEmpty()) {
        // A file with content but no recognisable declaration still deserves one block; an empty
        // outline for a non-empty file reads as "this file is broken".
        return if (lines.all { it.isBlank() }) {
            emptyList()
        } else {
            listOf(
                BlockNode(
                    id = "0",
                    startLine = 0,
                    endLine = lines.lastIndex,
                    depth = 0,
                    kind = BlockKind.Statement,
                    title = titleOf(lines, 0, lines.lastIndex),
                    children = emptyList(),
                ),
            )
        }
    }
    // Lead-in comments and blank lines are folded into the first block, so concatenating every
    // root reproduces the file exactly, header and all.
    roots = listOf(roots[0].copy(startLine = 0)) + roots.drop(1)
    return roots
}

private fun segment(
    lines: List<String>,
    depths: LineDepths,
    from: Int,
    to: Int,
    targetDepth: Int,
    idPrefix: String,
    allowChildren: Boolean,
): List<BlockNode> {
    if (to < from) return emptyList()

    val starts = ArrayList<Int>()
    for (i in from..to) {
        if (depths.brace[i] != targetDepth) continue
        if (depths.paren[i] != 0) continue
        if (!isMeaningful(lines[i])) continue
        if (i > from && isContinuation(lines[i - 1])) continue
        // An annotation belongs to whatever follows it, not to itself.
        if (i > from && lines[i - 1].trim().startsWith("@") && lines[i - 1].trim().isNotEmpty()) continue
        starts.add(i)
    }
    if (starts.isEmpty()) return emptyList()

    val nodes = ArrayList<BlockNode>(starts.size)
    for ((order, start) in starts.withIndex()) {
        val nextStart = starts.getOrNull(order + 1) ?: (to + 1)
        val extentEnd = minOf(nextStart - 1, to)
        val base = depths.brace[start]

        var openLine = -1
        for (k in start..extentEnd) {
            if (depths.brace[k + 1] > depths.brace[k] && depths.brace[k + 1] > base) {
                openLine = k
                break
            }
        }

        // The block runs to the end of its extent rather than to the closing brace: the blank
        // lines and comments after a `}` belong to the block they follow, which is what keeps
        // siblings contiguous and makes a reorder lossless.
        val endLine = extentEnd
        var children: List<BlockNode> = emptyList()
        if (openLine >= 0) {
            var closeLine = -1
            for (k in (openLine + 1)..extentEnd) {
                if (depths.brace[k + 1] <= base) {
                    closeLine = k
                    break
                }
            }
            if (closeLine >= 0 && allowChildren && targetDepth < MAX_NESTING) {
                children = segment(
                    lines = lines,
                    depths = depths,
                    from = openLine + 1,
                    to = closeLine - 1,
                    targetDepth = base + 1,
                    idPrefix = "$idPrefix$order-",
                    allowChildren = true,
                )
            }
        }

        nodes.add(
            BlockNode(
                id = "$idPrefix$order",
                startLine = start,
                endLine = endLine,
                depth = targetDepth,
                kind = kindOfLine(lines[start]),
                title = titleOf(lines, start, endLine),
                children = children,
            ),
        )
    }
    return nodes
}

/** Finds the node a path points at. Empty path is the file itself and returns null. */
fun nodeAt(tree: List<BlockNode>, path: List<Int>): BlockNode? {
    var nodes = tree
    var node: BlockNode? = null
    for (index in path) {
        node = nodes.getOrNull(index) ?: return null
        nodes = node.children
    }
    return node
}

/**
 * Moves the element at [from] to sit directly after the element at [after] (both indices into the
 * list *before* the move), where `after = -1` means "put it at the top".
 *
 * The "after" wording matches how a drop is reported: the finger crossed a row's lower half, so
 * the block belongs under that row rather than at some abstract index.
 */
internal fun <T> MutableList<T>.moveAfter(from: Int, after: Int) {
    if (from !in indices) return
    if (after == from) return
    if (after >= 0 && after !in indices) return
    val item = removeAt(from)
    val insertAt = when {
        after < 0 -> 0
        else -> (if (after > from) after - 1 else after) + 1
    }
    add(insertAt.coerceIn(0, size), item)
}

/**
 * Reorders the children of the container [path] points at (empty path = the file itself) and
 * returns the rewritten source. [after] is the sibling the moved block should follow, or -1 to
 * put it first.
 *
 * The container is rebuilt from four pieces — everything before it, its header, its permuted
 * children, its footer and everything after it — so only whole line ranges move. No line is ever
 * edited, which is why a move is exact even for a block whose text this model does not parse.
 */
fun reorderBlocks(
    source: String,
    tree: List<BlockNode>,
    path: List<Int>,
    from: Int,
    after: Int,
): String {
    if (from == after) return source
    val lines = source.split('\n')
    val containerStart: Int
    val containerEnd: Int
    val children: List<BlockNode>

    if (path.isEmpty()) {
        children = tree
        if (children.isEmpty()) return source
        containerStart = 0
        containerEnd = lines.lastIndex
    } else {
        val node = nodeAt(tree, path) ?: return source
        children = node.children
        if (children.isEmpty()) return source
        containerStart = node.startLine
        containerEnd = node.endLine
    }

    if (from !in children.indices) return source
    if (after >= children.size) return source

    val header = lines.subList(containerStart, children.first().startLine)
    val footer = lines.subList(children.last().endLine + 1, containerEnd + 1)

    // Blank lines between blocks are separators that belong to their *slot*, not to the block
    // above them. Splitting each child into content and trailing gap and leaving the gaps in place
    // means a moved block still lands under a blank line, which is what the file looked like
    // before the move — and every line is still only ever relocated, never rewritten.
    val contents = ArrayList<List<String>>(children.size)
    val gaps = ArrayList<List<String>>(children.size)
    for (child in children) {
        var contentEnd = child.endLine
        while (contentEnd > child.startLine && lines[contentEnd].isBlank()) contentEnd--
        contents.add(lines.subList(child.startLine, contentEnd + 1).toList())
        gaps.add(lines.subList(contentEnd + 1, child.endLine + 1).toList())
    }

    val order = (0 until children.size).toMutableList().apply { moveAfter(from, after) }
    val body = ArrayList<String>(lines.size)
    for (slot in order.indices) {
        body.addAll(contents[order[slot]])
        body.addAll(gaps[slot])
    }

    return (
        lines.subList(0, containerStart) +
            header +
            body +
            footer +
            lines.subList(containerEnd + 1, lines.size)
        ).joinToString("\n")
}

/** The parent path of a path: `listOf(0, 2, 1)` → `listOf(0, 2)`; root stays root. */
internal fun parentPath(path: List<Int>): List<Int> =
    if (path.isEmpty()) emptyList() else path.dropLast(1)

/** One visible row of the block outline: the block, its path, and how far in it sits. */
data class FlatBlock(
    val node: BlockNode,
    val path: List<Int>,
)

/**
 * Depth-first list of everything that should be drawn, honouring [collapsed]. Collapsing a block
 * hides its subtree but never its siblings, so the outline still covers the whole file.
 */
fun flattenBlocks(
    tree: List<BlockNode>,
    collapsed: Set<String>,
    path: List<Int> = emptyList(),
    out: MutableList<FlatBlock> = mutableListOf(),
): List<FlatBlock> {
    tree.forEachIndexed { index, node ->
        val childPath = path + index
        out.add(FlatBlock(node, childPath))
        if (!collapsed.contains(node.id) && node.children.isNotEmpty()) {
            flattenBlocks(node.children, collapsed, childPath, out)
        }
    }
    return out
}

/**
 * One entry in the "add a block" sheet: the label it is filed under, the lines it writes, and
 * where in those lines the caret belongs afterwards.
 *
 * The caret target is a line/column pair rather than an offset, because the offset depends on the
 * indent the insertion lands at and only the insertion knows that.
 */
data class BlockTemplate(
    val id: String,
    val label: String,
    val lines: List<String>,
    val caretLine: Int,
    val caretColumn: Int,
)

/**
 * What the sheet offers for a statement slot.
 *
 * Deliberately the handful of shapes every supported language parses the same way, so a template
 * never has to be filtered by file type to avoid writing something the language would reject. The
 * placeholder words (`condition`, `name`, `value`) are plain text on purpose: they are what the
 * caret lands on so the reader can type over them, not identifiers the compiler is meant to see.
 */
val StatementSlots: List<BlockTemplate> = listOf(
    BlockTemplate("if", "if", listOf("if (condition) {", "    // TODO", "}"), 0, 4),
    BlockTemplate("for", "for", listOf("for (item in items) {", "    // TODO", "}"), 0, 5),
    BlockTemplate("while", "while", listOf("while (condition) {", "    // TODO", "}"), 0, 7),
    BlockTemplate("when", "when", listOf("when (value) {", "    else -> {", "    }", "}"), 0, 6),
    BlockTemplate("try", "try", listOf("try {", "    // TODO", "} catch (e: Exception) {", "}"), 1, 7),
    BlockTemplate("fun", "fun", listOf("fun name() {", "    // TODO", "}"), 0, 4),
    BlockTemplate("val", "val", listOf("val name = value"), 0, 4),
    BlockTemplate("comment", "comment", listOf("// "), 0, 3),
)

/**
 * Writes [template] into [source] at the caret's line and returns the new text plus the offset the
 * caret should move to.
 *
 * The block always takes whole lines: it lands at the *start* of the caret's line, indented to that
 * line, so the line the caret was sitting on keeps its own content and a statement can never be
 * spliced into the middle of an expression. With the caret on a blank line — the usual case after
 * pressing Enter — that puts the new block exactly where the caret already was.
 */
internal fun insertBlockAt(source: String, caret: Int, template: BlockTemplate): Pair<String, Int> {
    if (template.lines.isEmpty()) return source to caret.coerceIn(0, source.length)
    val at = caret.coerceIn(0, source.length)
    // `lastIndexOf('\n', -1)` is -1, which is exactly the line-start the first line needs.
    val lineStart = source.lastIndexOf('\n', at - 1) + 1
    val found = source.indexOf('\n', at)
    val lineEnd = if (found < 0) source.length else found
    val line = source.substring(lineStart, lineEnd)
    val indent = line.takeWhile { it == ' ' || it == '\t' }

    val body = template.lines.joinToString("\n") { indent + it }
    val next = source.substring(0, lineStart) + body + "\n" + source.substring(lineStart)

    val targetLine = template.lines.getOrNull(template.caretLine) ?: template.lines.first()
    val above = template.lines.take(template.caretLine).sumOf { it.length + 1 }
    val column = template.caretColumn.coerceIn(0, targetLine.length)
    val caretAt = lineStart + indent.length + above + column
    return next to caretAt.coerceIn(0, next.length)
}
