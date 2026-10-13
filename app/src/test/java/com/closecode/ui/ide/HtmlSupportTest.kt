package com.closecode.ui.ide

import com.closecode.lang.DiagnosticCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSupportTest {

    // -- which rules a buffer is coloured by ---------------------------------------------

    @Test
    fun aDoctypeMakesItMarkup() {
        assertEquals(CodeLanguage.MARKUP, codeLanguageFor("HTML"))
        assertEquals(CodeLanguage.MARKUP, codeLanguageFor("XML"))
        assertEquals(CodeLanguage.STYLESHEET, codeLanguageFor("CSS"))
        assertEquals(CodeLanguage.PROGRAMMING, codeLanguageFor("Kotlin"))
    }

    @Test
    fun aBufferIsRecognisedFromItsOwnFirstLines() {
        assertEquals(CodeLanguage.MARKUP, codeLanguageOf("<!DOCTYPE html>\n<html>"))
        assertEquals(CodeLanguage.MARKUP, codeLanguageOf("<!-- a page -->\n<div>"))
        assertEquals(CodeLanguage.STYLESHEET, codeLanguageOf("body {\n  color: red;\n}"))
        assertEquals(CodeLanguage.PROGRAMMING, codeLanguageOf("fun main() {\n}"))
    }

    @Test
    fun aSlashInsideAUrlIsNotAComment() {
        // The bug this pins: `//` starts a comment in every language this file used to have, so
        // href="http://example.com" swallowed the rest of the line and the tag never closed on screen.
        val marked = highlightMarkup("""<a href="http://example.com">x</a>""", testColors())
        val styles = marked.spanStyles
        // The only styled runs are the tag, its attribute and its value — the url is inside the
        // value's quotes, so it can never start a comment.
        assertTrue(styles.isNotEmpty())
        assertTrue(marked.text.contains("http://example.com"))
    }

    @Test
    fun aMarkupCommentIsStillAComment() {
        val marked = highlightMarkup("<!-- hidden --><p>shown</p>", testColors())
        assertTrue(marked.text.contains("hidden"))
        assertTrue(marked.spanStyles.isNotEmpty())
    }

    @Test
    fun aStylesheetCommentCarriesAcrossLines() {
        // Same class of bug as the url above: a comment left open swallows the rules after it.
        val text = "/* open\n   still comment\n   closed */\nbody { color: red; }"
        val marked = highlightStylesheet(text, testColors())
        assertEquals(text, marked.text)
    }

    @Test
    fun aKtFileIsNotColouredAsMarkup() {
        val code = "fun a() = listOf(1)\nval x = \"<!-- not a comment -->\""
        assertEquals(CodeLanguage.PROGRAMMING, codeLanguageFor("Kotlin"))
        assertEquals(code, highlightCode(code, testColors(), CodeLanguage.PROGRAMMING).text)
    }

    // -- tag completion --------------------------------------------------------------------

    @Test
    fun typingAnAngleBracketOffersTags() {
        val src = "<"
        val ctx = htmlTagContextAt(src, 1)
        assertNotNull(ctx)
        assertEquals("", ctx!!.tagName)
        assertTrue(htmlTagSuggestions("").contains("div"))
    }

    @Test
    fun aPartialTagNameFiltersTheList() {
        val ctx = htmlTagContextAt("<but", 4)
        assertEquals("but", ctx!!.tagName)
        val offers = htmlTagSuggestions(ctx.tagName)
        assertEquals(listOf("button"), offers)
    }

    @Test
    fun aSpaceAfterTheTagNameOffersItsAttributes() {
        val ctx = htmlTagContextAt("<img ", 5)
        assertEquals("img", ctx!!.tagName)
        assertTrue(ctx.afterName)
        assertTrue(htmlAttributeSuggestions("img", "").contains("src"))
        assertTrue(htmlAttributeSuggestions("img", "").contains("alt"))
    }

    @Test
    fun everyTagAlsoOffersTheGlobalAttributes() {
        val attrs = htmlAttributeSuggestions("div", "")
        assertTrue(attrs.contains("class"))
        assertTrue(attrs.contains("id"))
    }

    @Test
    fun aTagWithNoAttributeListOfItsOwnStillOffersTheGlobalOnes() {
        val attrs = htmlAttributeSuggestions("kbd", "")
        assertTrue(attrs.contains("class"))
    }

    @Test
    fun ordinaryTextIsNotATagContext() {
        assertNull(htmlTagContextAt("val x = 1", 8))
        assertNull(htmlTagContextAt("a < b", 4))
    }

    // -- closing a tag ----------------------------------------------------------------------

    @Test
    fun typingTheSecondSlashCompletesTheOpenTag() {
        val before = "<div>\n  <"
        val (after, caret) = closeTagAt(before, "$before/", before.length + 1)!!
        assertEquals("<div>\n  </div>", after.substring(0, caret))
        assertTrue(after.endsWith(">"))
    }

    @Test
    fun theInnermostOpenTagIsTheOneOffered() {
        val before = "<div>\n  <section>\n    <"
        val (_, caret) = closeTagAt(before, "$before/", before.length + 1)!!
        // The `</` was already typed, so only the name and the bracket are added.
        assertEquals(before.length + 1 + "section>".length, caret)
    }

    @Test
    fun aVoidTagIsNotOfferedACloser() {
        // </img> is not markup; offering it would insert a tag no browser accepts.
        val before = "<img src=\"a.png\">\n<"
        assertNull(closeTagAt(before, "$before/", before.length + 1))
    }

    @Test
    fun aSlashThatIsNotAfterAnAngleBracketIsLeftAlone() {
        val before = "<div>text"
        assertNull(closeTagAt(before, "$before/", before.length + 1))
    }

    @Test
    fun aPasteIsNotTreatedAsTypingACloser() {
        val before = "<div>"
        assertNull(closeTagAt(before, "$before</div>", before.length + 6))
    }

    // -- diagnostics -------------------------------------------------------------------------

    @Test
    fun anUnclosedTagIsReportedOnItsOwnLine() {
        val src = "<div>\n  <p>hello</p>"
        val found = htmlTagDiagnostics(src)
        assertEquals(1, found.size)
        assertEquals(1, found[0].line)
        assertEquals(DiagnosticCodes.HTML_UNCLOSED_TAG, found[0].code)
        assertTrue(found[0].message.contains("div"))
    }

    @Test
    fun aClosedTagReportsNothing() {
        assertEquals(emptyList<Any>(), htmlTagDiagnostics("<div>\n  <p>hi</p>\n</div>"))
    }

    @Test
    fun aVoidTagNeverCountsAsUnclosed() {
        assertEquals(emptyList<Any>(), htmlTagDiagnostics("<br>\n<img src=\"a.png\">"))
    }

    @Test
    fun aSelfClosingTagNeverCountsAsUnclosed() {
        assertEquals(emptyList<Any>(), htmlTagDiagnostics("<div />"))
    }

    @Test
    fun aTagLeftOpenInsideACommentIsNotReported() {
        assertEquals(emptyList<Any>(), htmlTagDiagnostics("<!-- <div> -->\n<p>x</p>"))
    }

    @Test
    fun aTagLeftOpenInsideScriptIsNotReported() {
        val src = "<script>\n  var a = '<div>';\n</script>"
        assertEquals(emptyList<Any>(), htmlTagDiagnostics(src))
    }

    @Test
    fun aGreaterThanInsideAnAttributeDoesNotCloseTheTag() {
        val src = "<div title=\"a > b\">\n  <p>x</p>\n</div>"
        assertEquals(emptyList<Any>(), htmlTagDiagnostics(src))
    }

    @Test
    fun aDoctypeIsNotATag() {
        assertEquals(emptyList<Any>(), htmlTagDiagnostics("<!DOCTYPE html>\n<html><body>x</body></html>"))
    }

    @Test
    fun codeIsNeverCheckedForTags() {
        val kt = "fun main() {\n  listOf(1)\n}"
        assertEquals(emptyList<Any>(), htmlTagDiagnostics(kt))
    }

    private fun testColors() = com.closecode.ui.theme.DarkColors
}