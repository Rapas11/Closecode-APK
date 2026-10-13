package com.closecode.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownNamesGroupingTest {

    @Test
    fun everyKnownNameIsGrouped() {
        val grouped = KnownNames.grouped().map { it.first }.toSet()
        assertEquals(KnownNames.all, grouped)
    }

    @Test
    fun everyEntryCarriesAGroupOrIsAnImplicitName() {
        // `it`, `this`, `R` and friends have no package, so they are allowed to be blank.
        for ((name, group) in KnownNames.grouped()) {
            if (name in KnownNames.implicit) continue
            assertTrue("$name should have a group", group.isNotBlank())
        }
    }

    @Test
    fun implicitNamesGetNoPackage() {
        val byName = KnownNames.grouped().toMap()
        for (name in KnownNames.implicit) {
            assertEquals("$name should have no package", "", byName[name])
        }
    }

    @Test
    fun typesAreGroupedWithTheirLanguage() {
        val byName = KnownNames.grouped().toMap()
        assertEquals("kotlin", byName["String"])
        assertEquals("java.lang", byName["NumberFormatException"])
    }

    @Test
    fun thereAreNoDuplicates() {
        assertEquals(KnownNames.grouped().size, KnownNames.grouped().map { it.first }.toSet().size)
    }
}

class CompletionRankingTest {

    private fun labelsFor(src: String, marker: String): List<String> {
        val result = KotlinAnalyzer.analyze(src)
        val caret = src.indexOf(marker) + marker.length
        return result.completionsAt(caret, limit = 40).map { it.label }
    }

    @Test
    fun fileDeclaredNamesBeatTheStdlibList() {
        // The known-name list is huge. Anything the user actually typed must win the limited
        // result window, otherwise their own names get pushed off by stdlib noise.
        val src = """
            class Repo {
                fun find(query: String) {
                    val results = listOf(1)
                }
            }
        """.trimIndent()
        val labels = labelsFor(src, "listOf(1)")
        assertTrue("locals missing in $labels", labels.contains("results"))
        assertTrue("params missing in $labels", labels.contains("query"))
        assertTrue("types missing in $labels", labels.contains("Repo"))
    }

    @Test
    fun stdlibEntriesCarryAPackageSubtitle() {
        val result = KotlinAnalyzer.analyze("val x = 1\n")
        val caret = result.source.length
        // Ask for a wide window: the stdlib list is large and ranked below local names.
        val items = result.completionsAt(caret, limit = 400)
        val stringEntry = items.firstOrNull { it.label == "String" }
        assertNotNull("String should be offered", stringEntry)
        assertEquals("kotlin", stringEntry!!.subtitle)
    }

    @Test
    fun stdlibEntriesUseTheirOwnBadgesNotThePlainKeywordOne() {
        val result = KotlinAnalyzer.analyze("val x = 1\n")
        val caret = result.source.length
        val items = result.completionsAt(caret, limit = 400)
        val stringEntry = items.first { it.label == "String" }
        assertTrue(
            "String should not use the generic keyword badge",
            stringEntry.kind != CompletionKind.KEYWORD,
        )
    }

    @Test
    fun aLocalOutranksStdlibOfTheSameLetter() {
        // Both start with "m", so ranking is the only thing separating them. The marker ends in a
        // newline so the caret sits at an empty prefix, the way it does mid-typing.
        val src = "fun demo() {\n    val myLocal = 1\n    \n}\n"
        val labels = labelsFor(src, "val myLocal = 1\n    ")
        assertTrue("myLocal missing in $labels", labels.contains("myLocal"))
        val localIndex = labels.indexOf("myLocal")
        val stdlibIndex = labels.indexOf("maxOf")
        if (stdlibIndex >= 0) {
            assertTrue("local should outrank stdlib", localIndex < stdlibIndex)
        }
    }

    @Test
    fun keywordsNoLongerCrowdOutRealNames() {
        // Thirty bare keywords used to sit ahead of every stdlib name in the result window.
        val result = KotlinAnalyzer.analyze("val x = 1\n")
        val items = result.completionsAt(result.source.length, limit = 40)
        val keywordCount = items.count { it.kind == CompletionKind.KEYWORD }
        assertTrue("keywords should not fill the window, found $keywordCount", keywordCount <= 5)
    }
}