package com.closecode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.CheckGlyph
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Mono
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.delay

private sealed interface Stage {
    data object Checking : Stage
    data object Downloading : Stage
    data object Finalizing : Stage
    data object Creating : Stage
}

@Composable
fun ProvisioningScreen(
    webProject: Boolean,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = LocalPageColors.current
    var stage by remember { mutableStateOf<Stage>(Stage.Checking) }
    var finished by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = when (stage) {
            Stage.Downloading -> 0.82f
            Stage.Finalizing -> 1f
            else -> 0f
        },
        animationSpec = tween(1400),
        label = "download",
    )

    LaunchedEffect(webProject) {
        val checks = if (webProject) 1 else 3
        repeat(checks) { delay(450) }
        stage = if (webProject) Stage.Finalizing else Stage.Downloading
        delay(if (webProject) 300 else 1500)
        stage = Stage.Finalizing
        delay(1200)
        stage = Stage.Creating
        delay(700)
        finished = true
        delay(400)
        if (!dismissed) onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Preparing your development environment",
                    color = c.tx,
                    fontSize = 20.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 26.sp,
                )
                Text(
                    text = if (webProject) {
                        "This only takes a moment."
                    } else {
                        "Checking the tools this project needs."
                    },
                    color = c.mu,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        dismissed = true
                        onSkip()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Skip",
                    color = c.mu,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 36.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!webProject) {
                CheckLine("Checking JDK", stage != Stage.Checking)
                CheckLine("Checking Android SDK", stage != Stage.Checking)
                CheckLine("Checking Build Tools", stage != Stage.Checking)

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Downloading required components...",
                    color = c.mu,
                    fontSize = 13.5.sp,
                    fontFamily = Mono,
                )
                Spacer(Modifier.height(10.dp))
                ProgressTrack(progress = progress)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = c.mu.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = Mono,
                )

                Spacer(Modifier.height(24.dp))

                CheckLine("Gradle", stage != Stage.Checking && stage != Stage.Downloading)
                CheckLine("Android SDK", stage != Stage.Checking && stage != Stage.Downloading)
                CheckLine("Required dependencies", stage != Stage.Checking && stage != Stage.Downloading)
            } else {
                CheckLine("Project files", stage != Stage.Checking)
                Spacer(Modifier.height(18.dp))
                ProgressTrack(progress = progress)
            }

            AnimatedVisibility(
                visible = stage == Stage.Creating || finished,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Creating project...",
                        color = c.mu,
                        fontSize = 13.5.sp,
                        fontFamily = Mono,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckLine(label: String, done: Boolean) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            CheckGlyph(color = c.string, size = 15.dp)
        } else {
            Box(Modifier.size(15.dp))
        }
        Text(
            text = label,
            color = if (done) c.tx else c.mu,
            fontSize = 13.5.sp,
            fontFamily = Mono,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ProgressTrack(progress: Float) {
    val c = LocalPageColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.panel3)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(c.ac)
        )
    }
}