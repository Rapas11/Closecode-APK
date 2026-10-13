package com.closecode.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.build.ToolchainManifest
import com.closecode.build.ToolchainState
import com.closecode.build.ToolchainStore
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.launch

/**
 * First-setup download: the compiler pack over Wi-Fi, once, then offline. Shows
 * real progress per file, verifies hashes, and states the aapt2 license before
 * anything is fetched: it arrives as a user SDK download (SDK license, like
 * AndroidIDE), or as an AOSP build made here if that source ever goes unclear.
 */
@Composable
fun ToolchainSetupScreen(
    store: ToolchainStore,
    onClose: () -> Unit,
    onDone: () -> Unit = {},
) {
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val state by store.state.collectAsState()
    val totalMb = ToolchainManifest.totalBytes / 1_048_576

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Build tools",
            color = c.brightText,
            fontSize = 20.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "About $totalMb MB over Wi-Fi, once. Afterwards your phone builds " +
                "fully offline. Downloads resume where they stopped.",
            color = c.textMuted,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))
        when (val current = state) {
            ToolchainState.NotStarted -> {
                ToolComponentList()
                Spacer(Modifier.height(16.dp))
                PrimaryButton(label = "Download", onClick = { scope.launch { store.ensureAll() } })
            }
            is ToolchainState.Downloading -> {
                val doneMb = current.doneBytes / 1_048_576
                Text(
                    text = "${current.current} · $doneMb of $totalMb MB",
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.panel2),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(
                                (current.doneBytes.toFloat() / ToolchainManifest.totalBytes)
                                    .coerceIn(0f, 1f),
                            )
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.accent),
                    )
                }
            }
            is ToolchainState.Verifying -> {
                Text(
                    text = "Checking ${current.fileName}…",
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }
            ToolchainState.Ready -> {
                Text(
                    text = "Ready — your phone can build offline.",
                    color = c.success,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(label = "Done", onClick = { onDone(); onClose() })
            }
            is ToolchainState.Failed -> {
                Text(
                    text = current.message,
                    color = c.error,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(label = "Try again", onClick = { scope.launch { store.ensureAll() } })
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Licenses",
            color = c.brightText,
            fontSize = 14.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Kotlin compiler (Apache 2.0), ECJ (EPL-2.0, used unmodified), " +
                "R8 marches under BSD-3, apksig under Apache 2.0. aapt2 arrives as your " +
                "own SDK download under the SDK license, exactly like AndroidIDE fetches " +
                "it — or as an AOSP build made here if that source ever goes unclear. " +
                "Native tools ship inside this app and never run from writable storage.",
            color = c.textFaint,
            fontSize = 11.sp,
            fontFamily = Sans,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onClose)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Close", color = c.textMuted, fontSize = 14.sp, fontFamily = Sans)
        }
    }
}

@Composable
private fun ToolComponentList() {
    val c = LocalPageColors.current
    Column {
        ToolchainManifest.components.forEach { component ->
            Text(
                text = "${component.id} ${component.version} · ${component.sizeBytes / 1_048_576} MB",
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        Text(
            text = "plus arm64 aapt2 ${ToolchainManifest.AAPT2_VERSION} (SDK download)",
            color = c.text,
            fontSize = 13.sp,
            fontFamily = Sans,
            modifier = Modifier.padding(vertical = 3.dp),
        )
    }
}
