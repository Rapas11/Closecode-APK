package com.closecode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.closecode.ui.components.ConfigForm
import com.closecode.ui.components.SegmentedTabs
import com.closecode.ui.components.SheetFooter
import com.closecode.ui.components.SheetHeader
import com.closecode.ui.components.TemplateGrid
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates
import com.closecode.ui.model.defaultPackageName
import com.closecode.ui.theme.LocalPageColors

private enum class Step { Pick, Config, Provisioning }

@Composable
fun StartProjectSheet(
    recentCount: Int,
    onOpenRecent: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, selection: SelectedTemplate, config: ProjectConfig) -> Unit,
) {
    val c = LocalPageColors.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val wide = screenWidth >= 720
    val maxSheetHeight = (screenHeight * if (wide) 0.86f else 0.92f).dp

    var step by rememberSaveable { mutableStateOf(Step.Pick.name) }
    var tab by rememberSaveable { mutableStateOf(TemplateTab.Web) }
    var selectedIndex by rememberSaveable { mutableStateOf(-1) }
    var selectedTab by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var configEncoded by rememberSaveable { mutableStateOf(ProjectConfig().encode()) }


    val currentStep = Step.valueOf(step)
    val config = ProjectConfig.decode(configEncoded)

    val selected = remember(selectedIndex, selectedTab) {
        if (selectedIndex >= 0 && selectedTab.isNotEmpty()) {
            SelectedTemplate(TemplateTab.valueOf(selectedTab), selectedIndex)
        } else {
            null
        }
    }

    val current = selected?.let { Templates[it.tab].orEmpty().getOrNull(it.index) }
    val canContinue = selected != null && selected.tab == tab && current != null
    val webProject = selected?.tab == TemplateTab.Web

    if (currentStep == Step.Provisioning) {
        ProvisioningScreen(
            webProject = webProject,
            onDone = {
                if (selected != null) {
                    onCreate(name, selected, config)
                } else {
                    onDismiss()
                }
            },
            onSkip = {
                if (selected != null) {
                    onCreate(name, selected, config)
                } else {
                    onDismiss()
                }
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        // System Back dismisses the sheet, exactly like the scrim tap and the X: without this
        // the press falls through to the hero's exit guard underneath.
        BackHandler(onBack = {
            com.closecode.ui.ide.BackLog.press("start-sheet", "branch=dismiss")
            onDismiss()
        })
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x8C05080E))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(tween(300)) { it / 5 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(240)) { it / 5 } + fadeOut(tween(240)),
            modifier = Modifier.align(if (wide) Alignment.Center else Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
                    .heightIn(max = maxSheetHeight)
                    .clip(
                        if (wide) {
                            RoundedCornerShape(22.dp)
                        } else {
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        }
                    )
                    .background(c.bg)
            ) {
                SheetHeader(
                    onClose = onDismiss,
                    title = if (currentStep == Step.Pick) "Start a project" else "Project configuration",
                    subtitle = if (currentStep == Step.Pick) {
                        "Pick a template to begin. You can change anything later."
                    } else {
                        "Review the settings for this project."
                    },
                )

                if (currentStep == Step.Pick) {
                    SegmentedTabs(
                        tab = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.padding(start = 22.dp, top = 18.dp),
                    )

                    TemplateGrid(
                        tab = tab,
                        selected = selected,
                        onSelect = { index ->
                            selectedIndex = index
                            selectedTab = tab.name
                            name = Templates[tab].orEmpty()[index].name
                            configEncoded = ProjectConfig(
                                packageName = defaultPackageName(name)
                            ).encode()
                        },
                        onOpenRecent = onOpenRecent,
                        recentCount = recentCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .heightIn(min = 0.dp),
                    )

                    SheetFooter(
                        name = name,
                        onNameChange = { name = it },
                        canCreate = canContinue,
                        hint = if (canContinue) {
                            "${tab.noun} · ${current?.name} · ${current?.tags}"
                        } else {
                            "Choose a template above."
                        },
                        actionLabel = "Continue",
                        onCreate = {
                            if (selected != null) {
                                configEncoded = config.copy(
                                    packageName = config.packageName.ifBlank {
                                        defaultPackageName(name)
                                    }
                                ).encode()
                                step = Step.Config.name
                            }
                        },
                    )
                } else {
                    ConfigForm(
                        config = config,
                        onConfigChange = { configEncoded = it.encode() },
                        webProject = webProject,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .heightIn(min = 0.dp),
                    )

                    SheetFooter(
                        name = name,
                        onNameChange = {},
                        canCreate = selected != null,
                        showNameField = false,
                        hint = if (current != null) {
                            "${current.name} · ${current.tags}"
                        } else {
                            "Choose a template above."
                        },
                        actionLabel = "Create project",
                        secondaryLabel = "Back",
                        onSecondary = { step = Step.Pick.name },
                        onCreate = {
                            if (selected != null) {
                                step = Step.Provisioning.name
                            }
                        },
                    )
                }
            }
        }
    }
}

