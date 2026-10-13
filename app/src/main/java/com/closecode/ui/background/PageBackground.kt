package com.closecode.ui.background

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.closecode.ui.theme.LocalPageColors

private const val GRID = 56f
private const val LINE_ALPHA = 140

private fun DrawScope.ellipse(
    paint: Paint,
    centerX: Float,
    centerY: Float,
    radiusX: Float,
    radiusY: Float,
) {
    drawIntoCanvas { wrapper ->
        val canvas = wrapper.nativeCanvas
        val backup = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(1f, if (radiusX > 0f) radiusY / radiusX else 1f)
        canvas.drawCircle(0f, 0f, radiusX, paint)
        canvas.restoreToCount(backup)
    }
}

@Composable
fun PageBackground(modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val bgArgb = c.bg.toArgb()
    val lineArgb = c.ln.toArgb()
    val glowArgb = c.glow.toArgb()

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawIntoCanvas { wrapper ->
                val canvas = wrapper.nativeCanvas

                canvas.drawColor(bgArgb)

                canvas.saveLayer(0f, 0f, w, h, null)

                val glow = Paint().apply {
                    isAntiAlias = true
                    shader = RadialGradient(
                        0f, 0f,
                        maxOf(w * 0.6f, 1f),
                        intArrayOf(glowArgb, Color.Transparent.toArgb()),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                ellipse(glow, w / 2f, 0f, w * 0.6f, h * 0.5f)

                val grid = Paint().apply {
                    isAntiAlias = false
                    strokeWidth = 1f
                    color = lineArgb
                    alpha = LINE_ALPHA
                }
                var x = 0f
                while (x <= w) {
                    canvas.drawLine(x, 0f, x, h, grid)
                    x += GRID
                }
                var y = 0f
                while (y <= h) {
                    canvas.drawLine(0f, y, w, y, grid)
                    y += GRID
                }

                val mask = Paint().apply {
                    isAntiAlias = true
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    shader = RadialGradient(
                        0f, 0f,
                        maxOf(w * 0.7f, 1f),
                        intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                ellipse(mask, w / 2f, h * 0.3f, w * 0.7f, h * 0.6f)

                canvas.restore()
            }
        }
    }
}