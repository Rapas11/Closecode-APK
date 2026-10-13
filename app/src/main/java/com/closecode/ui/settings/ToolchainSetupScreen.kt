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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import com.closecode.build.ToolchainManifest
import com.closecode.build.ToolchainState
import com.closecode.build.ToolchainStore
import com.closecode.ui.components.PrimaryButton
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import kotlinx.coroutines.launch

@Composable
fun ToolchainSetupScreen(
    store: ToolchainStore,
    onClose: () -> Unit,
    onDone: () -> Unit = {},
) {
    val c = LocalPageColors.current
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var allowMobileData by remember { mutableStateOf(false) }
    var acceptedAndroidSdkLicense by remember { mutableStateOf(store.androidSdkLicenseAccepted()) }
    val downloading = state is ToolchainState.Downloading || state is ToolchainState.Verifying

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
            text = "Download checksum-verified Android and compiler source files. Downloads " +
                "resume after interruption and use Wi-Fi unless you opt in to mobile data.",
            color = c.textMuted,
            fontSize = 13.sp,
            fontFamily = Sans,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))
        ToolchainManifest.components.forEach { component ->
            Text(
                text = "${component.fileName} · ${"%.1f".format(component.sizeBytes / 1_000_000.0)} MB · ${component.license}",
                color = c.text,
                fontSize = 11.sp,
                fontFamily = Sans,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acceptedAndroidSdkLicense,
                onCheckedChange = {
                    acceptedAndroidSdkLicense = it
                    store.setAndroidSdkLicenseAccepted(it)
                },
                enabled = !downloading,
            )
            Text(
                text = "I accept the Android SDK License",
                color = c.text,
                fontSize = 12.sp,
                fontFamily = Sans,
                modifier = Modifier
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://developer.android.com/studio/terms"),
                                ),
                            )
                        } catch (_: ActivityNotFoundException) {
                            android.widget.Toast.makeText(
                                context,
                                "Could not open the Android SDK License page.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Allow mobile data downloads",
                color = c.text,
                fontSize = 13.sp,
                fontFamily = Sans,
            )
            Switch(
                checked = allowMobileData,
                onCheckedChange = { allowMobileData = it },
                enabled = !downloading,
            )
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            label = if (downloading) "Downloading…" else "Download verified artifacts",
            enabled = !downloading,
            onClick = { scope.launch { store.ensureAll(allowMobileData) } },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Important: these verified source archives are not a complete on-device " +
                "builder. Android-compatible DEX runtime files and arm64 aapt2 are still " +
                "missing, so CloseCode will not claim the toolchain is ready or produce an APK.",
            color = c.textMuted,
            fontSize = 12.sp,
            fontFamily = Sans,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))
        when (val current = state) {
            ToolchainState.NotStarted -> {
                Text(
                    text = "At least 1.5 GB of free storage is required before downloading.",
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is ToolchainState.Downloading -> {
                Text(
                    text = "${current.current} · ${current.doneBytes} bytes",
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
                    text = "Checking ${current.fileName}",
                    color = c.text,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                )
            }
            is ToolchainState.Extracting -> {
                Text(
                    text = "Unpacking ${current.fileName}",
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
            is ToolchainState.DownloadedNotReady -> {
                Text(
                    text = current.message,
                    color = c.error,
                    fontSize = 13.sp,
                    fontFamily = Sans,
                    lineHeight = 19.sp,
                )
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
            }
        }
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
