package com.closecode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WebGlyph(color: Color, size: Dp = 21.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        drawCircle(
            color = color,
            radius = w / 2f - stroke / 2f,
            style = Stroke(stroke),
        )
        drawLine(
            color = color,
            start = Offset(stroke / 2f, h / 2f),
            end = Offset(w - stroke / 2f, h / 2f),
            strokeWidth = stroke,
        )
        drawOval(
            color = color,
            topLeft = Offset(w / 2f - w * 0.23f, stroke / 2f),
            size = Size(w * 0.46f, h - stroke),
            style = Stroke(stroke),
        )
    }
}

@Composable
fun PhoneGlyph(color: Color, size: Dp = 21.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(w * 0.18f),
            style = Stroke(stroke),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.34f, h * 0.83f),
            end = Offset(w * 0.66f, h * 0.83f),
            strokeWidth = stroke * 0.95f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CodeGlyph(color: Color, size: Dp = 19.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        val cap = StrokeCap.Round
        drawLine(color, Offset(w * 0.32f, h * 0.2f), Offset(w * 0.1f, h * 0.5f), stroke, cap = cap)
        drawLine(color, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.32f, h * 0.8f), stroke, cap = cap)
        drawLine(color, Offset(w * 0.68f, h * 0.2f), Offset(w * 0.9f, h * 0.5f), stroke, cap = cap)
        drawLine(color, Offset(w * 0.9f, h * 0.5f), Offset(w * 0.68f, h * 0.8f), stroke, cap = cap)
    }
}

@Composable
fun CheckGlyph(color: Color, size: Dp = 15.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.14f
        drawLine(
            color = color,
            start = Offset(w * 0.18f, h * 0.52f),
            end = Offset(w * 0.42f, h * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.42f, h * 0.76f),
            end = Offset(w * 0.84f, h * 0.26f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun ArrowGlyph(color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        drawLine(
            color = color,
            start = Offset(w * 0.12f, h * 0.78f),
            end = Offset(w * 0.84f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.58f, h * 0.22f),
            end = Offset(w * 0.84f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.58f, h * 0.78f),
            end = Offset(w * 0.84f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun PlusGlyph(color: Color, size: Dp = 19.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.13f
        drawLine(color, Offset(w / 2f, h * 0.18f), Offset(w / 2f, h * 0.82f), stroke)
        drawLine(color, Offset(w * 0.18f, h / 2f), Offset(w * 0.82f, h / 2f), stroke)
    }
}

@Composable
fun CrossGlyph(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        val cap = StrokeCap.Round
        drawLine(color, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), stroke, cap = cap)
        drawLine(color, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), stroke, cap = cap)
    }
}