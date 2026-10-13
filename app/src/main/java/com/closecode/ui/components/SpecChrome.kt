package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

/**
 * The one popup-menu style from the screens update spec: menuBg fill, 1 dp border,
 * 12 dp radius, 8 dp elevation, 6 dp vertical padding. Groups of [SpecMenuItem] are
 * separated by [SpecMenuDivider]. No dim layer behind it; the caller closes on tap
 * outside or on Back.
 */
@Composable
fun SpecMenu(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .shadow(8.dp, shape)
            .clip(shape)
            .background(c.spec.menuBg)
            .border(1.dp, c.spec.menuBorder, shape)
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
fun ColumnScope.SpecMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val c = LocalPageColors.current
    val tint = when {
        !enabled -> c.textFaint
        danger -> c.spec.menuDanger
        else -> c.iconTint
    }
    val textColor = when {
        !enabled -> c.textFaint
        danger -> c.spec.menuDanger
        else -> c.brightText
    }
    Row(
        Modifier
            .height(40.dp)
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 13.5.sp,
            fontFamily = Sans,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ColumnScope.SpecMenuDivider() {
    val c = LocalPageColors.current
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(c.spec.menuDivider),
    )
}

/**
 * The back button from the spec: 36 dp, 10 dp radius, 18 dp chevron, 12 dp gap to
 * the title (the caller adds the gap). Does exactly what system Back does.
 */
@Composable
fun PageBackButton(onBack: () -> Unit, label: String = "Back") {
    val c = LocalPageColors.current
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.chipBg)
            .clickable(onClick = onBack)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = com.closecode.ui.ide.CcIcons.ChevronLeft,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Short commit-code chip: hashChipBg fill, blue mono text. */
@Composable
fun HashChip(code: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(c.spec.hashChipBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code,
            color = c.spec.hashChipText,
            fontSize = 11.sp,
            fontFamily = com.closecode.ui.theme.Mono,
            maxLines = 1,
        )
    }
}
