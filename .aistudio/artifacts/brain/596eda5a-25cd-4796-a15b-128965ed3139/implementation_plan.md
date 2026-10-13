# CloseCode Phase 0 Audit & Part A Stabilization Plan

A comprehensive roadmap to audit existing code, resolve all navigation and keyboard behavior issues, clean up editor test artifacts, and establish a green baseline before rebuilding the on-device compiler engine.

### User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the work sequence:
> 1. Execute **Phase 0 audit and Part A fixes first**.
> 2. Top priority for the next test APK is **Navigation, Back handling, and editor cleanup**.
>
> In addition, because the files in `com.closecode.build` were removed from the workspace, the IDE references will be decoupled/stubbed cleanly with an honest "Compile check" status so the app builds and runs without crashing during Phase 0/Part A testing.

- **Confirmed Decision 1**: Prioritize Phase 0 audit and Part A fixes over compiler chain rebuild.
- **Confirmed Decision 2**: Ensure Back navigation (system gesture, hardware button, and Terminal header back) operates hierarchically with zero swallowed presses.
- **Confirmed Decision 3**: Remove all leftover test artifacts, including test lines and intrusive diagnostic logs, while maintaining the non-intrusive `BackLog` forensics.

---

### 1. Overview & Core Concept

- **What It Does**: Stabilizes CloseCode on Android phones. Ensures all navigation paths (back gestures, header arrows, exit confirmations) behave predictably; guarantees the editor key row displays only when the keyboard is active; removes unwanted test lines; and ensures input fields never auto-capitalize or mangle commands.
- **Target Persona**: Mobile developers creating, editing, and managing Android and web projects entirely on their Android devices.
- **Key Value**: Rock-solid editor interaction without keyboard glitches, broken Back buttons, or visual artifacts, ready for on-device APK generation.

---

### 2. User Experience & Visual Design

- **Hierarchical Back Navigation**:
  - **Level 1 (Keyboard)**: If the soft keyboard is open, Back dismisses the keyboard and clears focus.
  - **Level 2 (Popups & Sheets)**: If a dialog (confirmation, prompt), bottom sheet (problems, search, more), or menu is open, Back dismisses only that modal.
  - **Level 3 (Overlays & Drawer)**: If the file drawer, diff view, or commit sheet is active, Back closes the drawer or returns to the file view.
  - **Level 4 (Tools & Panels)**: If Terminal, Preview, or Logcat is active, Back returns to the Code editor.
  - **Level 5 (Project Exit)**: If on the Code editor with nothing open, Back prompts for unsaved changes before popping to the home/hero screen.
  - **Level 6 (App Exit)**: On the home/hero screen, first Back shows a toast `"Press back again to exit"`; a second Back within 2 seconds exits the app.
  - **Terminal Header Back**: Page back button in the Terminal header calls the exact same hierarchical back logic, ensuring the header back button and system gesture never disagree.

- **Editor & Key Row Behavior**:
  - Key row (Tab, brackets, quotes, operators) appears exclusively when `WindowInsets.isImeVisible` is true.
  - When the keyboard closes, the key row smoothly transitions out and the bottom navigation bar (Search, AI, More) returns.
  - Tapping a code line smoothly scrolls the cursor into view above the key row and keyboard.
  - Strict input flags: Editor, Terminal prompt, search field, and New command field all enforce `capitalization = KeyboardCapitalization.None` and `autoCorrect = false`.

- **File Drawer & Diff View**:
  - File drawer covers the full height down over the bottom bar without empty headers or awkward top gaps.
  - Diff view presents clear single-column phone-friendly line diffs.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Hierarchical Single-Point Back Dispatcher**
  - *Chosen Approach*: Centralized, deterministic `handleBackPress()` with strict priority order, backed by Compose `BackHandler` enabled appropriately.
  - *Why*: Eliminates race conditions where multiple sub-composables register conflicting handlers or swallow Back events when hidden.
- **Decision 2: Editor Cleanliness & Performance**
  - *Chosen Approach*: Strip all artificial test drawings and verify clean syntax coloring and gutter geometry.
  - *Why*: The editor must render at full 60/120 fps without spurious drawing passes or distracting debug lines.
- **Decision 3: Decoupled Build Module Boundary**
  - *Chosen Approach*: Implement a lightweight, honest `CompileDriver` / `BuildStatus` contract inside the IDE screen to keep the UI functional and compiling while Part A is tested, ready for Phase 1 & 3 rebuild.

---

### 4. Technical Architecture & Component Flow

```
┌────────────────────────────────────────────────────────┐
│                   MainActivity                         │
│  - Double-back exit guard on HeroScreen                │
│  - NavHost (Hero -> Recent -> Settings -> IDE)         │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│                     IdeScreen                          │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Deterministic Back Handler Chain                 │  │
│  │   1. IME Clear Focus                             │  │
│  │   2. Dialogs (Delete, Prompt, Rename)            │  │
│  │   3. Bottom Sheets (Search, More, Problems)      │  │
│  │   4. Full Overlays (File Drawer, Diff, History)  │  │
│  │   5. Panels (Terminal, Logcat -> Code)           │  │
│  │   6. Unsaved changes check -> Exit to Hero       │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Editor Core                                      │  │
│  │   - No leftover test lines                       │  │
│  │   - AutoCorrect & Capitalization = None          │  │
│  │   - IME-docked Key Row (WindowInsets.isImeVisible)│ │
│  │   - Viewport scroll adjustment on focus          │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Terminal Screen (IdeTerminal)                    │  │
│  │   - Header PageBackButton wired to handleBack    │  │
│  │   - Commands sheet BackHandler enabled condition │  │
│  │   - Raw terminal character stream                │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

### 5. Step-by-Step Execution Plan

1. **Fix Compile Dependencies from Deleted Build Files**:
   - Provide clean stub definitions for `InstallState`, `BuildOutcome`, and `TemplateContract` in `com.closecode.build` so `IdeScreen.kt` and `SettingsScreen.kt` compile with zero errors.
   - Verify `compile_applet` succeeds.

2. **Audit & Fix Navigation & Back Handling**:
   - Verify and fix Back in `IdeTerminal.kt`: ensure the header back button and back gesture consistently pop forms, commands panels, or return to the Code editor.
   - Ensure every secondary BackHandler in `IdeTerminal.kt`, `IdeGitPanel.kt`, and `IdeGitHubSignIn.kt` has strict `enabled` flags so it never swallows Back when inactive.
   - Verify `MainActivity.kt` double-back exit toast on the Hero screen.

3. **Editor Cleanup & Input Configuration**:
   - Remove any temporary test lines, debug drawing flags, or lingering test code in `IdeScreen.kt` and `IdeDiagnosticsUi.kt`.
   - Audit and set `KeyboardCapitalization.None` and `autoCorrect = false` on all code/command inputs.
   - Verify tapped line auto-scrolling remains responsive above the key row.

4. **File Drawer & UI Corrections**:
   - Verify drawer height covers down over the bottom bar without empty space at the top.
   - Ensure Search and Diff views display correct file names and highlighted lines.

5. **Compilation & Unit Verification**:
   - Run `compile_applet` to verify a 100% clean build.
   - Run unit tests (`gradle :app:testDebugUnitTest`) to ensure regression-free status.
