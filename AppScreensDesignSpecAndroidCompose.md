# App Screens Design Spec (Android Compose)

Oct 9, 2026 · @Hh4hh

## Overview

This is the second design spec. The first one (Code Editor Design Spec) covers the editing area, top bar and tabs. This one covers every other screen the user sees: file explorer, source control, GitHub sign-in, clone, terminal, website preview, console, Android build panel and screen previews.

The reference for every screen is the mockup image shown during design. Match the mockup first, then use this document for exact values.

Goals for all screens:

- Professional and calm: dark background, one blue accent, no clutter.
- Plain words. Words like Initialize repository and device flow are replaced with text a beginner understands.
- One main action per screen, shown as the only large blue button.
- Every screen has an empty state, a loading state and an error state.

Scope in this document:

1. File explorer
2. Source control (first-time screen and screen with changes)
3. GitHub sign-in
4. Clone repository and cloning progress
5. Terminal
6. Website preview, including device sizes and the console bar
7. Console
8. Android build and run panel
9. Screen previews for Jetpack Compose @Preview functions

All sizes are in dp and text sizes in sp, based on a 380 dp wide phone. Use the same theme tokens as the first spec.

## Design tokens and shared components

Reuse every token from the first spec (editorBg #0F1117, accent #4C8DFF, divider #1C212C, text #D4D7DE, popupBg #181D29, popupSelected #243055, error #EF5B5B). The tokens below are new.

| Token | Hex | Used for |
| --- | --- | --- |
| surface | #12161F | Cards and panels on the dark background |
| surfaceBorder | #232938 | 1 dp border of cards |
| chipBg | #161B26 | Segmented controls, chips, search fields |
| fieldBg | #0B0D12 | Text fields, terminal and log background |
| fieldBorder | #2A3142 | 1 dp border of text fields |
| primary | #2F6FED | The one large main button on a screen |
| primaryTint | #161F3A | Outline buttons with blue text |
| link | #7FB0FF | Links and outline button text |
| textMuted | #7D869A | Descriptions and helper text |
| textFaint | #566078 | Hints, disabled text, folder counts |
| success | #3FB97A | Added files, success checks |
| warning | #E5C07B | Modified files, unsaved dots, warnings |
| statusAddedBg | #16382A | Background of the A badge |
| statusModifiedBg | #3A3115 | Background of the M badge |
| statusDeletedBg | #3A1A1F | Background of the D badge |

Shared components used on many screens:

| Component | Spec |
| --- | --- |
| Segmented control | Container chipBg, radius 9 dp, 3 dp padding, 12 dp side margin. Items 28 dp tall, 12sp. Selected item popupSelected with white text |
| Primary button | 42 to 46 dp tall, radius 10 dp, primary color, 14sp medium white text. Only one per screen |
| Outline button | 38 dp tall, radius 9 dp, 1 dp primary border, primaryTint fill, link text 13sp |
| Text button | No background, link text 12sp |
| Card | surface fill, 1 dp surfaceBorder, radius 12 dp, 12 dp padding |
| Text field | 44 dp tall, radius 10 dp, fieldBg, 1 dp fieldBorder, 12 dp side padding |
| Status badge | 18 by 18 dp, radius 5 dp, 10sp medium letter. A is success on statusAddedBg, M is warning on statusModifiedBg, D is error on statusDeletedBg |
| Section label | 11sp uppercase, 0.06 em letter spacing, textFaint |
| Touch target | Never smaller than 32 by 32 dp |

Rules:

- A screen has one primary button at most. Other actions use outline or text buttons.
- Destructive actions (Delete, Cancel build) use the error color for text only, never a filled red button.
- Every icon button has a content description for accessibility.

## File explorer

The file explorer opens from the menu button at the far left of the top bar. It is a drawer that slides in from the left and leaves a strip of the editor visible on the right.

**Header**

- A segmented control with two items: Files and Source Control. The Source Control item shows a small blue count bubble (16 dp tall, 10sp white text) with the number of changed files. Hide the bubble when the number is 0.
- Below it, one row with the project name on the left in small uppercase letters (11sp, 0.07 em spacing, textFaint) and four icon buttons on the right: New file, New folder, Refresh, Collapse all. Each button is 30 by 30 dp with a 17 dp icon in #9AA3B5.
- The old EXPLORER title and the PROJECT label are removed. The project name already says where the user is.

**Tree rows**

| Part | Value |
| --- | --- |
| Row height | 36 dp, full width |
| Indent per level | 16 dp, with a 1 dp guide line in indentGuide color |
| Chevron | 13 dp, textFaint. Down means open, right means closed. Files have no chevron and keep the same indent |
| Icon | 16 dp, 8 dp gap to the name, color by file type (table below) |
| Name | 13sp, color text. One line, ellipsis in the middle for long names |
| Folder count | On closed folders only: the number of items, 11sp, textFaint, right aligned |
| Git letter | 11sp medium at the right edge: A in success, M in warning, D in error, U (untracked) in textMuted |
| Unsaved dot | 6 dp warning-colored dot right after the name |
| Sorting | Folders first, then files, both alphabetical and ignoring case |

File icon colors:

| File type | Icon | Color |
| --- | --- | --- |
| Folder | folder / folder-open | #61AFEF |
| HTML | html5 | #E5784A |
| CSS | css3 | #4C8DFF |
| JavaScript | javascript | #E5C07B |
| Kotlin | kotlin | #C792EA |
| Markdown | markdown | #9AA3B5 |
| Anything else | file | #7D869A |

**Selection and actions**

- The open file's row has background #1B2850 and a 3 dp accent bar on its left edge (radius 2 dp, 4 dp inset top and bottom).
- Tapping a file opens it in the editor and closes the drawer. Tapping a folder opens or closes it.
- Long press shows a menu: Rename, Duplicate, Copy path, Delete. Delete is in the error color and asks for confirmation.
- The tree remembers which folders are open between app launches.
- Empty project: show the text No files yet and two outline buttons, New file and Clone a repository.
- A hint line under the tree (11sp, textFaint) explains long press, the yellow dot and the Git letter. Show it only the first three times the drawer is opened.

## Source control

Source Control is the second tab of the explorer drawer. It has two states, chosen by whether the project already has a Git history.

**State 1: first time (no Git history yet)**

Show a centered intro and three option cards. Nothing else.

- Icon tile: 52 dp, radius 14 dp, primaryTint fill, branch icon in link color.
- Title: Get started with Git (17sp medium). Text under it: Git saves versions of your project so you can undo mistakes and back it up online. (12sp, textMuted).
- Three option cards, 12 dp apart, each with a 36 dp icon tile, a 14sp medium title, a 11sp description and a chevron on the right:

| Order | Title | Description | Style | Action |
| --- | --- | --- | --- | --- |
| 1 | Start tracking this project | Create a Git history on this device | Primary (filled blue, white text) | Runs git init, then shows State 2 |
| 2 | Clone a repository | Download a project from GitHub | Normal card | Opens the Clone screen |
| 3 | Sign in with GitHub | Back up and sync your projects | Normal card | Opens the GitHub sign-in screen |

The words Initialize repository and Not under version control must not appear anywhere.

**State 2: project has a Git history**

From top to bottom:

1. **Branch row:** branch icon, branch name (13sp), and on the right a refresh icon with the status text, for example Not published, Up to date, 2 to push or 1 to pull.
2. **Staged section:** label Staged with the count, and a text button Unstage all on the right. Each row is 38 dp: status badge, file name, folder name small and gray after it, and a round 26 dp minus button on the right to unstage.
3. **Changes section:** same rows with a plus button to stage a file. Tapping a row opens a side-by-side diff of that file.
4. **Commit box:** 58 dp tall text field with the hint Message: what did you change?
5. **Commit button:** the one primary button, labeled Commit N files (for example Commit 4 files). It is gray and disabled until the message is not empty and at least one file is staged.
6. **GitHub card** (only when not signed in): see the GitHub sign-in section. When signed in, replace it with two outline buttons, Pull and Push, each showing the number of waiting commits.

Stash, Discard all and Switch branch live in a three-dot menu on the branch row. They are not shown as buttons.

**Errors and edge cases**

- Commit fails: show a red text line under the button with the reason in plain words.
- No changes: show the text Everything is saved and a small green check, and hide the commit box.
- Merge conflicts: list conflicted files first with a red C badge.

## GitHub sign-in

The app signs in with GitHub's device flow. The app asks GitHub for a short code, the user types that code on github.com/login/device, and the app is connected without ever seeing the user's password. The screen must explain this in three plain steps.

**Entry points**

- The Sign in with GitHub card on the first-time source control screen.
- The Connect GitHub card at the bottom of the source control screen, shown only when not signed in. It has the title GitHub is not connected (13sp medium, brand icon), the text Connect it to back up this project online and open it on other devices. (12sp, textMuted), an outline button Connect GitHub, and a text button Clone a repository.
- The Sign in with GitHub button on the Clone screen.

**Sign-in screen** (full-screen sheet, title Connect GitHub 17sp medium, subtitle 12sp textMuted)

| Step | Title | Content |
| --- | --- | --- |
| 1 | Copy this code | Code box (fieldBg, fieldBorder, radius 10 dp) with the code in monospace 20sp, link color, 0.14 em letter spacing, and a Copy button (32 dp tall, popupSelected, white 12sp) inside the box on the right. Tapping Copy changes the label to Copied for 2 seconds |
| 2 | Open GitHub | Text: It opens github.com/login/device in your browser. Outline button Open GitHub with an external-link icon. It opens the browser or a Chrome Custom Tab |
| 3 | Paste the code and tap Authorize | Text: Come back here. It connects by itself. |

Step numbers are 22 dp circles (popupSelected fill, light blue number).

**Waiting and result**

- Under the steps show a status row (radius 10 dp, surface fill) with a 8 dp warning-colored dot and the text Waiting for approval, followed by a countdown such as code expires in 14:32.
- The app polls GitHub at the interval GitHub returns (at least every 5 seconds) and connects by itself. Do not ask the user to press a button after authorizing.
- On success: show Connected as @username with a green check for 1.5 seconds, then return to the previous screen with the GitHub card replaced by Pull and Push buttons.
- When the code expires: show Code expired and a primary button Get a new code.
- When the user denies access: show You did not allow access and the same button.
- When the phone is offline: show No internet connection and a Try again outline button.
- Cancel is a text button at the bottom and stops polling.

Small print under the status row (11sp, textFaint): Your password is never shared with this app. You can disconnect any time in settings.

Store the access token in Android Keystore backed encrypted storage. Never write it to logs, project files or the Git config.

## Clone repository

A full-screen page with an X button at the top right (36 by 36 dp, radius 10 dp, fieldBorder outline). It has two ways to clone: paste a link, or pick from the user's GitHub repositories.

**Header**

- Title Clone a repository (18sp medium) and the text Paste a link, or pick one of your repositories. (12sp, textMuted).

**Link form**

| Part | Value |
| --- | --- |
| Label | Repository link, 12sp, #9AA3B5, 16 dp above the field |
| Link field | 44 dp tall text field, monospace 12sp. Placeholder https://github.com/owner/repository. A Paste button (30 dp tall, chipBg) sits inside the right end and fills the field from the clipboard |
| Folder label | Save in folder |
| Folder field | Fills in by itself from the link (the last part, without .git). The user can edit it. If the folder already exists, show a red line The folder already exists and keep Clone disabled |
| Clone button | 44 dp tall. Gray (chipBg, textFaint) while the link is empty or not valid. Primary blue once valid |

Link validation: accept https links to github.com, gitlab.com and bitbucket.org, with or without .git. Show a red helper line Enter a full link that starts with https:// when the link is wrong, only after the user stops typing for one second.

**Choose from GitHub** (below a divider with the label OR CHOOSE FROM GITHUB)

- **Not signed in:** a card with the title See your repositories here, the text Sign in once to pick private and public repositories from a list. Public links above work without signing in., and an outline button Sign in with GitHub.
- **Signed in:** a search field (38 dp, Search your repositories) and a list of repositories. Each row is 54 dp: lock icon for private or book icon for public, the name (13sp), the text Updated 2 days ago and Private or Public (11sp, textMuted), and a Clone button (28 dp tall, popupSelected fill) on the right. Newest first. Load 30 at a time and load more when scrolling.
- Tapping a row's Clone button clones with the default folder name and starts the progress screen directly.

**Cloning progress screen**

- Centered icon tile (56 dp), title Cloning followed by the repository name (17sp medium), and the link in monospace 12sp under it.
- A progress bar 8 dp tall (chipBg track, accent fill, radius 4 dp). Under it, on the left the current step and on the right the percentage and the size, for example 62% · 1.2 of 1.9 MB.
- A three-step list: Connected to GitHub, Downloading files, Open as a project. Done steps have a green check, the current step has a spinner and white text, future steps are gray.
- A text button Cancel in the error color. Cancelling deletes the half-downloaded folder.
- When done, open the project automatically and show the file explorer.
- Errors in plain words: Repository not found or private, No internet connection, Not enough storage. Each has a Try again outline button.

## Terminal

The terminal opens from the terminal button in the top bar. It works like a real terminal: the prompt is part of the output, not a separate bar. The old separate input bar and the two rows of command chips are removed.

**Layout, top to bottom**

| Part | Value |
| --- | --- |
| Header | 52 dp tall. Left: title Terminal (14sp medium) and under it the folder path in monospace 11sp textFaint, for example \~/Landing page. Right: a Commands chip (28 dp tall, chipBg, bolt icon, 12sp) |
| Session row | Session chips, 26 dp tall, monospace 12sp, radius 7 dp. The active chip is white text with a 2 dp accent line under it. A plus chip adds a session. Long press a chip to rename or close it |
| Output | Fills the remaining space. Background fieldBg (#0B0D12), monospace 11.5sp, line height 19 dp, 12 dp padding. Scrolls and sticks to the bottom while new text arrives |
| Key row | One row, 34 dp tall keys with 4 dp gaps, on a #12151D background with a 1 dp top divider |

**Prompt and output**

- The prompt is part of the output: the folder path in green (#3FB97A), then a gray $, then the command the user types, ending with a blinking block cursor 7 dp wide in accent color.
- Typing happens right at the prompt using the system keyboard. The Enter key runs the command. There is no send button.
- Output colors: folder names blue (#61AFEF), errors red (#EF5B5B), git additions green (#3FB97A), everything else #C5CAD6. Support standard ANSI colors.
- Empty state: the first line is Android shell · followed by the folder, then a prompt. Do not show a big instruction paragraph.
- Long press selects text with the same handles as the editor. A Copy action appears in a small toolbar. Tapping a file path in the output opens that file.
- A running command shows a small Stop button in the header in place of the Commands chip. It sends Ctrl+C.

**Key row**

Keys in order: ESC, TAB, CTRL, ALT, |, \~, /, up arrow, down arrow.

- ESC, TAB, CTRL and ALT use a blue tint (primaryTint fill, link text). CTRL and ALT are sticky: one tap arms the key for the next character, a second tap releases it.
- All other keys use chipBg with #C5CAD6 text, monospace 12sp.
- Up and down arrows move through the command history.

**Commands sheet**

Tapping the Commands chip opens a bottom sheet with saved commands as a 2-column list of cards: ls -la, pwd, ps, clear, kill, git status, git diff. Tapping one types it at the prompt without running it, so the user can edit it first. A Add command card at the end lets the user save their own. The Clear and Cancel keys are removed from the key row because Clear is in this sheet and Cancel is the Stop button.

## Website preview

The preview is the third icon (picture icon) in the row that shows the file name, below the top bar. It shows the website in a WebView served from a local address such as localhost:8080, so scripts, styles and images load like in a real browser.

**Toolbar** (below the file row, 8 dp padding)

| Part | Value |
| --- | --- |
| Address pill | 34 dp tall, fully rounded, chipBg. Contents: a 7 dp green dot (live), the file name in 12sp white, and the local address in 11sp textFaint. One line, ellipsis at the end |
| Refresh | 34 by 34 dp, 18 dp icon. Reloads the page and keeps the scroll position |
| Open in browser | 34 by 34 dp, opens the page in the phone's browser |
| Console | Bug icon, 34 by 34 dp. A red badge (15 dp tall, 9sp white text, top right) shows the number of errors. No badge when there are none |

**Device sizes** (row under the toolbar, 1 dp bottom divider)

- Three chips: Phone, Tablet, Desktop (26 dp tall, 11sp, device icon in front). The selected chip uses popupSelected fill and white text.
- To the right, the page size in monospace 11sp textFaint: 375 × 812, 768 × 1024, 1280 × 800.
- Phone shows the page at full width. Tablet shows it at 78% of the width, centered. Desktop renders the page at 1280 dp wide and scales it down to fit (about 0.55), so layouts with breakpoints behave correctly. Set the WebView user agent and viewport width to match.
- The stage behind the page (fieldBg) shows around Tablet and Desktop so the page edge is visible.

**Behavior**

- **Live:** the page reloads by itself about 500 ms after the user saves a file in the project. A small progress line (2 dp, accent color) runs along the top of the stage while loading.
- **Last scroll position:** keep it across reloads.
- **Console bar:** a 40 dp bar at the bottom of the preview, with a terminal icon, the word Console, the error count in red when above zero, and an up chevron. Tapping it opens the console (next section).
- **Errors:** if the page fails to load, show a centered message with the reason and a Try again outline button, never a blank white page.
- **Light page on dark app:** the page keeps its own colors. Do not tint it.
- **Remove** the old plain gray row that only had refresh and open icons.

## Console

The console shows what the website prints and the errors it throws. It opens as a panel at the bottom of the preview and shrinks the preview to about 150 dp tall while open.

**Panel**

| Part | Value |
| --- | --- |
| Height | 230 dp, with a drag handle to resize. Swipe down or tap the Console bar to close |
| Background | fieldBg (#0B0D12), 1 dp top divider |
| Filter row | Chips All, Errors (with count, red text), Warnings, Logs. 24 dp tall, 11sp. Selected chip uses popupSelected fill. A trash icon on the right clears the list |
| Log line | Monospace 11sp, line height 1.5, 12 dp side padding, 1 dp bottom divider #12161F, text #C5CAD6 |
| Error line | Background #1D1215, text #F2A0A0 |
| Warning line | Background #1D1A10, text warning color |
| Source link | Under errors and warnings: file name and line number in 10.5sp sans, link color, with the words tap to open |

**What goes in the console**

- console.log, console.info, console.warn and console.error output from the page.
- Uncaught JavaScript errors with their message and file and line.
- Failed requests for files such as styles.css or images, shown as Failed to load styles.css (404).
- A line Page loaded in N ms after every load.

**Behavior**

- Tapping a line with a source link closes the preview and opens that file in the editor with the cursor on that line.
- The list clears automatically when the page reloads, unless Preserve log is switched on in the three-dot menu of the panel.
- Long press a line to copy its text.
- Show at most 500 lines and drop the oldest first.
- The error count badge on the toolbar and on the Console bar always matches the number of lines under the Errors filter.
- A new error while the panel is closed makes the bug icon badge update and the Console bar count turn red, but does not open the panel.

## Android build and run panel

For Android projects the run button builds the app and shows this panel instead of a website preview. Android does not let one app display another running app inside itself, so the built app opens on its own and this panel is the control center for the build.

**Panel content, top to bottom** (14 dp padding)

| Part | Value |
| --- | --- |
| App header | 48 dp icon tile (radius 12 dp, #1D2B57 fill, Android icon in link color), app name 15sp medium, and under it the package name and version in monospace 11sp textMuted |
| Result bar | 10 dp radius row. Success: background #12281F, text #5FD19A, check icon, Build successful · 12 s. Failure: background #2A1519, text #F2A0A0, Build failed · 3 errors |
| Step list | One row per step, 30 dp tall, 13sp: Compile resources, Compile Kotlin, Package APK with the file size, Sign and install. Each has a green check and its time on the right. The running step shows a spinner. Steps after a failure are gray |
| Primary button | Open app, 46 dp tall, play icon. Launches the installed app. On a failed build this becomes View errors |
| Secondary buttons | Two outline buttons side by side, 40 dp tall: Build log and Share APK |

**Behavior**

- While building, show the step list with a progress bar and a Cancel text button in the error color. Keep the screen awake.
- On success, install the APK automatically. If Android asks for permission to install, explain it in one line and open the system settings page.
- Open app launches the app. A floating button Back to editor (36 dp tall, pill shape, chipBg, arrow-left icon) stays on top of the app for a few seconds after launch and then shrinks to a small edge handle, so one tap returns to the editor. This needs the overlay permission, which is asked for once with a clear explanation. If it is refused, show a notification with a Back to editor action instead.
- Build log opens a full-screen log with the same style as the console. Errors have a source link that opens the file at that line.
- Share APK opens the system share sheet with the built file.
- Show the last build time and size under the buttons for apps that were built before.
- Failed build: list each error as a card with the message in plain words, the file and line as a link, and a Fix with AI button when the AI feature is available.

## Screen previews for Compose

For Android projects the preview icon shows pictures of the app's screens while the user codes. Every function marked @Preview is rendered to an image and shown on a card. This is the heaviest feature in this document and should be built last.

**How it works**

1. When the user saves a Kotlin file, find all functions marked @Preview in the project.
2. Compile only what those functions need, using an incremental build, and render each one with a Compose preview renderer (the layoutlib based renderer used by Android Studio) at the chosen device size and theme.
3. Return one PNG per function and show it on its card.

These are static images. Buttons inside them cannot be tapped. To test taps, use Open app from the build panel.

**Top row** (8 dp padding, 1 dp bottom divider)

- Device chip: phone icon, device name such as Pixel 8, down chevron. Opens a list of devices (Pixel 8, Pixel Fold, small phone, tablet).
- Theme chip: sun icon and Light, or moon icon and Dark. Tapping toggles the theme of all cards.
- On the right: a green 7 dp dot, the text Updated 2 s ago (11sp, textMuted) and a refresh icon.

**Views** (a segmented control or three buttons)

| View | Layout |
| --- | --- |
| All screens | Two columns, 14 dp vertical gap and 10 dp horizontal gap. Each card is a phone picture 132 by 268 dp with a 3 dp rounded border (#232938, radius 16 dp), and the function name with a status icon under it (12sp) |
| Split view | Code on the top half and a horizontally scrolling row of phone pictures below, at 0.82 scale. A 22 dp drag handle between them resizes the two halves. The cursor's function is scrolled into view in the row |
| Full screen | One phone picture scaled to 1.32. Under it left and right arrows with the function name and page dots in the middle, and an Open in code button |

**Card states**

- **Done:** the rendered picture and a green check next to the name.
- **Rendering:** the previous picture (if any) is dimmed by a 62% dark layer with a spinner and the word Rendering. A clock icon is next to the name.
- **Failed:** the card shows a dark red panel with an alert icon, the title Preview failed, the error in monospace and the file and line, and a small Go to error button that opens the file at that line. A red cross is next to the name.
- **No previews yet:** show the text No previews found and a short example of a @Preview function with a Copy button.

**Gestures**

- Tap a card to open it in Full screen.
- Long press a card to open its function in the editor.
- Pinch in Full screen to zoom.

For website projects this preview icon shows the website preview instead. The app decides by project type.

## Compose implementation notes

These are suggestions; any approach with the same result is fine.

1. **Git.** Use JGit (org.eclipse.jgit) for init, clone, status, commit, push and pull on the device. It needs no installed git program. Run every Git call off the main thread and expose the results as StateFlow so the screens update by themselves.
2. **File explorer.** Build the tree from a flattened list of visible rows in a LazyColumn with fixed 36 dp rows. Keep open folders in a Set and rebuild the list when it changes. Watch the project folder with FileObserver to refresh the tree and Git letters.
3. **Git letters.** Compute them from one JGit status call, cache the result, and refresh after save, stage, commit and every 5 seconds while the explorer is open.
4. **GitHub sign-in.** Use the device flow endpoints on github.com (device code request, then polling for the access token) with a client id registered for the app. Store the token with EncryptedSharedPreferences or Android Keystore. Never log it.
5. **Clone.** Run JGit clone with a ProgressMonitor that reports percentage and bytes to the progress screen. Clone into a temporary folder and move it into place when finished, so a cancelled clone leaves nothing behind.
6. **Terminal.** Run the shell with ProcessBuilder or a PTY library such as termux terminal-emulator, so arrow keys, Ctrl+C and ANSI colors work. Draw output in a Canvas or LazyColumn of annotated strings. Keep a ring buffer of the last 5,000 lines.
7. **Website preview.** Use a WebView with a small local HTTP server (for example NanoHTTPD) serving the project folder, so relative paths work. Inject a script that forwards console messages and errors to the app through WebChromeClient.onConsoleMessage and a JavaScript interface. Set the WebView's viewport and user agent per device size.
8. **Auto reload.** Debounce file saves by 500 ms, then call reload and restore the saved scroll position after onPageFinished.
9. **Android build.** Run the Gradle or on-device build in a foreground service so it survives leaving the app, and stream each step's status to the panel. Install with PackageInstaller and launch with the package manager's launch intent.
10. **Back to editor button.** Use a small overlay window (needs the draw-over-other-apps permission) and fall back to a notification action if the user does not allow it.
11. **Compose previews.** Treat this as its own project. It needs a renderer that can run @Preview functions headlessly. Do it after everything else, and ship the other features first.
12. **States.** Every screen needs empty, loading and error states. Build them as a sealed UiState class so none are forgotten.

Common mistakes to avoid:

- Showing technical Git words (Initialize repository, Not under version control, device flow) to the user.
- More than one large blue button on a screen.
- Making the user tap a button after authorizing GitHub when the app can detect it by itself.
- A blank white page in the preview when the page fails to load.
- Running Git, builds or file scans on the main thread.
- Storing the GitHub token in plain text or in the project folder.

## Acceptance checklist

A screen is done only when every item for it is true on a real phone. Send a screenshot of each state for review. Build in the order of the sections: explorer, source control, GitHub sign-in, clone, terminal, website preview and console, Android build panel, then screen previews.

**File explorer**

- [ ] Segmented control shows Files and Source Control with a count bubble.
- [ ] Header shows the project name and the four icon buttons; the old EXPLORER and PROJECT labels are gone.
- [ ] Rows are 36 dp with colored file icons, folder counts on closed folders and Git letters on the right.
- [ ] The open file has the blue bar and highlight; unsaved files have a yellow dot.
- [ ] Long press shows Rename, Duplicate, Copy path and Delete (with confirmation).

**Source control and GitHub**

- [ ] A project without Git shows Get started with Git with the three option cards.
- [ ] Start tracking creates the Git history and switches to the changes screen.
- [ ] The changes screen has Staged and Changes lists, the commit box and one primary Commit button.
- [ ] Commit is disabled until there is a message and at least one staged file.
- [ ] Sign-in shows three numbered steps, a Copy button, an Open GitHub button and a countdown.
- [ ] The app connects by itself after authorizing, and handles expired code, denied access and no internet.

**Clone**

- [ ] The folder name fills in from the link and the Clone button turns blue only for a valid link.
- [ ] Not signed in shows the sign-in card; signed in shows a searchable repository list.
- [ ] Progress shows a bar, percentage, size and three steps; Cancel removes the partial folder.
- [ ] The project opens by itself when the clone finishes.

**Terminal**

- [ ] The prompt is inline in the output with a blinking cursor; there is no separate input bar.
- [ ] One row of keys: ESC, TAB, CTRL, ALT, |, \~, /, up, down.
- [ ] Commands chip opens the saved commands sheet; tapping a command types it without running it.
- [ ] Output uses colors for folders, errors and Git changes.

**Previews**

- [ ] Website preview has the address pill, refresh, open in browser and Console button with an error badge.
- [ ] Phone, Tablet and Desktop chips change the page width and the size label.
- [ ] The page reloads by itself after saving and keeps its scroll position.
- [ ] Console shows errors, warnings and logs with filters; tapping an error opens the file at that line.
- [ ] Android build panel shows the steps with times, then Open app, Build log and Share APK.
- [ ] Back to editor returns from the opened app in one tap.
- [ ] Screen previews show cards for every @Preview function with Done, Rendering and Failed states, in All screens, Split and Full screen views.
