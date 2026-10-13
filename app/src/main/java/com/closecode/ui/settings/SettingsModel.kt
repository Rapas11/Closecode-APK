package com.closecode.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * One row of the settings screen.
 *
 * A row is one of three things: a control bound to a field of [Settings] — so it reads and writes a
 * real preference — a plain read-out of something the app measures, or a control the app does not
 * honour *yet*. The third kind is deliberate: it is on screen so it can be wired up later, and it
 * behaves exactly as it always did rather than being hidden until someone gets to it. Only the rows
 * carrying a [BoolSetting] / [OptionSetting] key change anything.
 */
sealed interface Row {
    val name: String
    val description: String?

    /**
     * An on/off control. With [key] it resolves through [Settings]; without one it holds its own
     * [default] for the session, which is what an unwired row looked like before it was cut.
     */
    data class Toggle(
        override val name: String,
        override val description: String? = null,
        val key: BoolSetting? = null,
        val default: Boolean = false,
    ) : Row

    /**
     * A fixed list of choices. With [key] the current value comes from [Settings]; without one the
     * control shows [current] and stays where it is until a key is given.
     */
    data class Options(
        override val name: String,
        override val description: String? = null,
        val key: OptionSetting? = null,
        val choices: List<String> = emptyList(),
        val unit: String = "",
        val current: String = "",
    ) : Row

    /** A measured fact — a path, a size, a version — with nothing to edit. */
    data class Value(
        override val name: String,
        override val description: String? = null,
        val value: String,
    ) : Row

    /** A destination the row leads somewhere else to reach. */
    data class Nav(
        override val name: String,
        override val description: String? = null,
        val action: String,
    ) : Row

    /** A fact with a health signal beside it: green when it is fine, red when it is not. */
    data class Status(
        override val name: String,
        override val description: String? = null,
        val value: String,
        val ok: Boolean = true,
    ) : Row

    /** The signed-in account, shown as an avatar, a name and a way to manage it. */
    data class Account(
        val initials: String,
        override val name: String,
        val subtitle: String,
        val action: String,
    ) : Row {
        override val description: String? = null
    }

    /** The accent colour swatches. */
    data class Accents(override val name: String) : Row {
        override val description: String? = null
    }

    /** Something the screen can do rather than show. */
    data class Action(
        override val name: String,
        override val description: String? = null,
        val action: String,
        val id: SettingsAction,
        val danger: Boolean = false,
    ) : Row
}

/** The actions [Row.Action] can ask for. Named, so nothing matches on a row's display text. */
enum class SettingsAction {
    CLEAR_CACHES,
    DOWNLOAD_TOOLCHAIN,
}

@Immutable
data class Group(val title: String, val rows: List<Row>)

@Immutable
data class Section(
    val id: String,
    val label: String,
    val icon: String,
    val title: String,
    val blurb: String,
    val groups: List<Group>,
)

/** The accent swatches offered by [Row.Accents]. */
val AccentOptions = listOf(
    Color(0xFFF97316),
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
)

/**
 * What the app measures for the settings screen, off the main thread.
 *
 * These are facts rather than preferences, so they are not part of [Settings] and never persisted.
 */
@Immutable
data class SettingsLive(
    val projectRoot: String = "…",
    val projectsSize: String = "…",
    val cachesSize: String = "…",
    val version: String = "1.0.0",
    val toolchainStatus: String = "…",
)

/**
 * The settings screen: every row, across every section, built as a function of [live].
 *
 * A function rather than a constant because three of the rows report something the app measured for
 * this session. Section order is the order they are listed in — general, editor, intelligence,
 * build, terminal, git, ai, appearance, storage, security, about.
 */
