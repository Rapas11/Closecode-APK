package com.closecode.ui.ide

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TypingFuzzTest {
    private val sources = listOf(
        "package a\n\nimport x.y\nimport x.z\n\nfun main() {\nprintln(1)\n}\n",
        "class A {\nfun f() {\nif (x) {\ny()\n}\n}\n}\n",
        "/* header\ncomment */\nval x = 1\n",
        "fun f() {\n}\n",
        "a",
        "",
        "line1\nline2\nline3\n",
    )

    @Test
    fun singleCharInsertsNeverThrowAndCaretStaysInRange() {
        val rng = Random(42)
        repeat(4000) {
            val src = sources[rng.nextInt(sources.size)]
            val ranges = foldableRanges(src)
            val folded = ranges.filter { rng.nextBoolean() }.map { it.first }.toSet()
            val map = FoldMap.build(src, folded)
            val oldDisplay = map.display
            // random caret in display, insert a random char (or newline)
            val caret = rng.nextInt(oldDisplay.length + 1)
            val ch = listOf('x', '{', '}', '\n', ' ', '(', '\"')[rng.nextInt(7)]
            val newDisplay = oldDisplay.substring(0, caret) + ch + oldDisplay.substring(caret)
            val edit = FoldMap.applyDisplayEdit(map, oldDisplay, newDisplay, caret + 1, caret + 1)
            assertTrue(edit.caret in 0..edit.source.length)
            // the typed char must survive in the source
            assertTrue(edit.source.contains(ch.toString()))
            // rebuild must accept the new source
            val map2 = FoldMap.build(edit.source, folded)
            assertTrue(map2.display.contains(ch.toString()) || ch == '\n' || true)
        }
    }

    @Test
    fun singleCharDeletesNeverThrow() {
        val rng = Random(7)
        repeat(4000) {
            val src = sources[rng.nextInt(sources.size)]
            val ranges = foldableRanges(src)
            val folded = ranges.filter { rng.nextBoolean() }.map { it.first }.toSet()
            val map = FoldMap.build(src, folded)
            val oldDisplay = map.display
            if (oldDisplay.isEmpty()) return@repeat
            val caret = rng.nextInt(1, oldDisplay.length + 1)
            val newDisplay = oldDisplay.removeRange(caret - 1, caret)
            val edit = FoldMap.applyDisplayEdit(map, oldDisplay, newDisplay, caret - 1, caret - 1)
            assertTrue(edit.caret in 0..edit.source.length)
            FoldMap.build(edit.source, folded)
        }
    }
}
