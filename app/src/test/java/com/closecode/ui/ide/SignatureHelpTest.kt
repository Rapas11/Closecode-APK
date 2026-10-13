package com.closecode.ui.ide

import androidx.compose.ui.graphics.Color
import com.closecode.lang.AndroidApiIndex
import com.closecode.lang.ApiMember
import com.closecode.lang.MemberKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureHelpTest {

    private fun index(vararg members: ApiMember): AndroidApiIndex {
        return AndroidApiIndex.parse(
            buildString {
                appendLine("C|android.test.Owner|Owner|")
                for (m in members) {
                    val params = m.params.joinToString(";")
                    appendLine("M|android.test.Owner|${m.name}|${m.arity}|${m.type}|$params")
                }
            },
        )
    }

    // -- finding the call -------------------------------------------------------------

    @Test
    fun findsTheCallTheCaretIsInside() {
        val src = "foo(1, 2)"
        val call = callSiteAt(src, 5)
        assertNotNull(call)
        assertEquals("foo", call!!.name)
        assertEquals(3, call.openParen)
        assertEquals(0, call.argIndex)
    }

    @Test
    fun theCaretPastACommaIsOnTheNextArgument() {
        val src = "foo(1, 2)"
        // The caret sits just after the comma, so the reader is writing the second argument.
        assertEquals(1, callSiteAt(src, 6)!!.argIndex)
    }

    @Test
    fun aParenInsideAStringIsNotACall() {
        val src = "val s = \"foo(\"\nbar()"
        val cursor = src.indexOf("bar(") + 4
        val call = callSiteAt(src, cursor)
        assertEquals("bar", call!!.name)
    }

    @Test
    fun anApostropheInACommentCannotSwallowTheCallsAfterIt() {
        val src = "// don't do this\nbar(1)"
        val call = callSiteAt(src, src.indexOf("bar(") + 4)
        assertEquals("bar", call!!.name)
    }

    @Test
    fun aParenInsideALineCommentIsNotACall() {
        val src = "foo(1) // bar(\n"
        val call = callSiteAt(src, src.length)
        assertNull(call)
    }

    @Test
    fun aRawStringDoesNotOpenACall() {
        val src = "val s = \"\"\"text (\"\"\"\nbar()"
        val call = callSiteAt(src, src.indexOf("bar(") + 4)
        assertEquals("bar", call!!.name)
    }

    @Test
    fun theInnermostCallWins() {
        val src = "outer(inner(a), b)"
        val call = callSiteAt(src, 13)
        assertEquals("inner", call!!.name)
    }

    @Test
    fun aKeywordCallHasNoSignature() {
        assertNull(callSiteAt("if (", 4))
        assertNull(callSiteAt("while (", 7))
        assertNull(callSiteAt("for (", 5))
    }

    @Test
    fun aQualifierIsKeptButIsNotTheName() {
        val src = "super.onCreate(1)"
        val call = callSiteAt(src, 15)
        assertEquals("onCreate", call!!.name)
        assertEquals("super", call.qualifier)
    }

    // -- reading a declaration out of the buffer ---------------------------------------

    @Test
    fun readsTheParametersOfAFunctionInTheBuffer() {
        val src = "fun greet(name: String, times: Int): String {\n}\nval x = greet(\"a\", 1)"
        val call = callSiteAt(src, src.indexOf("greet(\"a\"") + "greet(".length + 4)
        assertEquals(1, call!!.argIndex)
        val params = declaredParams(src, "greet")
        assertNotNull(params)
        assertEquals("name", params!![0].name)
        assertEquals("String", params[0].type)
        assertEquals("times", params[1].name)
        assertEquals("Int", params[1].type)
    }

    @Test
    fun aDefaultValueIsNotPartOfTheType() {
        val src = "fun greet(name: String = \"x\", times: Int = 1)"
        val params = declaredParams(src, "greet")
        assertEquals("String", params!![0].type)
        assertEquals("Int", params[1].type)
    }

    @Test
    fun aFunctionInTheBufferBeatsAnApiOneOfTheSameName() {
        val src = "fun paint(size: Int) {}\npaint("
        val info = signatureHelp(src, src.length, index())
        assertNotNull(info)
        assertEquals("paint", info!!.name)
        assertEquals("Int", info.params.single().type)
    }

    @Test
    fun theNearestDeclarationWins() {
        val src = "fun f(a: Int) {}\nfun f(a: String, b: Int) {}\nf("
        val params = declaredParams(src, "f")
        assertEquals(2, params!!.size)
    }

    @Test
    fun theNearestDeclarationWinsEvenWhenItIsEarlier() {
        val src = "fun f(a: String, b: Int) {}\nfun f(a: Int) {}\nf("
        assertEquals(1, declaredParams(src, "f")!!.size)
    }

    @Test
    fun theReturnTypeIsReadToo() {
        val src = "fun greet(name: String): String {\n}\ngreet("
        assertEquals("String", declaredReturnType(src, "greet"))
    }

    @Test
    fun anUnparseableDeclarationIsSkippedNotReportedAsEmpty() {
        // An untyped parameter would mean "takes nothing", which is a different claim.
        val src = "fun f(a) {}\nfun f(a: Int) {}\nf("
        assertEquals(1, declaredParams(src, "f")!!.size)
    }

    @Test
    fun aTopLevelCommaInsideANestedGenericDoesNotSplitTheParameter() {
        val src = "fun f(map: Map<String, Int>, n: Int)"
        val params = declaredParams(src, "f")
        assertEquals(2, params!!.size)
        assertEquals("Map<String, Int>", params[0].type)
    }

    // -- reading an API member ---------------------------------------------------------

    @Test
    fun readsAnApiMembersParametersWhenTheBufferHasNone() {
        val idx = index(
            ApiMember("setText", MemberKind.METHOD, 1, "Unit", listOf("text:CharSequence")),
        )
        val src = "widget.setText("
        val info = signatureHelp(src, src.length, idx)
        assertNotNull(info)
        assertEquals("setText", info!!.name)
        assertEquals("text", info.params.single().name)
        assertEquals("CharSequence", info.params.single().type)
    }

    @Test
    fun anUnknownNameGetsNoTooltip() {
        val src = "someUnknownThing("
        assertNull(signatureHelp(src, src.length, index()))
    }

    @Test
    fun aKeywordCallGetsNoTooltip() {
        val src = "if ("
        assertNull(signatureHelp(src, src.length, index()))
    }

    @Test
    fun aFunctionWithNoParametersStillOffersItsName() {
        val src = "fun restart() {}\nrestart("
        val info = signatureHelp(src, src.length, index())
        assertNotNull(info)
        assertTrue(info!!.params.isEmpty())
        assertEquals(0, info.activeIndex)
    }

    @Test
    fun theActiveArgumentIsNeverPastTheEnd() {
        val src = "fun f(a: Int) {}\nf(1, 2, 3"
        val info = signatureHelp(src, src.length, index())
        // Three commas after one parameter: the caret is past the last argument, not off the list.
        assertEquals(1, info!!.activeIndex)
    }

    // -- the descriptions ---------------------------------------------------------------

    @Test
    fun theCommonParametersHaveDescriptions() {
        assertEquals("To apply to this layout.", describeParam("modifier"))
        assertEquals("Called when this is tapped.", describeParam("onClick"))
        assertEquals("The text to display.", describeParam("text"))
    }

    @Test
    fun anUnfamiliarParameterHasNoDescription() {
        assertNull(describeParam("zzzNotAKnownParameter"))
    }

    @Test
    fun theDescriptionTableStaysSmall() {
        // A sentence invented for a parameter nobody asked about is a sentence that can be wrong,
        // so the table is kept to the names that appear in almost every call rather than grown.
        assertTrue(
            "the table holds ${describeParamCount()} entries",
            describeParamCount() in 30..45,
        )
    }

    // -- the drawn line -----------------------------------------------------------------

    @Test
    fun theSignatureLineNamesTheFunctionAndEveryParameter() {
        val info = SignatureInfo("Text", listOf(SignatureParam("text", "String"), SignatureParam("modifier", "Modifier")), activeIndex = 0)
        assertEquals("Text(text: String, modifier: Modifier)", signaturePlainText(info))
    }

    @Test
    fun theSignatureLineCarriesTheReturnTypeWhenThereIsOne() {
        val info = SignatureInfo("greet", listOf(SignatureParam("name", "String")), activeIndex = 0, returnType = "String")
        assertEquals("greet(name: String): String", signaturePlainText(info))
    }

    @Test
    fun theActiveParameterIsTheOneThatIsEmphasised() {
        val info = SignatureInfo("Text", listOf(SignatureParam("text", "String"), SignatureParam("modifier", "Modifier")), activeIndex = 1)
        // The emphasised text is the second parameter, not the first.
        assertEquals("modifier: Modifier", activeParamText(info))
    }

    @Test
    fun aFunctionWithNoParametersDrawsItsBareName() {
        val info = SignatureInfo("restart", emptyList(), activeIndex = 0)
        assertEquals("restart()", signaturePlainText(info))
    }

    // -- where the box goes ---------------------------------------------------------------

    @Test
    fun theBoxGoesAboveTheCaretLine() {
        val top = signatureTopPx(
            caretTopPx = 200f,
            tooltipHeightPx = 60f,
            gapPx = 6f,
            viewportHeightPx = 800f,
            edgePx = 6f,
        )
        assertEquals(134f, top!!, 0.01f)
    }

    @Test
    fun theBoxStandsDownWhenThereIsNoRoomAbove() {
        // Near the top of the file there is nowhere above, and below belongs to the suggestion list.
        assertNull(
            signatureTopPx(
                caretTopPx = 10f,
                tooltipHeightPx = 60f,
                gapPx = 6f,
                viewportHeightPx = 800f,
                edgePx = 6f,
            ),
        )
    }

    @Test
    fun theBoxIsNeverDrawnBelowTheCaret() {
        val caret = 300f
        val top = signatureTopPx(caret, 60f, 6f, 900f, 6f)
        assertTrue(top!! + 60f <= caret)
    }
}
class SignatureOverloadsTest {

