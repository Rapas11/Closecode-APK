package com.closecode.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the app screens design spec's token table against the dark theme, value for value —
 * the same job EditorSpecPaletteTest does for the editor spec.
 */
class AppScreensPaletteTest {

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
        assertEquals("FF12161F", hex(c.surface))
        assertEquals("FF232938", hex(c.surfaceBorder))
        assertEquals("FF161B26", hex(c.chipBg))
        assertEquals("FF0B0D12", hex(c.fieldBg))
        assertEquals("FF2A3142", hex(c.fieldBorder))
        assertEquals("FF2F6FED", hex(c.primary))
        assertEquals("FF161F3A", hex(c.primaryTint))
        assertEquals("FF7FB0FF", hex(c.link))
        assertEquals("FF7D869A", hex(c.textMuted))
        assertEquals("FF566078", hex(c.textFaint))
        assertEquals("FFE5C07B", hex(c.warning))
        assertEquals("FF16382A", hex(c.statusAddedBg))
        assertEquals("FF3A3115", hex(c.statusModifiedBg))
        assertEquals("FF3A1A1F", hex(c.statusDeletedBg))
        assertEquals("FF1B2850", hex(c.fileRowActive))
        assertEquals("FFE5784A", hex(c.htmlIcon))
        assertEquals("FF12151D", hex(c.keyRowBg))
        assertEquals("FF1A1F2B", hex(c.cmdButtonBg))
        assertEquals("FF12172A", hex(c.cmdAddBg))
    }
}
