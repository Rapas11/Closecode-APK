package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ANSI parser, with no screen involved.
 *
 * A terminal program's colour is written as escape sequences. Shown raw they are noise, so the whole
 * job of the parser is to remove them and keep the drawing intent — which is exactly the kind of
 * thing that can be pinned by a test rather than by looking at a phone.
 */
class TerminalAnsiTest {

    private fun esc(code: String): String = "$ESC[$code"
    private fun sgr(code: String): String = esc(code + "m")

    private fun runsOf(line: String) = parseAnsi(line).runs
    private fun textOf(line: String) = parseAnsi(line).text

    /**
     * The style a set of SGR parameters produces.
     *
     * Taken straight from the transition function rather than from a line: a line that is nothing
     * but escapes draws no runs at all, so there is nothing left to read a style back from — and
     * re-parsing escapes here would only be testing a second copy of the parser.
     */
    private fun styleOf(vararg codes: Int) = applySgr(AnsiStyle.Plain, intArrayOf(*codes))

    // -- Plain text ------------------------------------------------------------

    @Test
    fun textWithoutEscapesIsLeftAlone() {
        assertEquals("hello", textOf("hello"))
    }

    @Test
    fun plainTextIsASingleUnstyledRun() {
        val runs = runsOf("hello")
        assertEquals(1, runs.size)
        assertEquals("hello", runs[0].text)
        assertEquals(AnsiStyle.Plain, runs[0].style)
    }

    @Test
    fun anEmptyLineHasNothingInIt() {
        assertEquals("", textOf(""))
        assertTrue(runsOf("").all { it.text.isEmpty() })
    }

    // -- Colour ----------------------------------------------------------------

    @Test
    fun aColourSequenceIsRemovedAndItsRunIsTinted() {
        val line = sgr("31") + "error" + sgr("0")
        assertEquals("error", textOf(line))
        val runs = runsOf(line)
        assertEquals(1, runs.size)
        assertEquals(AnsiColor.Palette(1), runs[0].style.fg)
    }

    @Test
    fun aResetReturnsToTheDefaultColour() {
        val runs = runsOf(sgr("32") + "ok" + sgr("0") + "plain")
        assertEquals(2, runs.size)
        assertEquals(AnsiColor.Palette(2), runs[0].style.fg)
        assertNull(runs[1].style.fg)
    }

    @Test
    fun everyColourNumberResolvesToItsOwnIndex() {
        for (index in 0..7) {
            assertEquals(AnsiColor.Palette(index), styleOf(30 + index).fg)
        }
    }

    @Test
    fun aBrightColourGetsItsOwnHalfOfThePalette() {
        // The bug this pins: every bright code collapsed onto its ordinary twin on the way in and
        // only the ordinary half was read on the way out, so a program's bright yellow, red, green
        // and blue all drew in their dimmer versions, and the bright half of every theme was dead.
        for (index in 0..7) {
            assertEquals(AnsiColor.Palette(index + 8), styleOf(90 + index).fg)
            assertEquals(AnsiColor.Palette(index + 8), styleOf(100 + index).bg)
        }
    }

    @Test
    fun anOrdinaryColourIsUnchanged() {
        for (index in 0..7) {
            assertEquals(AnsiColor.Palette(index), styleOf(30 + index).fg)
            assertEquals(AnsiColor.Palette(index), styleOf(40 + index).bg)
        }
    }

    @Test
    fun theBrightHalfOfThePaletteIsNotTheOrdinaryHalfAgain() {
        // A theme whose bright half matched its ordinary half on every hue would be one where
        // nothing has been chosen yet.
        val dark = com.closecode.ui.theme.DarkColors.ansi
        assertTrue(dark.bright[3] != dark.normal[3])
        assertTrue(dark.bright[1] != dark.normal[1])
    }

    @Test
    fun aBackgroundIsRecordedSeparatelyFromTheForeground() {
        val style = styleOf(41)
        assertEquals(AnsiColor.Palette(1), style.bg)
        assertNull(style.fg)
    }

    @Test
    fun aBrightBackgroundIsRecordedToo() {
        // 105 is bright magenta on a background, so index 13 rather than 5: the two halves of the
        // palette are numbered separately, which is what lets the bright one be read at all.
        assertEquals(AnsiColor.Palette(13), styleOf(105).bg)
        assertEquals(AnsiColor.Palette(5), styleOf(45).bg)
    }

    @Test
    fun twoColoursOnOneLineProduceTwoRuns() {
        val runs = runsOf(sgr("31") + "a" + sgr("32") + "b")
        assertEquals(listOf("a", "b"), runs.map { it.text })
        assertEquals(listOf(AnsiColor.Palette(1), AnsiColor.Palette(2)), runs.map { it.style.fg })
    }