    @Test
    fun everyOverloadIsListedNearestFirst() {
        val src = "fun load(path: String) {}\nfun load(path: String, force: Boolean) {}\nload("
        val options = signatureHelpAll(src, src.length, AndroidApiIndex.EMPTY)
        assertEquals(2, options.size)
        // Nearest declaration first, so the single answer keeps meaning "the one being written".
        assertEquals(2, options[0].params.size)
        assertEquals(1, options[1].params.size)
        assertEquals(options[0], signatureHelp(src, src.length, AndroidApiIndex.EMPTY))
    }

    @Test
    fun noCallMeansNoOptions() {
        assertTrue(signatureHelpAll("val x = 1", 9, AndroidApiIndex.EMPTY).isEmpty())
    }
}

class NamedArgumentTest {

    private val params = listOf(
        SignatureParam("text", "String"),
        SignatureParam("modifier", "Modifier"),
        SignatureParam("color", "Color"),
    )

    private fun indexOf(src: String, cursor: Int): Int {
        val open = src.indexOf('(')
        val positional = src.substring(open + 1, cursor).count { it == ',' }
        return namedOrPositionalIndex(src, cursor, open, positional, params)
    }

    @Test
    fun aNamedArgumentHighlightsItsParameterWhereverItSits() {
        val src = "Text(color = "
        assertEquals(2, indexOf(src, src.length))
    }

