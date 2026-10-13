package com.closecode.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.model.BuildTypeOptions
import com.closecode.ui.model.GradleOptions
import com.closecode.ui.model.LanguageOptions
import com.closecode.ui.model.MinSdkOptions
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SdkOptions
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

@Composable
fun ConfigForm(
    config: ProjectConfig,
    onConfigChange: (ProjectConfig) -> Unit,
    webProject: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!webProject) {
            ConfigTextField(
                label = "Package name",
                value = config.packageName,
                onValueChange = { onConfigChange(config.copy(packageName = it)) },
            )
            ConfigDropdown(
                label = "Language",
                value = config.language,
                options = LanguageOptions,
                onSelect = { onConfigChange(config.copy(language = it)) },
            )
            ConfigDropdown(
                label = "Gradle version",
                value = config.gradleVersion,
                options = GradleOptions,
                onSelect = { onConfigChange(config.copy(gradleVersion = it)) },
            )
            ConfigDropdown(
                label = "Minimum SDK",
                value = config.minSdk,
                options = MinSdkOptions,
                onSelect = { onConfigChange(config.copy(minSdk = it)) },
            )
            ConfigDropdown(
                label = "Compile SDK",
                value = config.compileSdk,
                options = SdkOptions,
                onSelect = {
                    onConfigChange(config.copy(compileSdk = it, targetSdk = it))
                },
            )
            ConfigDropdown(
                label = "Target SDK",
                value = config.targetSdk,
                options = SdkOptions,
                onSelect = { onConfigChange(config.copy(targetSdk = it)) },
            )
        }
        ConfigDropdown(
            label = "Build configuration",
            value = config.buildType,
            options = BuildTypeOptions,
            onSelect = { onConfigChange(config.copy(buildType = it)) },
        )
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(12.dp)
    Column {
        Text(
            text = label,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(c.cd)
                .border(1.dp, c.ln, shape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                ),
                cursorBrush = SolidColor(c.ac),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConfigDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    val c = LocalPageColors.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column {
        Text(
            text = label,
            color = c.tx,
            fontSize = 13.sp,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(shape)
                    .background(c.cd)
                    .border(1.dp, c.ln, shape)
                    .clickable { open = !open }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    color = c.tx,
                    fontSize = 15.sp,
                    fontFamily = Sans,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "⌄",
                    color = c.mu,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            }

            if (open) {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 50.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(c.cd)
                        .border(1.dp, c.ln, shape)
                ) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clickable {
                                    onSelect(option)
                                    open = false
                                }
                                .background(
                                    if (option == value) c.ac.copy(alpha = 0.12f) else Color.Transparent
                                )
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option,
                                color = if (option == value) c.ac else c.tx,
                                fontSize = 14.sp,
                                fontFamily = Sans,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigSummary(config: ProjectConfig, webProject: Boolean, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val rows = if (webProject) {
        listOf("Build configuration" to config.buildType)
    } else {
        listOf(
            "Package name" to config.packageName,
            "Language" to config.language,
            "Gradle" to config.gradleVersion,
            "SDK" to "min ${config.minSdk} · target ${config.targetSdk}",
            "Build configuration" to config.buildType,
        )
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    color = c.tx,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
        }
    }
}
