package com.closecode.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the code editor design spec's Colors and fonts table against the dark theme, value for
 * value. The table is the single source of truth: if a drawing ever disagrees with it, this is
 * the test that says so first.
 */
class EditorSpecPaletteTest {

    private fun hex(color: Color): String {
        val a = (color.alpha * 255).toInt()
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return "%02X%02X%02X%02X".format(a, r, g, b)
    }

    @Test
    fun darkThemeMatchesTheSpecTable() {
        val c = DarkColors
        // Spec table, in spec order.
        assertEquals("FF0F1117", hex(c.editorBg))
        assertEquals("FF141925", hex(c.activeLineBg))
        assertEquals("FF1C212C", hex(c.divider))
        assertEquals("FFD4D7DE", hex(c.text))
        assertEquals("FF444C5E", hex(c.lineNumber))
        assertEquals("FFC5CAD6", hex(c.lineNumberActive))
        assertEquals("FF232938", hex(c.indentGuide))
        assertEquals("FF4C8DFF", hex(c.accent))
        assertEquals("FFEF5B5B", hex(c.error))
        assertEquals("FFC792EA", hex(c.codeKeyword))
        assertEquals("FFE5C07B", hex(c.codeType))
        assertEquals("FF61AFEF", hex(c.codeFunction))
        assertEquals("FF98C379", hex(c.codeString))
        assertEquals("FF181D29", hex(c.popupBg))
        assertEquals("FF2A3142", hex(c.popupBorder))
        assertEquals("FF243055", hex(c.popupSelected))
        assertEquals("FFC5CAD6", hex(c.popupText))
        assertEquals("FF7D869A", hex(c.popupMuted))
        assertEquals("FF2A4380", hex(c.textSelection))
        assertEquals("FF1F2533", hex(c.toolbarBg))
        assertEquals("FF2F3749", hex(c.toolbarBorder))
        assertEquals("FF7FB0FF", hex(c.toolbarAccent))
        assertEquals("FFE5C07B", hex(c.bulbGlyph))
        assertEquals("FF2A2615", hex(c.bulbTileBg))
        assertEquals("FF34191E", hex(c.pillBg))
        assertEquals("FF5C2A31", hex(c.pillBorder))
        assertEquals("FFF2A0A0", hex(c.pillText))
        assertEquals("FF3FB97A", hex(c.gitAdded))
        assertEquals("FF4C8DFF", hex(c.gitModified))
        assertEquals("FFEF5B5B", hex(c.gitDeleted))
        assertEquals("FF566078", hex(c.foldArrow))
        // Hexes the spec gives inline outside the table.
        assertEquals("FFE6E8EE", hex(c.brightText))
        assertEquals("FF6B7385", hex(c.hintText))
        assertEquals("FF9AA3B5", hex(c.iconTint))
        assertEquals("FF2F6FED", hex(c.runFill))
        assertEquals("FF131722", hex(c.tabActiveBg))
        assertEquals("FF1A1F2B", hex(c.foldChipBg))
        assertEquals("FF3A4256", hex(c.scrollThumb))
        assertEquals("FF7A5AF0", hex(c.badgeP))
        assertEquals("FF2F9E7A", hex(c.badgeF))
        assertEquals("FFC28A2C", hex(c.badgeM))
        assertEquals("FFE5C07B", hex(c.warnText))
    }

    @Test
    fun everySpecTokenHasALightValueToo() {
        // Not verbatim — the spec only defines dark — but present, so no screen can read an
        // uninitialised token on the light theme. indentGuide is exempt from the opacity check:
        // it is translucent by design on both themes, like the guide rule it replaces.
        val c = LightColors
        listOf(
            c.editorBg, c.activeLineBg, c.divider, c.text, c.lineNumber, c.lineNumberActive,
            c.accent, c.error, c.codeKeyword, c.codeType, c.codeFunction,
            c.codeString, c.popupBg, c.popupBorder, c.popupSelected, c.popupText, c.popupMuted,
            c.textSelection, c.toolbarBg, c.toolbarBorder, c.toolbarAccent, c.bulbTileBg,
            c.bulbGlyph, c.pillBg, c.pillBorder, c.pillText, c.gitAdded, c.gitModified,
            c.gitDeleted, c.foldArrow, c.brightText, c.hintText, c.iconTint, c.runFill,
            c.tabActiveBg, c.foldChipBg, c.scrollThumb, c.badgeP, c.badgeF, c.badgeM, c.warnText,
        ).forEach { assertEquals(1f, it.alpha, 0.001f) }
        assertEquals(0x26 / 255f, c.indentGuide.alpha, 0.01f)
    }
}
