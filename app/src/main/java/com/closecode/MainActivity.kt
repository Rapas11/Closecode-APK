package com.closecode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import com.closecode.ui.ide.TerminalSessions
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.closecode.ui.HeroScreen
import com.closecode.ui.ide.IdeScreen
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.settings.OptionSetting
import com.closecode.ui.settings.SettingsScreen
import com.closecode.ui.settings.rememberSettingsStore
import com.closecode.ui.theme.ClosecodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var keyboardLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    // Keyboard visibility from the window height, registered while attached: the visible frame
    // shrinks by the keyboard while some devices report zero inset height with a stuck visible
    // flag. Runs on the UI thread, so it writes the state directly. Re-registered on every
    // resume, so backgrounding and rotation never leave a stale reading behind.
    override fun onResume() {
        super.onResume()
        val contentView = findViewById<android.view.View>(android.R.id.content)
        keyboardLayoutListener =
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val frame = android.graphics.Rect()
                contentView.getWindowVisibleDisplayFrame(frame)
                com.closecode.ui.ide.KeyboardVisibility.update(
                    visibleHeight = frame.height(),
                    rootHeight = contentView.rootView.height,
                )
            }.also { contentView.viewTreeObserver.addOnGlobalLayoutListener(it) }
    }

    override fun onPause() {
        keyboardLayoutListener?.let {
            findViewById<android.view.View>(android.R.id.content)
                .viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        keyboardLayoutListener = null
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i(
            "CloseCode",
            "version=${BuildConfig.VERSION_NAME} build=${BuildConfig.VERSION_CODE} " +
                "source=${BuildConfig.SOURCE_REVISION}",
        )
        CrashReport.install(filesDir)
        // A crash leaves its trace in a file; the next launch offers it up for copying instead
        // of swallowing it. There is no logcat on a phone, so this dialog is the whole report.
        val pendingCrash = CrashReport.readLast(filesDir)
        setContent {
            var crashText by rememberSaveable { mutableStateOf(pendingCrash) }
            val settingsStore = rememberSettingsStore()
            var settings by remember(settingsStore) { mutableStateOf(settingsStore.read()) }
            // The theme is a preference, not a per-session toggle: System follows the device and
            // Dark or Light is whatever the settings screen was last left on, on every launch.
            val useDark = when (settings.theme) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            ClosecodeTheme(forceDark = useDark) {
                if (crashText != null) {
                    CrashDialog(
                        trace = crashText.orEmpty(),
                        onClose = {
                            CrashReport.clear(filesDir)
                            crashText = null
                        },
                    )
                }
                var encoded by rememberSaveable { mutableStateOf("") }
                var section by rememberSaveable { mutableStateOf("general") }

                val repository = rememberProjectRepository()
                val project = ProjectCodec.decode(encoded)
                // One .closecode.json per project, so this is a directory read — off the
                // composition path and onto Dispatchers.IO.
                var recentCount by remember { mutableIntStateOf(0) }
                LaunchedEffect(encoded, repository) {
                    recentCount = withContext(Dispatchers.IO) { repository.listProjects().size }
                }

                // "Open last project" decides what a launch lands on. The system restores the last
                // one for us; this is the single place that is allowed to say no, checked once at
                // startup — a project opened later in the session must never be thrown away by it.
                LaunchedEffect(Unit) {
                    if (!settings.openLastProject) encoded = ""
                }

                // One back stack for the top-level screens. Inner panels stay explicit state
                // with an ordered BackHandler chain inside IdeScreen — they share one composition
                // and its state, which a route per panel would destroy and rebuild on every switch.
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                // Back forensics: every route change lands on the stack the press log prints.
                DisposableEffect(nav) {
                    val listener = androidx.navigation.NavController.OnDestinationChangedListener {
                            _,
                            destination,
                            _,
                        ->
                        com.closecode.ui.ide.BackLog.routeChanged(destination.route)
                    }
                    nav.addOnDestinationChangedListener(listener)
                    onDispose { nav.removeOnDestinationChangedListener(listener) }
                }
                // Shells outlive the settings screen and the project switch: leaving the IDE must
                // never kill a running server just because the route changed.
                val sessionHolders = remember { mutableMapOf<String, TerminalSessions>() }
                val terminalSessions =
                    sessionHolders.getOrPut(project?.slug ?: "") { TerminalSessions() }
                // Set by the IDE as edits land; read when leaving it.
                var ideDirty by rememberSaveable { mutableStateOf(false) }

                // Opening a project lands on the IDE route, unless already there (cloning from
                // inside swaps the record under the same route, keyed by slug below).
                fun openEncoded(value: String) {
                    encoded = value
                    if (backStack?.destination?.route != "ide") nav.navigate("ide")
                }

                CompositionLocalProvider(LocalSettings provides settings) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (useDark) androidx.compose.ui.graphics.Color(0xFF0F1117)
                                else androidx.compose.ui.graphics.Color(0xFFECEFF4)
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 560.dp),
                        ) {
                            NavHost(navController = nav, startDestination = "hero") {
                                composable("hero") {
                                    // First Back warns, second within 2 s exits. Never from an inner
                                    // screen: those pop their own routes first.
                                    val context = LocalContext.current
                                    var lastBack by rememberSaveable { mutableStateOf(0L) }
                                    BackHandler {
                                        com.closecode.ui.ide.BackLog.press("hero", "branch=exit-guard")
                                        val now = System.currentTimeMillis()
                                        if (now - lastBack < 2000) {
                                            (context as? android.app.Activity)?.finish()
                                        } else {
                                            lastBack = now
                                            android.widget.Toast.makeText(
                                                context,
                                                "Press back again to exit",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    HeroScreen(
                                        recentCount = recentCount,
                                        onProjectCreated = { name, selection, templateName, config ->
                                            val record = repository.create(name, selection, config)
                                            openEncoded(
                                                ProjectCodec.encode(
                                                    Project(
                                                        record.name,
                                                        selection,
                                                        templateName,
                                                        config,
                                                    ),
                                                ),
                                            )
                                        },
                                        // A clone already owns its directory and its record, so it carries
                                        // its own slug rather than deriving one from the name.
                                        onProjectCloned = { record -> openEncoded(encodeRecord(record)) },
                                        onOpenRecent = { nav.navigate("recent") },
                                    )
                                }

                                composable("recent") {
                                    RecentProjectsRoute(
                                        repository = repository,
                                        onOpen = { record -> openEncoded(encodeRecord(record)) },
                                        onBack = { nav.popBackStack() },
                                    )
                                }

                                composable("settings") {
                                    SettingsScreen(
                                        activeId = section,
                                        onActiveChange = { section = it },
                                        onBack = { nav.popBackStack() },
                                        settings = settings,
                                        onSettingsChange = { settings = settingsStore.write(it) },
                                        projectRecord = project?.let { repository.find(it.slug) },
                                        onPackageNameChange = { record ->
                                            encoded = encodeRecord(record)
                                        },
                                    )
                                }

                                composable("ide") {
                                    val current = project
                                    if (current == null) {
                                        // Deep in the stack with nothing open (e.g. after a process
                                        // death): back out rather than showing an empty IDE.
                                        LaunchedEffect(Unit) { nav.popBackStack("hero", false) }
                                    } else {
                                        // Keyed on the project, not on composable identity. Cloning from
                                        // inside the IDE swaps `project` while this route stays put, and
                                        // every per-project holder would otherwise carry the old
                                        // project's contents into the new.
                                        key(current.slug) {
                                            IdeScreen(
                                                projectName = current.name,
                                                templateName = current.templateName,
                                                slug = current.slug,
                                                repository = repository,
                                                onOpenSettings = {
                                                    section = "general"
                                                    nav.navigate("settings")
                                                },
                                                onExit = { nav.popBackStack("hero", false) },
                                                onDirtyChange = { ideDirty = it },
                                                terminalSessions = terminalSessions,
                                                // A clone already owns its directory and its record, so it
                                                // carries its own slug rather than deriving one from the
                                                // name — and it lands here, in the IDE, without the hero
                                                // screen in between.
                                                onProjectCloned = { record ->
                                                    encoded = encodeRecord(record)
                                                },
                                                onToggleTheme = {
                                                    val next = when (settings.theme) {
                                                        "System" -> "Dark"
                                                        "Dark" -> "Light"
                                                        else -> "System"
                                                    }
                                                    settings = settingsStore.write(
                                                        settings.withOption(OptionSetting.THEME, next),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

        private fun encodeRecord(record: ProjectRecord): String = ProjectCodec.encode(
        Project(
            name = record.name,
            selection = SelectedTemplate(record.tab, record.templateIndex),
            templateName = record.templateName,
            config = record.config,
            // The record's own id, not one re-derived from the name: a cloned project's folder is
            // named when the slug is reserved, and re-slugifying could point at a different one.
            slug = record.slug,
        )
    )
}

/**
 * The previous run's crash, offered for copying. Plain Material styling on purpose: this shows
 * above every screen including a broken one, so it reads nothing from the app's own theme.
 */
@Composable
private fun CrashDialog(trace: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("The app crashed", fontSize = 17.sp) },
        text = {
            Text(
                text = trace.take(2000),
                fontSize = 11.sp,
                maxLines = 14,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(trace))
                    onClose()
                },
            ) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}