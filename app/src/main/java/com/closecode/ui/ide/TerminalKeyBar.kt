package com.closecode.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.settings.KeyValueStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono

/** The keys a phone keyboard cannot send, and which a shell cannot do without. */
internal val TerminalKeyBarKeys: List<Pair<String, String>> = listOf(
    "ESC" to "",
    "TAB" to "	",
    "|" to "|",
    "~" to "~",
    "/" to "/",
    "↑" to "↑",
    "↓" to "↓",
)

/** The four tinted keys: ESC and TAB with the modifiers, armed blue like the modifiers. */
private val TintedKeys = setOf("ESC", "TAB")

/**
 * The bar of terminal keys that sits above the soft keyboard.
 *
 * Nine keys, in spec order: ESC, TAB, CTRL, ALT, pipe, tilde, slash, up, down. A phone keyboard
 * has no ESC, no pipe and no arrows, so a shell on one is missing exactly the keys that make it
 * navigable. CTRL and ALT latch rather than toggling off, so a shortcut is one tap plus one key.
 * Clear lives in the Commands sheet and stopping is the header's Stop button, so neither takes
 * a key here.
 *
 * It is always on rather than appearing on demand, because a bar that hides itself is a bar that
 * cannot be found with a thumb.
 */
@Composable
internal fun TerminalKeyBar(
    ctrl: ModifierLatch,
    alt: ModifierLatch,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onKey: (String) -> Unit,
    onHistory: (Int) -> Unit,
    onCommands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Column(modifier.fillMaxWidth().background(c.keyRowBg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Row(
            Modifier
                .fillMaxWidth()
                // A vertical drag anywhere on the bar walks the command history, and nothing else
                // claims it: the buttons take taps, and a drag is told apart from a tap by slop
                // rather than by where it landed.
                .verticalSwipeTo(onHistory)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The bolt opens Commands; it looks like the other dark keys, not the blue four.
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(c.chipBg)
                    .border(1.dp, Color.Transparent, RoundedCornerShape(7.dp))
                    .clickable(onClick = onCommands)
                    .padding(horizontal = 13.dp)
                    .semantics { contentDescription = "Saved commands" },
                contentAlignment = Alignment.Center,
            ) {
                CcIcon(CcIcons.Zap, c.textMuted, 15.dp)
            }
            PlainKey("ESC", tinted = true) { onKey("\u001B") }
            PlainKey("TAB", tinted = true) { onKey("	") }
            ModifierKey("CTRL", ctrl, onCtrl)
            ModifierKey("ALT", alt, onAlt)
            TerminalKeyBarKeys.drop(2).forEach { (label, key) ->
                PlainKey(label, tinted = false) { onKey(key) }
            }
        }
    }
}

/**
 * A vertical drag on the bar, reported once it is clear the reader meant it.
 *
 * Only vertical travel commits, and only after it has passed the touch slop, so a tap on one of the
 * buttons — which is the bar's other job — is never mistaken for a scroll.
 */
private fun Modifier.verticalSwipeTo(onSwipe: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        val slop = viewConfiguration.touchSlop
        var travelled = 0f
        var swiping = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || !change.pressed || event.changes.count { it.pressed } > 1) break
            val delta = change.positionChange()
            if (!swiping) {
                travelled += delta.y
                if (kotlin.math.abs(travelled) > slop) swiping = true
            }
            if (swiping) {
                change.consume()
                if (kotlin.math.abs(travelled) > slop) {
                    // One step per slop of travel, so a short flick moves one entry and a long drag
                    // moves as many as the finger travelled rather than the one it meant.
                    onSwipe(if (travelled > 0) 1 else -1)
                    travelled = if (travelled > 0) (travelled % slop) else -(-travelled % slop)
                }
            }
        }
    }
}

/** One modifier key: identical to ESC/TAB, with an outline only while armed. */
@Composable
private fun ModifierKey(label: String, latch: ModifierLatch, onTap: () -> Unit) {
    val c = LocalPageColors.current
    val armed = modifierApplies(latch)
    val locked = latch == ModifierLatch.LOCKED
    // Same fill as its neighbours either way; locked is drawn darker on top of it so armed
    // ("the next key") and locked ("every key") still read apart. The outline is the armed
    // signal and appears only then, so an idle row is four flat blue keys.
    val fill = when {
        locked -> c.accent.copy(alpha = 0.45f)
        else -> c.primaryTint
    }
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(fill)
            .border(
                1.dp,
                if (armed) c.accent else Color.Transparent,
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 13.dp)
            .semantics { contentDescription = "$label ${latch.name.lowercase()}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = c.link,
            fontSize = 12.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** One ordinary key: 34 dp tall, blue like the modifiers for ESC/TAB, chip gray for the rest. */
@Composable
private fun PlainKey(label: String, tinted: Boolean, onTap: () -> Unit) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (tinted) c.primaryTint else c.chipBg)
            .border(1.dp, Color.Transparent, RoundedCornerShape(7.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 13.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (tinted) c.link else c.popupText,
            fontSize = 12.sp,
            fontFamily = Mono,
            fontWeight = if (tinted) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * Commands remembered for one project.
 *
 * Stored in the app's own preferences under the project's slug, never in the project folder: a file
 * there would appear in the file drawer and in Source Control, and a log of what somebody typed at
 * their shell is not something to put in front of them unasked, let alone in a commit.
 *
 * The preference store is wrapped rather than reached for directly so this can be tested against an
 * in-memory map, because `SharedPreferences` is not on the unit-test classpath.
 */
internal class TerminalHistoryStore(private val kv: KeyValueStore) {

    /** Newest first, exactly as it is kept, so reading one back needs no reversing. */
    fun read(slug: String): List<String> =
        kv.getString(key(slug), null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun write(slug: String, history: List<String>) {
        if (history.isEmpty()) {
            kv.remove(key(slug))
        } else {
            kv.putString(key(slug), history.take(TERMINAL_HISTORY_LIMIT).joinToString("\n"))
        }
    }

    private fun key(slug: String) = "terminal.history.$slug"
}

/** A store for [slug], remembered for as long as this screen is composed. */
@Composable
internal fun rememberTerminalHistory(slug: String): TerminalHistoryStore {
    val context = LocalContext.current
    return remember(slug) { TerminalHistoryStore(SharedPreferencesKeyValueStore(context)) }
}