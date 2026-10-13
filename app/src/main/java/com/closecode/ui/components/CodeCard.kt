package com.closecode.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.PageColors

private fun kotlinSnippet(c: PageColors): List<AnnotatedString> {
    val kw = SpanStyle(color = c.keyword)
    val fn = SpanStyle(color = c.fn)
    val st = SpanStyle(color = c.string)
    val cm = SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)

    return listOf(
        buildAnnotatedString { withStyle(cm) { append("// Your first app, running on your phone") } },
        buildAnnotatedString {
            withStyle(kw) { append("class") }
            append(' ')
            withStyle(fn) { append("MainActivity") }
            append(" : ")
            withStyle(fn) { append("ComponentActivity") }
            append("() {")
        },
        buildAnnotatedString {
            withStyle(kw) { append("override fun") }
            append(' ')
            withStyle(fn) { append("onCreate") }
            append("(state: Bundle?) {")
        },
        buildAnnotatedString {
            withStyle(kw) { append("super") }
            append('.')
            withStyle(fn) { append("onCreate") }
            append("(state)")
        },
        buildAnnotatedString {
            withStyle(fn) { append("setContent") }
            append(" { ")
            withStyle(fn) { append("Text") }
            append('(')
            withStyle(st) { append("\"Hello, world\"") }
            append(") }")
        },
        buildAnnotatedString { append("}") },
        buildAnnotatedString { append("}") },
    )
}

@Composable
private fun Caret(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caret-alpha",
    )
    Box(
        Modifier
            .padding(start = 1.dp, bottom = 3.dp)
            .size(width = 8.dp, height = 15.dp)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun CodeCard(modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val lines = kotlinSnippet(c)
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .border(1.dp, c.ln, shape)
            .background(c.cd, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, c.ln, shape)
                .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .padding(end = 7.dp)
                        .size(10.dp)
                        .background(c.ln, RoundedCornerShape(5.dp))
                )
            }
            Text(
                text = "MainActivity.kt",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Mono,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)
        ) {
            lines.forEachIndexed { index, text ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = text,
                        color = c.tx,
                        fontSize = 13.sp,
                        lineHeight = 23.4.sp,
                        fontFamily = Mono,
                        softWrap = false,
                    )
                    if (index == CARET_LINE) Caret(c.ac)
                }
            }
        }
    }
}

private const val CARET_LINE = 4