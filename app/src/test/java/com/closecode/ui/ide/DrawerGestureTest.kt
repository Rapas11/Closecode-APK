package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gesture that opens the drawer.
 *
 * The swipe itself needs a device to be worth anything, but the rule it obeys does not: a pull only
 * counts when it starts in the band at the left edge and travels far enough to the right. A drag
 * anywhere else is the editor's own scroll, and a nudge inside the band is a mis-tap — neither may
 * open a drawer over the file the user was reading.
 */
class DrawerGestureTest {

    @Test
    fun onlyARightwardPullFromTheEdgeOpensTheDrawer() {
        assertTrue(shouldOpenFromSwipe(startXPx = 10f, edgePx = 28f, dxPx = 40f, slopPx = 18f))
        assertTrue(shouldOpenFromSwipe(startXPx = 28f, edgePx = 28f, dxPx = 18f, slopPx = 18f))

        // A drag that starts clear of the edge is the editor's own horizontal scroll.
        assertFalse(shouldOpenFromSwipe(startXPx = 300f, edgePx = 28f, dxPx = 40f, slopPx = 18f))

        // A nudge that never leaves the band is a mis-tap on the side of the phone.
        assertFalse(shouldOpenFromSwipe(startXPx = 10f, edgePx = 28f, dxPx = 4f, slopPx = 18f))

        // Leftward never opens — that is the drawer closing, or a scroll back to the start.
        assertFalse(shouldOpenFromSwipe(startXPx = 10f, edgePx = 28f, dxPx = -40f, slopPx = 18f))
    }

    @Test
    fun theEdgeBandIsNarrowEnoughToLeaveTheRestOfTheScreenAlone() {
        // Narrower than a thumb's reach across the display, wide enough that a pull aimed at the
        // edge does not have to be aimed precisely.
        assertEquals(28f, DrawerEdgeBand.value, 0f)
    }
}