    @Test
    fun aSequenceInTheMiddleDoesNotSplitTheTextAroundIt() {
        assertEquals("abc", textOf("a" + sgr("1") + "b" + sgr("0") + "c"))
    }

    // -- Attributes ------------------------------------------------------------

    @Test
    fun theTextAttributesAreAllCarried() {
        assertTrue(styleOf(1).bold)
        assertTrue(styleOf(2).dim)
        assertTrue(styleOf(3).italic)
        assertTrue(styleOf(4).underline)
        assertTrue(styleOf(7).inverse)
    }

    @Test
    fun eachAttributeHasItsOwnWayOfBeingTurnedOff() {
        val all = AnsiStyle(bold = true, dim = true, italic = true, underline = true, inverse = true)
        assertEquals(all.copy(bold = false, dim = false), applySgr(all, intArrayOf(22)))
        assertEquals(all.copy(italic = false), applySgr(all, intArrayOf(23)))
        assertEquals(all.copy(underline = false), applySgr(all, intArrayOf(24)))
        assertEquals(all.copy(inverse = false), applySgr(all, intArrayOf(27)))
    }

    @Test
    fun aDefaultColourNumberForgetsTheColour() {
        val loud = AnsiStyle(fg = AnsiColor.Palette(1), bg = AnsiColor.Palette(2))
        assertNull(applySgr(loud, intArrayOf(39)).fg)
        assertEquals(AnsiColor.Palette(2), applySgr(loud, intArrayOf(39)).bg)
        assertNull(applySgr(loud, intArrayOf(49)).bg)
        assertEquals(AnsiColor.Palette(1), applySgr(loud, intArrayOf(49)).fg)
    }

    @Test
    fun aFullResetForgetsEverything() {
        val loud = AnsiStyle(fg = AnsiColor.Palette(3), bg = AnsiColor.Palette(4), bold = true, italic = true, underline = true)
        assertEquals(AnsiStyle.Plain, applySgr(loud, intArrayOf(0)))
    }

    @Test
    fun anEmptySequenceMeansResetRatherThanNothingAtAll() {
        // Some programs write ESC [ m where others write ESC [ 0 m. Reading the empty form as "no
        // change" would leave the previous colour in force for the rest of the line.
        assertEquals(AnsiStyle.Plain, styleOf(0))
        assertEquals("reset", textOf(esc("m") + "reset"))
    }

    @Test
    fun aSequenceIsOnlyAsLongAsItsFinalByte() {
        // ESC [ is not a sequence on its own, and the byte after it decides what it was: here an "r",
        // which is the restore-cursor command. Reading it that way is what keeps a stray bracket in
        // a program output from swallowing the rest of the line.
        assertEquals("eset", textOf(esc("r") + "eset"))
    }

    @Test
    fun severalSequencesInOneSequenceAreAllApplied() {
        val style = styleOf(1, 31, 4)
        assertTrue(style.bold)
        assertEquals(AnsiColor.Palette(1), style.fg)
        assertTrue(style.underline)
    }

    @Test
    fun aColourIsKeptUntilSomethingElseReplacesIt() {
        // No reset at the end of the line: a program that colours a run and forgets to close it
        // should still have its colour, because the next line starts afresh anyway.
        assertEquals(AnsiColor.Palette(1), runsOf(sgr("31") + "red").last().style.fg)
    }

    // -- Sequences that are not about colour ------------------------------------

    @Test
    fun cursorMovementIsDroppedRatherThanPrinted() {
        assertEquals("ab", textOf("a" + esc("2K") + "b"))
    }

    @Test
    fun anEraseScreenIsDropped() {
        assertEquals("text", textOf(esc("2J") + "text"))
    }

    @Test
    fun anOperatingSystemCommandIsDropped() {
        // ESC ] 0 ; title BEL sets the window title, which a list of lines has nowhere to show.
        assertEquals("text", textOf(ESC + "]0;some title\u0007text"))
    }

    @Test
    fun aTwoCharacterEscapeIsDropped() {
        assertEquals("after", textOf(ESC + "c" + "after"))
    }

    @Test
    fun aTruncatedEscapeAtTheEndOfALineDoesNotThrow() {
        // A program killed mid-write can leave half a sequence behind. Dropping it is the only
        // answer; throwing would take the whole terminal down with it.
        assertEquals("text", textOf("text$ESC"))
        assertEquals("text", textOf("text$ESC["))
        assertEquals("text", textOf("text$ESC[3"))
        assertEquals("text", textOf("text$ESC]"))
    }

    @Test
    fun anUnterminatedControlCharacterIsDropped() {
        // A bell or a form feed has no place in a list of lines, and letting it through can disturb
        // the layout of the row it lands on.
        assertEquals("ab", textOf("ab"))
    }

    // -- Tabs and carriage returns ---------------------------------------------

