package com.closecode.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words the two settings controls draw.
 *
 * Neither is a Compose test: what a row says is decided by a plain function, so it is pinned here
 * where it can be read. The picture these tests guard is a settings screen where a switch tells
 * you its state instead of leaving you to decode which way the knob points, and where the option
 * in force is marked by something other than the shade of its text.
 */
class SettingsControlsTest {

    @Test
    fun aSwitchSaysWhetherItIsOn() {
        assertEquals("On", toggleStateLabel(true))
        assertEquals("Off", toggleStateLabel(false))
    }

    @Test
    fun theChoiceInForceCarriesATick() {
        assertEquals("✓ 80%", optionLabel("80", selected = true, unit = "%"))
        assertEquals("✓ Off", optionLabel("Off", selected = true, unit = ""))
    }

    @Test
    fun aChoiceNotInForceIsLeftAlone() {
        assertEquals("80%", optionLabel("80", selected = false, unit = "%"))
        assertEquals("120", optionLabel("120", selected = false, unit = ""))
    }

    @Test
    fun everyToggleStillHasSomewhereToSayItsState() {
        // Word wrap used to be one of the rows with no key behind it: a switch that drew, and said
        // On or Off, and changed nothing at all. It is wired now, so one fewer row is a placeholder.
        val toggles = sections(SettingsLive())
            .flatMap { section -> section.groups.flatMap { it.rows } }
            .filterIsInstance<Row.Toggle>()

        assertEquals(20, toggles.size)
        assertEquals(8, toggles.count { it.key == null })
        assertTrue(toggles.any { it.name == "Word wrap" && it.key == BoolSetting.WORD_WRAP })
        assertTrue(toggles.any { it.name == "Rainbow brackets" && it.key == BoolSetting.RAINBOW_BRACKETS })
        assertTrue(toggles.any { it.name == "Frame stats" && it.key == BoolSetting.FRAME_STATS })
        assertEquals("On", toggleStateLabel(true))
        assertEquals("Off", toggleStateLabel(false))
    }
}
