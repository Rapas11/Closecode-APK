package com.closecode.lang

/**
 * Pure text transforms used by the quick-fix actions. These deliberately do not touch the lexer or
 * symbol table: an edit has to be predictable and independently testable, because it rewrites the
 * user's file. Every function returns the input unchanged when the edit does not apply.
 */
object SourceEdits {

    /** The final identifier segment of an fqn: `android.widget.TextView` -> `TextView`. */
    fun simpleNameOf(fqn: String): String =
        fqn.trim().substringAfterLast('.').replace("`", "")

    /** The package of an fqn, or "" when it sits in the default package. */
    fun packageOf(fqn: String): String =
        fqn.trim().substringBeforeLast('.', "").replace("`", "")

    private data class ExistingImport(val fqn: String, val lineIndex: Int)

    private fun existingImports(lines: List<String>): List<ExistingImport> {
        val out = ArrayList<ExistingImport>()
        lines.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            if (!trimmed.startsWith("import ")) return@forEachIndexed
            var body = trimmed.removePrefix("import").trim().removeSuffix(";").trim()
            body = body.substringBefore(" as ").trim().replace("`", "")
            if (body.isEmpty() || body.endsWith("*")) return@forEachIndexed
            out.add(ExistingImport(body, index))
        }
        return out
    }

    private fun eolOf(source: String): String = if (source.contains("\r\n")) "\r\n" else "\n"


    /**
     * Re-indents only the lines a selection touches, leaving the rest of the file exactly as it was.
     *
     * The whole-file formatter cannot be pointed at a selection: it rebuilds the file from brace depth,
     * so running it on part of one would re-indent every line outside the selection too — which is the
     * opposite of what a reader who selected three lines asked for, and impossible to undo one keystroke
     * at a time on a phone.
     *
     * The depth comes from the braces *before* the selection, so a block selected in the middle of a
     * function keeps the function's own level rather than jumping to the margin. The selection's own
     * braces are then read as they are re-indented, which is what makes a selected block's body come out
     * right rather than flat.
     */
    /**
     * Whether a selection covers whole lines: it starts at a line start and ends at a line end.
     * Decides whether Format touches just the selection or the whole file — a partial line is
     * never reformatted on its own, because re-indenting half a line would move code the reader
     * did not select.
     */
    fun selectionCoversWholeLines(source: String, from: Int, to: Int): Boolean {
        if (from >= to) return false
        val startOk = from <= 0 || source.getOrNull(from - 1) == '\n'
        // Ending at a line end, at the file end, or just past a newline: all three leave no
        // partial line inside the selection.
        val endOk = to >= source.length ||
            source.getOrNull(to) == '\n' ||
            source.getOrNull(to - 1) == '\n'
        return startOk && endOk
    }

    fun formatRange(source: String, from: Int, to: Int, tabSize: Int = 2): String {
        if (source.isEmpty() || from >= to) return source
        val eol = eolOf(source)
        val lines = source.split(eol).toMutableList()
        val startLine = source.substring(0, from.coerceIn(0, source.length)).count { it == '\n' }
        val endOffset = to.coerceIn(0, source.length)
        val endLine = source.substring(0, endOffset).count { it == '\n' }
        if (startLine >= lines.size) return source

        // Depth the selection starts at: the braces already closed before it.
        var depth = 0
        for (i in 0 until startLine) {
            depth += braceDelta(lines[i])
        }
        // A selection that starts on a line whose closing brace it includes should begin one level out.
        if (lines[startLine].trimStart().startsWith("}")) depth = (depth - 1).coerceAtLeast(0)

        val step = tabSize.coerceAtLeast(1)
        for (i in startLine..minOf(endLine, lines.size - 1)) {
            val raw = lines[i]
            val trimmed = raw.trimStart()
            val ownDelta = braceDelta(raw)
            // A closing brace at the start of a line belongs to the level it closes, not the one it opens.
            val lineDepth = if (trimmed.startsWith("}") || trimmed.startsWith(")") || trimmed.startsWith("]")) {
                (depth - 1).coerceAtLeast(0)
            } else {
                depth
            }
            lines[i] = if (trimmed.isEmpty()) {
                raw.trimEnd()
            } else {
                " ".repeat(step * lineDepth) + trimmed.trimEnd()
            }
            depth = (depth + ownDelta).coerceAtLeast(0)
        }
        return lines.joinToString(eol)
    }

    /**
     * How many blocks a line opens minus the ones it closes.
     *
     * Braces only, not parentheses. A `(` never starts a line of code on its own, so counting it
     * added a level for every call and for every `if (` condition — which is how `if (a > 0) {` came
     * to indent its own body two levels deeper than the brace it opened.
     *
     * Strings and comments are skipped for the same reason: a brace inside either is a character,
     * and counting it walks every following line one level to the right.
     */
    private fun braceDelta(line: String): Int {
        var delta = 0
        var i = 0
        var inString = false
        var quote = ' '
        var inLineComment = false
        var blockDepth = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inLineComment -> {
                    inLineComment = false
                }
                blockDepth > 0 -> {
                    if (ch == '/' && i + 1 < line.length && line[i + 1] == '*') blockDepth++
                    if (ch == '*' && i + 1 < line.length && line[i + 1] == '/') blockDepth--
                }
                inString -> {
                    if (ch == '\\') i++
                    else if (ch == quote) inString = false
                }
                ch == '/' && i + 1 < line.length && line[i + 1] == '/' -> inLineComment = true
                ch == '/' && i + 1 < line.length && line[i + 1] == '*' -> blockDepth++
                ch == '"' || ch == '\'' -> {
                    inString = true
                    quote = ch
                }
                ch == '{' -> delta++
                ch == '}' -> delta--
            }
            i++
        }
        return delta
    }

    /**
     * Inserts `import fqn`, or returns [source] untouched when that name is already bound.
     *
     * Refuses to add a second binding for a simple name, including when the existing import is
     * aliased. Two imports of the same name resolve ambiguously or fail to compile, so a no-op is
     * always better than producing a broken file.
     */
    fun addImport(source: String, fqn: String): String {
        val target = fqn.trim().replace("`", "")
        if (target.isEmpty()) return source
        val simple = simpleNameOf(target)
        if (simple.isEmpty()) return source

        val eol = eolOf(source)
        val lines = source.split(eol)
        val existing = existingImports(lines)
        if (existing.any { it.fqn == target || simpleNameOf(it.fqn) == simple }) {
            return source
        }

        val rebuilt = lines.toMutableList()

        // Append after the last import so the existing block stays together.
        if (existing.isNotEmpty()) {
            rebuilt.add(existing.maxOf { it.lineIndex } + 1, "import $target")
            return rebuilt.joinToString(eol)
        }

        // Otherwise sit after the package declaration and its blank lines, keeping one blank
        // line on either side of the new import block.
        val packageLine = rebuilt.indexOfFirst { it.trim().startsWith("package ") }
        if (packageLine >= 0) {
            var at = packageLine + 1
            while (at < rebuilt.size && rebuilt[at].isBlank()) at++
            rebuilt.add(at, "import $target")
            val after = at + 1
            if (after >= rebuilt.size || (!rebuilt[after].isBlank() && rebuilt[after].isNotEmpty())) {
                rebuilt.add(after, "")
            }
            return rebuilt.joinToString(eol)
        }

        rebuilt.add(0, "import $target")
        rebuilt.add(1, "")
        return rebuilt.joinToString(eol)
    }

    /** Removes `import fqn`, including an aliased form, or returns [source] untouched. */
    fun removeImport(source: String, fqn: String): String {
        val target = fqn.trim().replace("`", "")
        if (target.isEmpty()) return source
        val eol = eolOf(source)
        val lines = source.split(eol)
        val match = existingImports(lines).firstOrNull { it.fqn == target } ?: return source
        val rebuilt = lines.toMutableList()
        rebuilt.removeAt(match.lineIndex)
        return rebuilt.joinToString(eol)
    }

    /**
     * Caret offset just past the line holding `import fqn` after [addImport] ran, so the cursor
     * follows the new import instead of jumping to the end of the file. Null when nothing changed.
     */
    fun caretAfterAddImport(sourceBefore: String, fqn: String): Int? {
        val target = fqn.trim().replace("`", "")
        val updated = addImport(sourceBefore, target)
        if (updated == sourceBefore) return null
        val eol = eolOf(updated)
        val lines = updated.split(eol)
        val index = lines.indexOfFirst { it.trim() == "import $target" }
        if (index < 0) return null
        var offset = 0
        for (i in 0..index) offset += lines[i].length + eol.length
        return offset
    }

    // ---- line actions ----------------------------------------------------------------
    // These back the code actions in the quick-fix list. Like everything else here they are pure,
    // refuse out-of-range lines, and leave the source untouched when they do not apply.

    /** Adds `// ` to a line, or removes it when the line is already commented. */
    fun toggleLineComment(source: String, line: Int): String {
        val eol = eolOf(source)
        val lines = source.split(eol)
        val index = line - 1
        if (index !in lines.indices) return source
        val text = lines[index]
        val indent = text.takeWhile { it == ' ' || it == '\t' }
        val body = text.substring(indent.length)
        val updated = when {
            body.startsWith("// ") -> indent + body.removePrefix("// ")
            body.startsWith("//") -> indent + body.removePrefix("//").removePrefix(" ")
            else -> "$indent// $body"
        }
        return lines.toMutableList().also { it[index] = updated }.joinToString(eol)
    }

    /**
     * How many lines hold real content. A text ending in a newline yields a final empty entry that
     * is not a line the user can see or move, so line actions must not treat it as one.
     */
    private fun realLineCount(lines: List<String>): Int =
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines.size - 1 else lines.size

    /** Swaps a line with the one above it. The first line has nothing above it. */
    fun moveLineUp(source: String, line: Int): String {
        val eol = eolOf(source)
        val lines = source.split(eol).toMutableList()
        val index = line - 1
        if (index <= 0 || index >= realLineCount(lines)) return source
        val above = lines[index - 1]
        lines[index - 1] = lines[index]
        lines[index] = above
        return lines.joinToString(eol)
    }

    /** Swaps a line with the one below it. The last line of real content has nothing below it. */
    fun moveLineDown(source: String, line: Int): String {
        val eol = eolOf(source)
        val lines = source.split(eol).toMutableList()
        val index = line - 1
        if (index < 0 || index >= realLineCount(lines) - 1) return source
        val below = lines[index + 1]
        lines[index + 1] = lines[index]
        lines[index] = below
        return lines.joinToString(eol)
    }

    /** Caret offset that keeps the same column on the same text after a line swap. */
    fun caretAfterLineMove(sourceBefore: String, line: Int, column: Int, down: Boolean): Int {
        val eol = eolOf(sourceBefore)
        val lines = sourceBefore.split(eol)
        val index = (line - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val targetRow = if (down) index + 1 else index - 1
        if (targetRow !in lines.indices) {
            var at = 0
            for (i in 0..index) at += lines[i].length + eol.length
            return (at - eol.length).coerceIn(0, sourceBefore.length)
        }
        var at = 0
        for (i in 0 until targetRow) at += lines[i].length + eol.length
        return (at + (column - 1).coerceAtLeast(0)).coerceIn(0, sourceBefore.length)
    }

    /**
     * Spec 6.6, fix for E0101: delete the unresolved token that the diagnostic points at.
     *
     * The caret lands where the token started, so typing a replacement continues in place. The
     * offset is clamped because a diagnostic can outlive the edit that produced it.
     */
    fun removeToken(source: String, offset: Int, length: Int): String? {
        val from = offset.coerceIn(0, source.length)
        val to = (offset + length).coerceIn(from, source.length)
        if (to <= from) return null
        return source.substring(0, from) + source.substring(to)
    }

    /**
     * Spec 6.6, fix for E0102: put a semicolon at the end of the line. A line that already ends in
     * one is returned unchanged so the action is a no-op rather than producing "; ;".
     */
    fun addSemicolon(source: String, line: Int): String? {
        val eol = eolOf(source)
        val lines = source.split(eol).toMutableList()
        val index = line - 1
        if (index !in lines.indices) return null
        val current = lines[index]
        if (current.trimEnd().endsWith(";")) return null
        lines[index] = current.trimEnd() + ";"
        return lines.joinToString(eol)
    }

    /**
     * Removes trailing whitespace without changing indentation, line breaks, or content.
     * Used for file kinds without a structural formatter.
     */
    fun normalizeTrailingWhitespace(source: String): String {
        if (source.isEmpty()) return source
        val eol = eolOf(source)
        return source.split(eol).joinToString(eol) { it.trimEnd() }
    }

    /**
     * Formats Kotlin source by normalizing indentation to two spaces per nesting level.
     *
     * This is intentionally conservative: blank lines are preserved, comment text is not
     * reworded, raw-string bodies are left byte-for-byte alone, and braces inside strings or
     * comments do not change the nesting level. It fixes layout, not syntax.
     */
    fun formatKotlin(source: String): String {
        if (source.isEmpty()) return source
        val eol = eolOf(source)
        val lines = source.split(eol)
        val out = ArrayList<String>(lines.size)
        var depth = 0
        var commentDepth = 0
        var inRawString = false

        for (raw in lines) {
            if (inRawString) {
                out.add(raw)
                // Raw-string bodies are preserved exactly. Only check whether this line closes
                // the string; braces inside it must not change the surrounding nesting level.
                if (raw.contains("\"\"\"")) inRawString = false
                continue
            }
            if (commentDepth > 0) {
                out.add(raw.trimEnd())
                val scan = scanKotlinLine(raw, 0, commentDepth, depth)
                commentDepth = scan.commentDepth
                depth = scan.nextDepth
                continue
            }
            if (raw.isBlank()) {
                out.add("")
                continue
            }
            val firstCode = firstCodeIndex(raw)
            val closers = leadingClosers(raw, firstCode)
            val lineDepth = (depth - closers).coerceAtLeast(0)
            val body = raw.substring(firstCode).trimEnd()
            out.add("  ".repeat(lineDepth) + body)
            val scan = scanKotlinLine(raw, 0, 0, depth)
            commentDepth = scan.commentDepth
            depth = scan.nextDepth
            inRawString = scan.inRawString
        }
        return out.joinToString(eol)
    }

    private data class KotlinLineScan(
        val nextDepth: Int,
        val commentDepth: Int,
        val inRawString: Boolean,
    )

    private fun firstCodeIndex(line: String): Int {
        var i = 0
        var blockDepth = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == ' ' || ch == '\t') {
                i++
                continue
            }
            if (blockDepth > 0) {
                if (line.startsWith("/*", i)) {
                    blockDepth++
                    i += 2
                    continue
                }
                if (line.startsWith("*/", i)) {
                    blockDepth--
                    i += 2
                    continue
                }
                i++
                continue
            }
            if (line.startsWith("//", i)) return line.length
            if (line.startsWith("/*", i)) {
                blockDepth++
                i += 2
                continue
            }
            return i
        }
        return line.length
    }

    private fun leadingClosers(line: String, start: Int): Int {
        var i = start
        var closers = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == ' ' || ch == '\t') {
                i++
                continue
            }
            if (ch == '}' || ch == ')' || ch == ']') {
                closers++
                i++
                continue
            }
            break
        }
        return closers
    }

    private fun scanKotlinLine(
        line: String,
        start: Int,
        startCommentDepth: Int,
        startDepth: Int,
    ): KotlinLineScan {
        var i = start
        var blockDepth = startCommentDepth
        var depthDelta = 0
        var inRawString = false
        var inDouble = false
        var inSingle = false
        var inBacktick = false
        var escaped = false

        while (i < line.length) {
            if (inRawString) {
                if (line.startsWith("\"\"\"", i)) {
                    inRawString = false
                    i += 3
                } else {
                    i++
                }
                continue
            }
            val ch = line[i]
            if (escaped) {
                escaped = false
                i++
                continue
            }
            if (inDouble) {
                if (ch == '\\') escaped = true
                else if (ch == '"') inDouble = false
                i++
                continue
            }
            if (inSingle) {
                if (ch == '\\') escaped = true
                else if (ch == '\'') inSingle = false
                i++
                continue
            }
            if (inBacktick) {
                if (ch == '`') inBacktick = false
                i++
                continue
            }
            if (blockDepth > 0) {
                if (line.startsWith("/*", i)) {
                    blockDepth++
                    i += 2
                } else if (line.startsWith("*/", i)) {
                    blockDepth--
                    i += 2
                } else {
                    i++
                }
                continue
            }
            if (line.startsWith("//", i)) break
            if (line.startsWith("/*", i)) {
                blockDepth++
                i += 2
                continue
            }
            if (line.startsWith("\"\"\"", i)) {
                inRawString = true
                i += 3
                continue
            }
            when (ch) {
                '"' -> inDouble = true
                '\'' -> inSingle = true
                '`' -> inBacktick = true
                '{', '(', '[' -> depthDelta++
                '}', ')', ']' -> depthDelta--
            }
            i++
        }
        return KotlinLineScan(
            nextDepth = (startDepth + depthDelta).coerceAtLeast(0),
            commentDepth = blockDepth.coerceAtLeast(0),
            inRawString = inRawString,
        )
    }
}