    @Test
    fun aTabBecomesSpacesUpToTheNextStop() {
        // A literal tab has no tab stop of its own in a Compose string and would shove the rest of
        // the line out of the column it started in, so it is expanded here instead.
        assertEquals("a" + " ".repeat(TabStop - 1) + "b", textOf("a\tb"))
        assertEquals(TabStop + 1, textOf("\tb").length)
    }

    @Test
    fun aTabAdvancesToTheStopRatherThanAlwaysAddingEight() {
        assertEquals("  " + " ".repeat(TabStop - 2) + "x", textOf("  \tx"))
    }

    @Test
    fun aTabAfterExactlyOneStopStartsAFreshOne() {
        // Eight spaces already fill one stop, so the tab opens the next one rather than topping the
        // current one up: two stops of indent, plus the character after it.
        assertEquals(TabStop * 2 + 1, textOf(" ".repeat(TabStop) + "\tx").length)
    }

    @Test
    fun onlyTheLastPassOfAProgressLineSurvives() {
        // The way a download or a build draws a progress bar: the same line printed over and over
        // with a rising percentage. Everything before the final carriage return is a superseded
        // pass, and the escapes that coloured it sit after the return, so the colour comes along.
        val line = "10%\r20%\r" + sgr("32") + "done"
        assertEquals("done", textOf(line))
        assertEquals(AnsiColor.Palette(2), runsOf(line).last().style.fg)
    }

    @Test
    fun aLineWithNoCarriageReturnIsUntouched() {
        assertEquals("plain", textOf("plain"))
    }

    // -- Extended colours ------------------------------------------------------

    @Test
    fun anEightBitPaletteIndexKeepsItsNumber() {
        assertEquals(AnsiColor.Palette(1), styleOf(38, 5, 1).fg)
        assertEquals(AnsiColor.Palette(15), styleOf(38, 5, 15).fg)
    }

    @Test
    fun aTruecolorValuePassesThroughUntouched() {
        // Collapsing it onto black or white is how every 256-color program drew wrong.
        assertEquals(AnsiColor.Rgb(0xFAFAFA), styleOf(38, 2, 250, 250, 250).fg)
        assertEquals(AnsiColor.Rgb(0x0A0A0A), styleOf(38, 2, 10, 10, 10).fg)
        assertEquals(AnsiColor.Rgb(0x123456), styleOf(48, 2, 0x12, 0x34, 0x56).bg)
    }

    @Test
    fun theCubeMapsToRealSteps() {
        // 196 is full red: (5, 0, 0) on steps of 0, 95, 135, 175, 215, 255.
        assertEquals(AnsiColor.Rgb(0xFF0000), styleOf(38, 5, 196).fg)
        // 16 is the cube's black.
        assertEquals(AnsiColor.Rgb(0x000000), styleOf(38, 5, 16).fg)
    }
    @Test
    fun theGraysClimbInTensFromEight() {
        assertEquals(AnsiColor.Rgb(0x080808), styleOf(38, 5, 232).fg)
        // 240 is eight steps up: 8 + 10 * 8 = 88 = 0x58.
        assertEquals(AnsiColor.Rgb(0x585858), styleOf(38, 5, 240).fg)
    }

    @Test
    fun lsFolderBlueIsBoldPaletteBlue() {
        // What `ls --color` emits for a folder: bright-bold blue.
        val style = styleOf(1, 34)
        assertTrue(style.bold)
        assertEquals(AnsiColor.Palette(4), style.fg)
    }

    @Test
    fun printfRedGreenBlueShowsThreeColors() {
        // The exact probe from the phone: words in red, green and blue with plain gaps.
        val runs = runsOf("[31mred[0m [32mgreen[0m [34mblue[0m")
        assertEquals(
            listOf("red", " ", "green", " ", "blue"),
            runs.map { it.text },
        )
        assertEquals(
            listOf(
                AnsiColor.Palette(1),
                null,
                AnsiColor.Palette(2),
                null,
                AnsiColor.Palette(4),
            ),
            runs.map { it.style.fg },
        )
    }

    @Test
    fun anExtendedBackgroundIsRecordedAsABackground() {
        assertEquals(AnsiColor.Palette(2), styleOf(48, 5, 2).bg)
    }

    @Test
    fun anExtendedColourLeavesTheForegroundAloneWhenItIsABackground() {
        val style = AnsiStyle(fg = AnsiColor.Palette(4))
        assertEquals(AnsiColor.Palette(4), applySgr(style, intArrayOf(48, 5, 2)).fg)
        assertEquals(AnsiColor.Palette(2), applySgr(style, intArrayOf(48, 5, 2)).bg)
    }

    @Test
    fun anUnrecognisedSequenceChangesNothing() {
        assertEquals(AnsiStyle.Plain, styleOf(99))
    }
}