package com.closecode.ui.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every padding the gutter computes must be zero or more: negative padding throws
 * IllegalArgumentException during composition, which kills the app the moment the row draws.
 * That is exactly what the quick-fix bulb did — its centred tap area started at -3 dp, so
 * turning diagnostics on crashed the editor as soon as the caret sat on a problem line.
 */
class GutterGeometryTest {

    @Test
    fun bulbTapAreaStartsInsideTheGutter() {
        assertTrue(GutterBulbTapLeft >= androidx.compose.ui.unit.Dp(0f))
    }

    @Test
    fun bulbTileKeepsItsSlotInsideTheTapArea() {
        val tileLeft = GutterBulbLeft - GutterBulbTapLeft
        assertTrue(tileLeft >= androidx.compose.ui.unit.Dp(0f))
        assertTrue(tileLeft + GutterBulbSize <= GutterBulbTapLeft + GutterBulbTapWidth)
    }

    @Test
    fun foldTapAreaStartsInsideTheGutter() {
        assertTrue(GutterFoldTapLeft >= androidx.compose.ui.unit.Dp(0f))
    }

    @Test
    fun foldTileKeepsItsSlotInsideTheTapArea() {
        val tileLeft = GutterFoldLeft - GutterFoldTapLeft
        assertTrue(tileLeft >= androidx.compose.ui.unit.Dp(0f))
        assertTrue(tileLeft + GutterFoldSize <= GutterFoldTapLeft + GutterFoldTapWidth)
    }
}
