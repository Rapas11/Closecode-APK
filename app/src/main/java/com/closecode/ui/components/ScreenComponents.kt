package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

/**
 * The shared building blocks of the app screens design spec, so every screen reads as one app:
 * one segmented control, one primary button, one card, one field, one badge.
 *
 * Each matches its spec row exactly; anything the spec leaves open (disabled colours, ellipsis)
 * follows the calm-dark defaults used elsewhere.
 */

/** One item in [AppSegmentedControl]: the label and an optional count bubble (0 hides it). */
data class SegmentItem(val label: String, val count: Int = 0)

/**
 * The tab switcher shared by the explorer drawer and the preview views: a chipBg container with
 * 3 dp padding, 28 dp items, and the selected item on popupSelected. A positive [SegmentItem.count]
 * draws the 16 dp blue count bubble after the label.
 */
@Composable
fun AppSegmentedControl(
    items: List<SegmentItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.chipBg)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) c.popupSelected else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.label,
                        color = if (on) Color.White else c.textMuted,
                        fontSize = 12.sp,
                        fontFamily = Sans,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                    // The bubble counts changed files; zero means nothing changed, so no bubble.
                    if (item.count > 0) {
                        Box(
                            Modifier
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.accent)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = item.count.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = Sans,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The one large blue button a screen may have: 44 dp tall, radius 10, 14sp medium white text.
 * Disabled (nothing to do yet) is chipBg with faint text, never a second blue.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) c.primary else c.chipBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else c.textFaint,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Outline button: 38 dp, primary border on a primaryTint fill, link text. */
@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) c.primaryTint else c.chipBg)
            .border(1.dp, if (enabled) c.primary else c.fieldBorder, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) c.link else c.textFaint,
            fontSize = 13.sp,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Text button: no background, link text. Destructive actions pass the error color instead. */
@Composable
fun TextLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val c = LocalPageColors.current
    Box(
        modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color ?: c.link,
            fontSize = 12.sp,
            fontFamily = Sans,
            maxLines = 1,
        )
    }
}

/** Card: surface fill, 1 dp border, radius 12, 12 dp padding. */
@Composable
fun ScreenCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.surfaceBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        content = content,
    )
}

/**
 * Text field: 44 dp, radius 10, fieldBg with a fieldBorder edge, 12 dp side padding.
 * Monospace opt-in for links, codes and paths.
 */
@Composable
fun ScreenField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.fieldBg)
            .border(1.dp, c.fieldBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(
                color = c.text,
                fontSize = 13.sp,
                fontFamily = if (monospace) Mono else Sans,
            ),
            keyboardOptions = keyboardOptions,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        color = c.textFaint,
                        fontSize = 13.sp,
                        fontFamily = if (monospace) Mono else Sans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )
    }
}

/** Which Git letter a [StatusBadge] shows, with its spec colors. */
enum class StatusKind { Added, Modified, Deleted, Untracked }

/**
 * The 18 dp Git badge: A on green, M on brown-yellow, D on dark red, U gray. The letter is what
 * carries the meaning, so the badge keeps the letter at 10sp medium even at this size.
 */
@Composable
fun StatusBadge(kind: StatusKind, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val (bg, fg, letter) = when (kind) {
        StatusKind.Added -> Triple(c.statusAddedBg, c.gitAdded, "A")
        StatusKind.Modified -> Triple(c.statusModifiedBg, c.warning, "M")
        StatusKind.Deleted -> Triple(c.statusDeletedBg, c.error, "D")
        StatusKind.Untracked -> Triple(c.chipBg, c.textMuted, "U")
    }
    Box(
        modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = fg,
            fontSize = 10.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Section label: 11sp uppercase, wide tracking, faint. */
@Composable
fun ScreenSectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Text(
        text = text.uppercase(),
        color = c.textFaint,
        fontSize = 11.sp,
        fontFamily = Sans,
        letterSpacing = 0.72.sp,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Option card row used by the Git intro and clone screens: icon tile, title, subtitle, chevron. */
@Composable
fun RowScope.OptionCardText(title: String, subtitle: String) {
    val c = LocalPageColors.current
    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
        Text(
            text = title,
            color = c.text,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = c.textMuted,
            fontSize = 11.sp,
            fontFamily = Sans,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