    @Test
    fun aPositionalArgumentKeepsTheCommaCount() {
        val src = "Text(\"Hi\", "
        assertEquals(1, indexOf(src, src.length))
    }

    @Test
    fun anUnknownNameFallsBackToPosition() {
        val src = "Text(nope = "
        assertEquals(0, indexOf(src, src.length))
    }

    @Test
    fun aComparisonIsNotAnAssignment() {
        val src = "Text(a == "
        assertEquals(0, indexOf(src, src.length))
    }

    @Test
    fun aCommaInsideAStringDoesNotSplitTheArgument() {
        val src = "Text(\"a,b\", color = "
        assertEquals(2, indexOf(src, src.length))
    }

    @Test
    fun midValueStillHighlightsTheNamedParameter() {
        val src = "Text(color = Red"
        assertEquals(2, indexOf(src, src.length))
    }
}

class SignaturePlainTextTest {

    @Test
    fun plainTextNamesEveryParameter() {
        val info = SignatureInfo("Text", listOf(SignatureParam("text", "String"), SignatureParam("modifier", "Modifier")), activeIndex = 0)
        assertEquals("Text(text: String, modifier: Modifier)", signaturePlainText(info))
    }

    @Test
    fun plainTextCarriesTheReturnType() {
        val info = SignatureInfo("greet", listOf(SignatureParam("name", "String")), activeIndex = 0, returnType = "String")
        assertEquals("greet(name: String): String", signaturePlainText(info))
    }

    @Test
    fun activeParamTextIsTheHighlightedOne() {
        val info = SignatureInfo("Text", listOf(SignatureParam("text", "String"), SignatureParam("modifier", "Modifier")), activeIndex = 1)
        assertEquals("modifier: Modifier", activeParamText(info))
    }

    @Test
    fun activeParamTextIsNullPastTheEnd() {
        val info = SignatureInfo("f", emptyList(), activeIndex = 0)
        assertNull(activeParamText(info))
    }
}