fun sections(live: SettingsLive): List<Section> = listOf(
    Section(
        id = "general",
        label = "General",
        icon = "◎",
        title = "General",
        blurb = "Configure how Closecode behaves.",
        groups = listOf(
            Group(
                "Startup",
                listOf(
                    Row.Value(
                        "Default project location",
                        "Where new projects are created.",
                        live.projectRoot,
                    ),
                    Row.Toggle(
                        "Open last project",
                        "Reopen the project you were in when Closecode starts.",
                        BoolSetting.OPEN_LAST_PROJECT,
                    ),
                    Row.Toggle(
                        "Auto save",
                        "Write the file a moment after you stop typing.",
                        BoolSetting.AUTO_SAVE,
                    ),
                ),
            ),
            Group(
                "Account",
                listOf(Row.Account("RC", "Closecode Account", "Signed in", "Manage")),
            ),
        ),
    ),
    Section(
        id = "editor",
        label = "Editor",
        icon = "⌘",
        title = "Editor",
        blurb = "Customize the Closecode code editor.",
        groups = listOf(
            Group(
                "Text Editor",
                listOf(
                    Row.Options(
                        "Font size",
                        "Editor text size.",
                        OptionSetting.FONT_SIZE,
                        FontSizeChoices,
                        unit = " px",
                    ),
                    Row.Options(
                        "Tab size",
                        "Spaces the Tab key inserts.",
                        OptionSetting.TAB_SIZE,
                        TabSizeChoices,
                    ),
                    Row.Toggle("Line numbers", "Show the number gutter.", BoolSetting.LINE_NUMBERS),
                    Row.Toggle("Word wrap", "Wrap long lines instead of scrolling sideways.", BoolSetting.WORD_WRAP),
                    Row.Toggle("Code folding", "Collapse a block behind its first line.", default = true),
                    Row.Toggle(
                        "Rainbow brackets",
                        "Colour nested brackets by depth.",
                        BoolSetting.RAINBOW_BRACKETS,
                    ),
                    Row.Toggle("Bracket guides", "Draw a faint rule down each block's own indent level.", BoolSetting.BRACKET_GUIDES),
                    Row.Toggle(
                        "Frame stats",
                        "Show milliseconds per frame over the editor while scrolling.",
                        BoolSetting.FRAME_STATS,
                    ),
                ),
            ),
            Group(
                "Behavior",
                listOf(
                    Row.Toggle(
                        "Auto indent",
                        "Carry the indentation of the line above onto a new one.",
                        BoolSetting.AUTO_INDENT,
                    ),
                    Row.Toggle(
                        "Auto close brackets",
                        "Type ( and get (), with the caret in between.",
                        BoolSetting.AUTO_CLOSE_BRACKETS,
                    ),
                    Row.Toggle(
                        "Format on save",
                        "Tidy a Kotlin file as it is written to disk.",
                        BoolSetting.FORMAT_ON_SAVE,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "intelligence",
        label = "Code Intelligence",
        icon = "✦",
        title = "Code Intelligence",
        blurb = "Configure autocomplete, diagnostics and coding assistance.",
        groups = listOf(
            Group(
                "Code Intelligence",
                listOf(
                    Row.Toggle(
                        "Autocomplete",
                        "Show code completion suggestions as you type.",
                        BoolSetting.AUTOCOMPLETE,
                    ),
                    Row.Toggle("Auto import", "Add the import a symbol needs.", default = true),
                    Row.Toggle("Parameter hints", "Show the arguments a call takes.", default = true),
                    Row.Toggle("Quick fixes", "Offer a fix under a problem as you type.", default = true),
                    Row.Toggle(
                        "Diagnostics",
                        "Flag problems in the gutter and offer quick fixes.",
                        BoolSetting.DIAGNOSTICS,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "build",
        label = "Build & Toolchain",
        icon = "▣",
        title = "Build & Toolchain",
        blurb = "Manage the tools Closecode uses to build Android projects.",
        groups = listOf(
            Group(
                "Toolchain",
                listOf(
                    Row.Status("JDK", "Java runtime used for Android builds.", "Java 17"),
                    Row.Status("Android SDK", null, "SDK 35"),
                    Row.Status("Build Tools", null, "35.0.0"),
                    Row.Value("Gradle", null, "Managed"),
                ),
            ),
            Group(
                "On-device build tools",
                listOf(
                    Row.Status(
                        "Compiler pack",
                        "Kotlin, Java, dex and signer. One Wi-Fi download, then offline.",
                        live.toolchainStatus,
                        ok = live.toolchainStatus == "Ready",
                    ),
                    Row.Action(
                        "Download build tools",
                        "About 80 MB over Wi-Fi. Resumes if interrupted.",
                        "Download",
                        SettingsAction.DOWNLOAD_TOOLCHAIN,
                    ),
                ),
            ),
            Group(
                "Build Behavior",
                listOf(
                    Row.Toggle("Build cache", "Reuse task output instead of redoing it.", default = true),
                    Row.Nav("SDK Manager", "Manage installed Android SDK packages.", "Open"),
                ),
            ),
        ),
    ),
    Section(
        id = "terminal",
        label = "Terminal",
        icon = ">_",
        title = "Terminal",
        blurb = "Configure the integrated terminal.",
        groups = listOf(
            Group(
                "Terminal",
                listOf(
                    Row.Value("Default shell", null, "System"),
                    Row.Options(
                        "Terminal colours",
                        "The palette a program's own colour codes are drawn against.",
                        OptionSetting.TERMINAL_THEME,
                        TerminalThemeChoices,
                    ),
                    Row.Options(
                        "Terminal font size",
                        "Larger fits less of a line across the screen.",
                        OptionSetting.TERMINAL_FONT_SIZE,
                        TerminalFontSizeChoices,
                        unit = " pt",
                    ),
                    Row.Toggle("Command history", "Keep the commands you have run.", default = true),
                ),
            ),
        ),
    ),
    Section(
        id = "git",
        label = "Git & GitHub",
        icon = "⑂",
        title = "Git & GitHub",
        blurb = "Manage Git identity and GitHub connections.",
        groups = listOf(
            Group(
                "Git Identity",
                listOf(
                    Row.Value("Name", null, "Not configured"),
                    Row.Value("Email", null, "Not configured"),
                    Row.Value("Default branch", null, "main"),
                ),
            ),
            Group(
                "GitHub",
                listOf(Row.Nav("GitHub account", "Sign in to clone, pull and push.", "Connect")),
            ),
        ),
    ),
    Section(
        id = "ai",
        label = "AI",
        icon = "✧",
        title = "AI",
        blurb = "Configure the AI assistant and agent permissions.",
        groups = listOf(
            Group(
                "AI Assistant",
                listOf(
                    Row.Value("Provider", null, "Not configured"),
                    Row.Value("Model", null, "Default"),
                ),
            ),
            Group(
                "Agent Permissions",
                listOf(
                    Row.Value(
                        "File access",
                        "Allow AI to read and modify project files.",
                        "Ask",
                    ),
                    Row.Value("Terminal access", null, "Ask"),
                    Row.Value("Build access", null, "Allow"),
                    Row.Value("Git access", null, "Ask"),
                ),
            ),
        ),
    ),
    Section(
        id = "appearance",
        label = "Appearance",
        icon = "◐",
        title = "Appearance",
        blurb = "Customize the look and feel of Closecode.",
        groups = listOf(
            Group(
                "Theme",
                listOf(
                    Row.Options(
                        "Theme",
                        "Follow the system, or pick one.",
                        OptionSetting.THEME,
                        ThemeChoices,
                    ),
                    Row.Accents("Accent color"),
                    Row.Toggle("Animations", "Let panels and controls move.", default = true),
                ),
            ),
        ),
    ),
    Section(
        id = "storage",
        label = "Storage",
        icon = "▤",
        title = "Storage",
        blurb = "View and manage Closecode storage.",
        groups = listOf(
            Group(
                "Storage Usage",
                listOf(
                    Row.Value("Projects", "Everything Closecode has on this device.", live.projectsSize),
                    Row.Value("Cache", "Temporary files. Deleting them is always safe.", live.cachesSize),
                    Row.Value("Android SDK", null, "2.2 GB"),
                    Row.Value("Gradle cache", null, "640 MB"),
                    Row.Value("Build cache", null, "420 MB"),
                ),
            ),
            Group(
                "",
                listOf(
                    Row.Action(
                        "Clear caches",
                        "Delete every temporary file Closecode has written.",
                        "Clear",
                        SettingsAction.CLEAR_CACHES,
                        danger = true,
                    ),
                ),
            ),
        ),
    ),
    Section(
        id = "security",
        label = "Security",
        icon = "◇",
        title = "Security",
        blurb = "Manage credentials, signing keys and app security.",
        groups = listOf(
            Group(
                "Security",
                listOf(
                    Row.Toggle(
                        "Biometric lock",
                        "Require biometric authentication when opening Closecode.",
                        default = false,
                    ),
                    Row.Nav("Credentials", "Stored tokens and passwords.", "Manage"),
                    Row.Nav("Signing keys", "Keystore files used to sign a build.", "Manage"),
                ),
            ),
        ),
    ),
    Section(
        id = "about",
        label = "About",
        icon = "ⓘ",
        title = "About",
        blurb = "Information about Closecode.",
        groups = listOf(
            Group(
                "Closecode",
                listOf(
                    Row.Value("Version", "This build of Closecode.", live.version),
                    Row.Nav("Open source licenses", null, "View"),
                    Row.Nav("Privacy", null, "View"),
                ),
            ),
        ),
    ),
)

fun sectionById(id: String): Section = sections(SettingsLive()).firstOrNull { it.id == id }
    ?: sections(SettingsLive()).first()

/**
 * Every key a settings row edits, for the test that keeps the two lists honest: a field nobody can
 * reach from the screen is a setting that will never be changed.
 *
 * Only the rows that carry a key count. The unwired rows have none by design — they are on screen
 * waiting to be wired, not claiming to work today.
 */
fun usedBoolSettings(): Set<BoolSetting> = sections(SettingsLive())
    .flatMap { section -> section.groups.flatMap { it.rows } }
    .mapNotNull { row -> (row as? Row.Toggle)?.key }
    .toSet()

fun usedOptionSettings(): Set<OptionSetting> = sections(SettingsLive())
    .flatMap { section -> section.groups.flatMap { it.rows } }
    .mapNotNull { row -> (row as? Row.Options)?.key }
    .toSet()

/** Total rows on the screen, whatever section they sit in. */
fun rowCount(): Int = sections(SettingsLive())
    .sumOf { section -> section.groups.sumOf { it.rows.size } }
