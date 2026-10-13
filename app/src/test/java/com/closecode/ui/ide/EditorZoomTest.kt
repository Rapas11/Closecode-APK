package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pinch zoom, in the two numbers it is made of.
 *
 * The gesture itself needs a device to be worth anything, but the rule it obeys does not: the size
 * the settings chose is the base, the pinch multiplies it, and neither end of the range may be
 * crossed. Those are plain functions, so they are pinned here.
 */
class EditorZoomTest {

    @Test
    fun withNobodyPinchingTheSettingDecidesTheSize() {
        // The fixed constant the setting replaced was 13 sp, and it must still be 13 sp.
        assertEquals(13, zoomedFontSize(13, 1f))
        assertEquals(11, zoomedFontSize(11, 1f))
        assertEquals(20, zoomedFontSize(20, 1f))
    }

    @Test
    fun aPinchMultipliesTheSizeTheSettingChose() {
        assertEquals(26, zoomedFontSize(13, 2f))
        assertEquals(7, zoomedFontSize(13, 0.5f))
        // Rounded to the whole points a font is drawn in, in both directions.
        assertEquals(8, zoomedFontSize(13, 0.6f))
        assertEquals(31, zoomedFontSize(13, 2.4f))
    }

    @Test
    fun thePinchStopsAtBothEndsOfItsRange() {
        assertEquals(0.6f, EditorScaleRange.start, 0f)
        assertEquals(2.4f, EditorScaleRange.endInclusive, 0f)
    }
}
