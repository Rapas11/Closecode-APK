package com.closecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions






import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.ProjectTemplate
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans

private val ColumnGap = 12.dp
private const val MIN_TILE = 220

@Composable
fun TemplateCard(
    template: ProjectTemplate,
    selected: Boolean,
    tab: TemplateTab,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(16.dp)
    val isWeb = tab == TemplateTab.Web

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cd)
            .border(
                width = 1.dp,
                color = if (selected) c.ac else c.ln,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(
                    Color.hsl(template.hue, 0.65f, 0.55f, 0.16f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isWeb) {
                WebGlyph(color = Color.hsl(template.hue, 0.65f, 0.48f))
            } else {
                PhoneGlyph(color = Color.hsl(template.hue, 0.65f, 0.48f))
            }
        }
        Text(
            text = template.name,
            color = c.tx,
            fontSize = 16.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp,
        )
        Text(
            text = template.description,
            color = c.mu,
            fontSize = 13.5.sp,
            fontFamily = Sans,
            lineHeight = 19.6.sp,
        )
        Row(Modifier.padding(top = 4.dp)) {
            Box(
                Modifier
                    .border(1.dp, c.ln, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = template.tags,
                    color = c.mu,
                    fontSize = 11.5.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) c.ab else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (active) c.af else c.mu,
            fontSize = 14.5.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SegmentedTabs(
    tab: TemplateTab,
    onSelect: (TemplateTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .border(1.dp, c.ln, RoundedCornerShape(12.dp))
            .background(c.cd, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TemplateTab.entries.forEach { entry ->
            TabChip(
                label = entry.label,
                active = entry == tab,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@Composable
fun RecentHeader(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RECENT",
            color = c.mu.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count projects",
            color = c.mu,
            fontSize = 12.sp,
            fontFamily = Sans,
        )
        ArrowGlyph(color = c.mu, size = 14.dp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun TemplateGrid(
    tab: TemplateTab,
    selected: SelectedTemplate?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecent: (() -> Unit)? = null,
    recentCount: Int = 0,
) {
    val items = Templates[tab].orEmpty()

    BoxWithConstraints(modifier) {
        val available = maxWidth - 44.dp
        val span = ((available + ColumnGap) / (MIN_TILE.dp + ColumnGap))
            .toInt()
            .coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(span),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
            verticalArrangement = Arrangement.spacedBy(ColumnGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onOpenRecent != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                    RecentHeader(count = recentCount, onClick = onOpenRecent)
                }
            }
            itemsIndexed(items) { index, template ->
                TemplateCard(
                    template = template,
                    selected = selected != null && selected.tab == tab && selected.index == index,
                    tab = tab,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
fun SheetFooter(
    name: String,
    onNameChange: (String) -> Unit,
    canCreate: Boolean,
    hint: String,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Create project",
    showNameField: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = LocalPageColors.current
    val fieldShape = RoundedCornerShape(12.dp)
    val buttonShape = RoundedCornerShape(12.dp)

    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = c.ln,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
            .background(c.bg)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showNameField) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                    .clip(fieldShape)
                    .background(c.cd)
                    .border(1.dp, c.ln, fieldShape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = "Project name",
                        color = c.mu,
                        fontSize = 16.sp,
                        fontFamily = Sans,
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = c.tx,
                        fontSize = 16.sp,
                        fontFamily = Sans,
                    ),
                    cursorBrush = SolidColor(c.ac),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (secondaryLabel != null && onSecondary != null) {
                Row(
                    Modifier
                        .height(46.dp)
                        .clip(buttonShape)
                        .border(1.dp, c.ln, buttonShape)
                        .clickable(onClick = onSecondary)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = secondaryLabel,
                        color = c.tx,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(
                Modifier
                    .height(46.dp)
                    .clip(buttonShape)
                    .background(
                        if (canCreate) c.ab else c.mu.copy(alpha = 0.28f)
                    )
                    .clickable(enabled = canCreate, onClick = onCreate)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                PlusGlyph(color = if (canCreate) c.af else c.mu)
                Text(
                    text = actionLabel,
                    color = if (canCreate) c.af else c.mu,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Text(
            text = hint,
            color = c.mu,
            fontSize = 13.5.sp,
            fontFamily = Sans,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SheetHeader(
    onClose: () -> Unit,
    title: String = "Start a project",
    subtitle: String = "Pick a template to begin. You can change anything later.",
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.tx,
                fontSize = 22.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp,
            )
            Text(
                text = subtitle,
                color = c.mu,
                fontSize = 14.5.sp,
                fontFamily = Sans,
                lineHeight = 21.sp,
            )
        }
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.cd)
                .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            CrossGlyph(color = c.mu)
        }
    }
}