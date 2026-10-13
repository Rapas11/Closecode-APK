package com.closecode.ui.ide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the soft keyboard is open, measured from the window height rather than the inset
 * APIs. The visible display frame shrinks by the keyboard's height while the insets on some
 * devices report zero height and a stuck visible flag — the combination that once froze the
 * key row, the bottom bar and Back all at once. A ratio threshold means rotation needs no
 * re-baseline: a keyboard takes a large fraction of the height, bars and cutouts never do.
 * Floating keyboards under the threshold read closed; that limitation is stated, not hidden.
 */
object KeyboardVisibility {
    var open by mutableStateOf(false)
        private set

    /** The obscured height in px, for the log columns. Zero when closed. */
    var heightPx by mutableIntStateOf(0)
        private set

    fun update(visibleHeight: Int, rootHeight: Int) {
        if (rootHeight <= 0 || visibleHeight <= 0) return
        val obscured = (rootHeight - visibleHeight).coerceAtLeast(0)
        heightPx = obscured
        open = obscured > rootHeight * 0.15f
    }
}
