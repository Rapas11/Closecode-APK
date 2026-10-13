# Part B: Rebuild Brief for the New Developer

Oct 12, 2026 · @Hh4hh

## Read this first

You are a new developer, working on a new machine, on a project that another developer worked on before you. You have not seen the earlier conversations. This document gives you everything you need. Read all of it before you change any code.

**Who you work for**

The owner of the app, called the user here. The user is not a programmer, and English is not the user's first language. The user tests on their own Android phone, takes screenshots, and passes your messages to and from other people. The user does not have a computer for this work.

**What the app is**

CloseCode is a code editor and project builder that runs on an Android phone. It is built with Kotlin and Jetpack Compose, and its package name is com.closecode. A user can create and edit projects: websites (HTML, CSS, JavaScript) and Android apps (Kotlin with Jetpack Compose). The goal of this brief (Part B) is that the app can build, install and run Android projects on the phone itself.

**Two parts of work**

- **Part A** is the small fixes: screens, keyboard behavior, navigation and similar. They are described in three design documents (listed in the next section).
- **Part B** is the rebuild: real builds, install, real previews and a real terminal. This is the main part of this brief.

The user wants Part A finished before Part B. Phase 0 below tells you how to find out what is already finished.

**Rules for working with this user**

1. **You cannot run the app on the user's phone.** The user installs the APK you deliver and tests it. So "done" means nothing until the user has tested it. Use the reporting labels in the section Reporting and testing rules.
2. **Treat every claim from the earlier developer as unverified.** That developer reported many things as done that did not work on the phone. Check the code, then ask the user to test.
3. **Plain, short messages.** Write the way you would to a friend who is not technical. No long technical text. Put exact technical detail in files or commit messages, not in messages to the user.
4. **No time estimates.** The user does not want to hear weeks or days. Say what you are doing and what you need.
5. **Ask only when you are blocked,** one question at a time. When something blocks you, tell the user the same day and give the exact reason.
6. **Do not redesign.** The visual design is approved. Match the documents and pictures exactly. If you think a design is wrong, say so and wait for the answer.
7. **Be careful with existing work.** Do not delete or rewrite code that works. Make the smallest fix that solves a problem. Do one commit per phase, keep the test suite green before every commit, never force-push, and keep the git history.
8. **Be fast.** The user wants an APK as soon as each step works. Do not hold results back to the end.

## What exists today

**The project**

The repository of the CloseCode app (package com.closecode). Ask the user for the repository link or path if you do not have it. An earlier developer reported that the last of nine phases was pushed as commit a58a5b4 with 1041 tests passing. Treat that as a pointer to look at, not as proof that anything works. Tests that pass on a computer do not prove that a screen or a keyboard behavior works on a phone.

**Design documents (the user will send them with this brief)**

1. **Code Editor Design Spec (Android Compose).** The editing area, top bar, tabs, brace underline, autocomplete, lightbulb, error pill, signature tooltip, selection toolbar, fold arrows, git marks.
2. **App Screens Design Spec (Android Compose).** File explorer, source control, GitHub sign-in, clone, terminal, website preview, console, Android build panel, screen previews.
3. **Screens Update Spec: Terminal, Files, Diff, Search, Source Control.** The newest corrections to nine screens, with a pictures list.

The user will also send screenshots of the approved designs. Match the screenshots first, then use the numbers in the documents. If a picture and a document disagree, the newer document wins.

**What the user has tested on the phone**

The user tested several APKs. From the last tests, these worked:

- The editor key row appears with the keyboard and disappears when it closes.
- A tapped line scrolls into view above the keyboard and key row.
- The terminal follows streaming output.
- The New command form opens inside the commands panel with proper Cancel and Create buttons.
- The screen preview cards read their texts and buttons correctly, the Approximate preview label shows, and the error banner works.

These were wrong in the last tests, and are described in the third document:

- Terminal typed text is changed by the keyboard (Ls, Is, Ping), and the last line of an ls -la listing is broken.
- The file drawer has an empty block at its top and does not cover the bottom bar.
- The diff view is side by side and cut off on a phone. Search results show the wrong file name and no highlight.
- Source Control puts the history below the changes.
- A red test line is still drawn in the editor.

These had been wrong earlier, and the user has not confirmed that they are fixed: system Back and the Back button in the Terminal header did nothing. Check them in Phase 0.

**Test projects on the user's phone**

- **Landing page.** A small HTML website.
- **Blank app.** A small Kotlin and Compose app made from the template.
- **Biz.** The user's real Android app, and the user's main pain. It is called BizTrack in its code. From the file tree, it uses Compose, Navigation, Supabase, a charts library (Vico), a database folder (core/database), a Gradle version catalog (gradle/libs.versions.toml) and the CAMERA permission. At the moment the app says Biz is Not packable. Phase 1 fixes this.

## Phase 0: check the current code first

Another developer already changed this project, and the user has not confirmed that work. Before you build anything new, check what is really in the code. If something is wrong, make it right. This is the first thing you do.

**Why this phase exists**

The earlier developer often reported a feature as done when it did not work on the phone. Names, comments and passing tests do not prove that a feature works. So you check behavior, not names.

**Steps**

1. **Set up your machine.** Get the repository, install what the project needs to build, and build a debug APK. Run the whole test suite and write down how many tests pass and fail. You need to be able to produce app/build/outputs/apk/debug/app-debug.apk, because the user installs APKs from you.
2. **Read the three design documents and the pictures** (see What exists today).
3. **Find where each feature lives in the code.** At least: top bar and tabs, editor (drawing, scrolling, gutter, popups, selection toolbar), keyboard handling, navigation and Back, bottom bar and key row, file drawer, source control (git), GitHub sign-in, clone, terminal, website preview and console, Android build panel, screen previews, search, diff and the project package rule.
4. **Give every requirement in the documents one status:**

| Status | Meaning |
| --- | --- |
| Matches | The code does what the document says. You read the code and, where possible, a test proves it |
| Different | The code does something else. Say what |
| Missing | Nothing in the code does it |
| Broken | The code exists but behaves wrongly. Say how you know |

5. **Look closely at these known trouble spots:**

- System Back, the back gesture and the Back button in the Terminal header. They did nothing in an earlier test. Check that every handler is enabled only while its popup or panel is visible, and that unhandled Back pops the screen stack.
- The Search / AI / More bar versus the editor key row. The key row must show only while the keyboard is visible, read from WindowInsets (isImeVisible).
- Keyboard flags in the terminal, New command field, search field and editor: no auto capital letters, no auto-correct, no suggestions.
- Scrolling rules in the editor: the scroll position changes only for the user's own actions.
- Leftover test code: the red 2 dp line in the editor, and test logs.
- What the Run button does for an Android project. The earlier developer said it only runs a Kotlin analyser over up to 60 files and reports errors, and builds no APK. Confirm this.
- The project package rule that makes Biz say Not packable.
- The terminal: a plain pipe or a PTY, the ANSI color parser, stderr in red, the COLUMNS setting, and how the last line of output is flushed.
- Screen previews: how texts and button labels are read from a function body.
- Search: where each result gets its file name.
- Git: which library is used, how status letters are computed, whether history exists.
- GitHub sign-in: the stored client ID, the device flow calls, and how the token is stored (it must be in the Android Keystore backed storage, never in plain text or logs).

6. **Fix what is wrong.** Make the smallest fix that works and add or update a test. Fix Part A problems now, unless a fix is large, then list it. Leave Part B work (build chain, install, previews, terminal) to its own phase.
7. **Send the user a short audit report** in plain words: for each area one line with its status, what you changed, and what the user must test on the phone. Use the labels from the Reporting and testing rules. Then build a fresh APK and send it.
8. **Continue straight into Phase 1** without waiting for an answer, unless the audit found something that blocks it. In that case tell the user the exact reason the same day.

**What a good audit report looks like**

Example lines: Back navigation, Broken, handler in the Terminal page stays enabled and swallows the press, fixed, written but not tested on a phone. Key row, Matches, tested on the phone by the user earlier. Search file names, Broken, every result reads one shared variable, fixed, needs a phone test.

## The goal of Part B and the decisions already made

**The goal**

A user writes code in CloseCode on their phone and taps Run. The app checks and compiles the project. Compile errors show as cards with links to the exact file and line. The user fixes them and taps Run again. The app builds a real APK, signs it, installs it on the same phone, and Open app starts it. The screen previews show the app's real colors and real buttons, and the terminal is a real terminal. After a first setup download, all of this works offline.

**Decisions made by the user (do not reopen them)**

1. **Everything runs on the phone.** No server, no monthly cost, and no project code is uploaded anywhere. Earlier ideas about a cloud build server and cloud rendering were dropped.
2. **Open source is fine.** The user's app can be open source, so GPL code from Termux, CodeAssist and AndroidIDE may be reused where it makes the work faster. Rules for this: check the license of every part you reuse, one by one (do not assume that a whole project has one license), keep every license file and notice, check that the other libraries in the app are compatible, and tell the user in plain words what they must do (for example publish the source code under the right license and add a LICENSE file). The earlier developer planned to write everything from scratch to avoid GPL code. That is no longer needed.
3. **First setup download.** About 200 to 300 MB of build tools (compiler libraries, ECJ, D8/R8, apksig, android.jar, Compose libraries and an arm64 aapt2). It is resumable, uses Wi-Fi by default with an option for mobile data, and shows a progress screen. The app needs about 1.5 GB of free storage and shows a clear message if there is less.
4. **Signing.** Debug signing with an automatically generated key. Release signing comes later.
5. **Package names.** New projects get the default package com.closecode.\<name>, which the user can edit when creating the project. The package rule checks whatever package is saved and only warns about a package that looks foreign. It never blocks. Imported projects keep their own package name and you never rewrite it.
6. **What builds first.** Projects made from the app's own templates. The goal is any Android app, reached in stages (see Phase 3). Be honest in the app about what is not supported yet: name the unsupported line or library in plain words. Do not say never. Say not planned for now.
7. **Versions.** The earlier developer pinned Kotlin 1.9.24, Compose compiler 1.5.x and Compose BOM 2024.04.01, saying they came from the user's templates. Check this against the templates in the repository. If they differ, use the templates' versions and tell the user.
8. **Honest build panel.** Until a real build works, the panel says Compile check and shows no Open app or Share APK buttons.
9. **GitHub sign-in.** The user turned on Device Flow in their GitHub OAuth app. The app already carries a client ID. If sign-in fails with it, ask the user for the client ID only, never the secret.
10. **The design does not change.** Match the three documents and the pictures.

**What the user does not want**

- Time estimates, and the word done for untested work.
- A server, a subscription, or anything that costs money each month.
- A screen that looks real but is fake. Where something is approximate, say so on screen.

## How the on-device build works

This section explains the logic of the build, so you can decide how to build each step. The user's phone has no JDK, no Gradle and no Android SDK. Everything the build needs must be downloaded once and then run on the phone.

&#91;embedded content: build chain, 9 steps, a failing step stops it\]

**The idea in plain words**

Building an Android app means turning the project's files into one APK file in a fixed order. Each step needs the result of the step before it. If one step fails, the build stops, the app shows the errors as cards with the file and line, and the last good APK stays untouched. A build that never produced an APK must never show Open app or Share APK.

**The tools for each step**

| Step | Tool | What it does | Runs as |
| --- | --- | --- | --- |
| 2 Check the project | Our own code | Checks the package name, versions and libraries before anything starts, so the user gets a clear message early | Inside the app |
| 3 Resolve libraries | Our own resolver | Reads the declared libraries (including BOMs and the libraries they need) and finds their jar and AAR files in the downloaded cache | Inside the app |
| 4 Compile resources | aapt2 for arm64 | Compiles the res folder and the manifest into binary resources and creates the R class | A native program |
| 5 Compile code | kotlin-compiler-embeddable with the Compose compiler plugin, and ECJ for Java files | Turns Kotlin and Java sources (and the generated R and BuildConfig) into class files | Inside the app, on the Android runtime |
| 6 Make dex | D8 from the R8 library, minimum API 29 | Converts class files into dex files | Inside the app |
| 7 Pack the APK | A zip writer | Puts the manifest, resources and dex files into one APK | Inside the app |
| 8 Sign | apksig with an automatically made debug key | Signs the APK so Android accepts it | Inside the app |
| 9 Install and open | PackageInstaller and a launch intent | Installs through the Android consent screen, then starts the app | Android system |

**Android limits you must plan for**

1. **There is no JDK on Android.** Kotlin compiler, ECJ, D8 and apksig are Java libraries. Run them inside the app's own runtime as libraries. Check how much memory the compiler needs on the user's phone and limit it. Ask the user for the phone's model and free memory.
2. **aapt2 is a native program, and Google publishes no arm64 build.** Community projects publish arm64 builds (for example the tools releases of AndroidIDE). Check the license, test that it runs, and use it. If there is no usable build, compile aapt2 from the AOSP source with the Android NDK.
3. **Android blocks running programs that were downloaded into the app's storage.** Ship every native program inside the APK as a library file (lib something .so in jniLibs) and run it from the app's native library folder. Never download an executable and run it from files or cache.
4. **Storage.** The tools, libraries and outputs need about 1.5 GB free. Check before the setup download and say so clearly.
5. **Installing needs the user's permission twice.** The app needs the install packages permission, and Android 12 and later sends the user once to a settings page to allow installs from this app. Then the system shows its own consent screen for every install. Explain both in one plain line each.
6. **Template contract.** In the first version a project must be buildable by construction: fixed Kotlin and Compose versions, declared libraries only, and no Gradle scripts to run. This is why the build needs no Gradle. Anything outside the contract fails early in step 2 with a plain message that names the unsupported line.

**Two possible roads (a design choice, already made)**

- **Slim chain** (the table above). No Gradle. Fast to reach a first real build, and it is the road a CodeAssist-like app takes.
- **Heavy chain.** A full Gradle with its own JDK and SDK on the phone, the road an AndroidIDE-like app takes. It can build projects with custom Gradle scripts, but it is much larger.

Start with the slim chain. The heavy chain stays possible later for projects that need it, so write that as not planned for now, never as impossible. Because open source is allowed, look at what you can reuse from either project and tell the user what it saves.

## Phase 1: unblock the Biz project

**What is wrong**

The user's real app, Biz, shows Not packable. The app has a project contract: a check that decides whether a project can be built. It rejects Biz, so nothing that depends on the build works for Biz. Biz uses the package com.example, which does not look like a CloseCode package, and the check treats that as a failure.

**What is required (the user's decision)**

1. **Default package for new projects:** com.closecode.\<name>, where name is the project name in lowercase letters and digits.
2. **The package field is editable** when the project is created and in the project settings.
3. **The contract checks whatever package is saved,** with two different results:

- A package that is not a legal Android package name is an error with a plain message. Legal means: at least two parts separated by dots, every part starts with a letter, and parts contain only letters, digits and underscores. Words reserved by Java (like class or new) are not allowed as a part.
- A legal package that only looks foreign (for example com.example) gives a warning and never blocks. The warning says in plain words that the app will install under that name.

4. **Clash warning.** If another project in CloseCode uses the same package, show a warning: installing this app would replace the other one. It does not block.
5. **Imported and cloned projects keep their own package name.** Read it from the project's manifest and build files. Never rewrite it.
6. Biz must no longer say Not packable because of its package.

**Then find out what else stops Biz from building**

The package fix only removes the first blocker. Biz is a real app, much larger than the template. Before Phase 3, read its build files (gradle/libs.versions.toml, app/build.gradle.kts, the manifest and the folders under app/src) and write down:

| Item | What Biz uses | Does the first build chain support it? | What it takes if not |
| --- | --- | --- | --- |
| Gradle plugins | for example Kotlin Android, Compose, kotlinx serialization, KSP or kapt, Hilt, Google services |  |  |
| Libraries | for example Supabase and its network engine, Vico charts, Compose Navigation, a database library |  |  |
| SDK levels | compileSdk, minSdk, targetSdk |  |  |
| Manifest | permissions such as CAMERA, the application class BizTrackApplication, activities |  |  |
| Generated code | BuildConfig, ViewBinding, Room or other annotation processors |  |  |

Do not guess. Read the files. Libraries such as Supabase usually need the kotlinx serialization compiler plugin, and a database folder often means Room, which needs an annotation processor. If something is not supported in the first version, tell the user in plain words which part of Biz it is and what it takes to support it. This tells the user honestly when Biz can be built.

**Care with secrets**

Biz has a .env.example file and uses a backend. Never print, log, commit or upload any key or secret. If a real .env file exists, leave it alone.

**How to verify**

- A test: a new project gets the default package and passes the contract.
- A test: a custom legal package passes.
- A test: a clash with another project shows a warning and does not block.
- A test: an illegal package such as com..bad or 9com.x fails with a plain message.
- A test with a Biz-shaped fixture (a copy of Biz's manifest and build files without secrets): it passes the contract, with a warning for com.example.
- On the phone: the user opens Biz and sees that Not packable is gone. Send a fresh APK.

## Phase 2: GitHub sign-in, clone and push check

Do this right after Phase 1. It is small, and the user needs it to back up and share code.

**Background**

The app signs in to GitHub with the device flow. The app asks GitHub for a short code, the user types the code on github.com/login/device, and the app is connected without ever seeing the user's password. The user has turned on Device Flow in their GitHub OAuth app. The app already carries a client ID. The screens for this are in the App Screens Design Spec (GitHub sign-in and Clone) and in the Screens Update Spec (Source Control).

**What to check and fix**

1. **Sign in.** Start sign-in. The app must show three steps: copy the code, open GitHub, paste the code and authorize. Copy changes to Copied. The countdown ticks. After the user approves in the browser, the app connects by itself without another tap. Test these cases: success, expired code, access denied, and no internet.
2. **If the stored client ID fails** with an error such as device\_flow\_disabled or incorrect\_client\_credentials, tell the user the exact error. Then ask the user for the client ID only. Never ask for or store the client secret. A device flow does not need it.
3. **Token storage.** The access token must be kept in Android Keystore backed encrypted storage. It must never appear in logs, project files or the git configuration. Check how the earlier code stores it. The earlier developer said that on a broken Keystore the token falls back to plain preferences. Change that fallback: sign the user out and ask them to sign in again instead of storing it in plain text.
4. **Clone.** Clone a small public repository by link. Clone a private repository from the signed-in list. Check the progress screen, Cancel (it must delete the partly downloaded folder) and the finished project opening by itself.
5. **Commit and push.** In a cloned or new project, change a file, stage it, commit it with a message, and push to the user's repository. Check that Pull and Push show their counts and that errors (no internet, rejected push) are explained in plain words.
6. **Source control screens** as in the Screens Update Spec: the GitHub banner when not connected, the Connect button, the Pull and Push icons when connected.

**Report to the user**

Tell the user in plain words which of these worked, which failed with the exact reason, and what you changed. Label each item with the reporting labels. Use the user's phone for the sign-in test: only the user can approve the code in their GitHub account.

## Phase 3: the real build chain

**Goal**

The Blank app builds end to end on the phone with Wi-Fi switched off, and a file app-debug.apk exists in the project's build/outputs/apk/debug folder. When a build fails, the errors show as cards with the file and line. Use the diagram and the tool table in How the on-device build works.

**3.1 The setup screen and the downloads**

1. A list (manifest) of everything to download. Each entry has a name, version, address, size, SHA-256 checksum and license: the Kotlin compiler and Compose compiler plugin, ECJ, R8 (for D8), apksig, android.jar for platform 35, the Compose libraries the templates use, and the arm64 aapt2 program.
2. **Phone format.** The Java libraries are desktop bytecode, and the phone's runtime loads dex files. When you prepare the release on your computer, convert each library to dex with D8, put the dex files in the download, and list them in the manifest. The app then loads them with a class loader.
3. Downloads are resumable and verified by checksum. They use Wi-Fi by default, with a switch for mobile data. Show a progress screen. Refuse to start under 1.5 GB of free storage, with a plain message.
4. After the download, nothing needs the network.
5. If tools are missing or damaged, the Build button shows an honest setup screen with a button to download them again.

**3.2 The build engine**

Write it as a list of tasks. Each task has inputs, outputs, progress, a possible error, and can be cancelled. Skip a task when its inputs did not change (compare hashes). Run the build in a foreground service with a notification, so it keeps running when the user leaves the screen, and keep the screen on while it runs.

Task details, in order:

1. **Contract check** (Phase 1 rules).
2. **Resolve libraries.** Read the declared libraries, including BOMs and the libraries they need. For each AAR, take out classes.jar, the res folder and the manifest.
3. **aapt2.** Compile the res folders into flat files, then link them with android.jar and the merged manifest. Replace ${applicationId} in the manifest with the package, and set the SDK levels and version values. The output is a resources APK and the R class.
4. **BuildConfig.** Generate BuildConfig.java from the project values.
5. **Compile code.** Run the Kotlin compiler with the Compose plugin first, so Kotlin can see the Java sources. Then run ECJ for the Java files with the Kotlin classes on its class path. The class path contains android.jar and the libraries.
6. **D8.** Turn the classes of the project and its libraries into dex files, minimum API 29, with android.jar as the library.
7. **Pack.** Add the dex files to the resources APK. Keep resources.arsc stored without compression and aligned to 4 bytes, because newer Android versions require it.
8. **Sign** with apksig using one debug key that is created once and reused. If the key is lost, an old installed version must be uninstalled first. Say this plainly in the error message.
9. The finished file is build/outputs/apk/debug/app-debug.apk in the project.

**Errors**

- Collect diagnostics from aapt2, the Kotlin compiler, ECJ and D8 with file, line, column and message. Show them as error cards in the build panel with a link that opens the file at that line, and keep the full log in Build log.
- Rewrite common messages into plain words when you can, and always keep the original text available.
- A failed build keeps the last good APK. Show a red result bar and no Open app button.

**Memory and speed**

The Kotlin compiler needs a lot of memory. Measure the peak memory of a Blank app build on the user's phone, limit the compiler's heap and the number of parallel workers, and tell the user the numbers. Ask the user for the phone's model and free memory.

**3.3 What the first version builds**

Projects made from the app's own templates: known libraries, plain manifests, the ${applicationId} replacement, BuildConfig generation, debug builds only, and no Gradle scripts. Not in the first version: Room, Hilt, Firebase, any KSP or kapt, custom Gradle plugins and scripts, AIDL, ViewBinding, app bundles, JNI code and release signing. The Biz table in Phase 1 tells you which of these Biz needs. If Biz needs some, they come first in the next stage.

**3.4 The next stage, toward any Android app**

In this order:

1. Read a small subset of build.gradle.kts (package or namespace, SDK levels, declared libraries). Anything else fails early with a plain message that names the unsupported line.
2. A Maven resolver for jar and AAR files with the libraries they need. It downloads from Maven Central and Google's Maven when online and caches for offline use.
3. Manifest merging (package, label, theme, icon, activities, permissions).
4. A ViewBinding generator.
5. A KSP runner (Room and Hilt processors as Maven libraries) and the kotlinx serialization compiler plugin.
6. Firebase google-services.json handling.
7. A fuller resources step.
8. App bundles and release signing with the user's own key.

Custom Gradle plugins and scripts are not planned for now. Such projects can be exported to a computer.

**3.5 How to verify**

- Unit tests for each task with small fixtures: the contract, the resolver, the manifest replacement, the BuildConfig generator and the error mapping.
- On the phone with Wi-Fi off: Blank app builds and the APK file exists.
- On the phone: add a mistake such as an unknown name. The build fails with an error card, tapping it opens the file at that line. Fix it and build again successfully.
- Send the user an APK as soon as the setup screen works, and again as soon as the first real build works.

## Phase 4: install, open and share the built app

**Goal**

After a successful build, the user taps Open app. The app installs the APK, then starts it. The user can also share the APK file. The screen design is in the App Screens Design Spec, section Android build and run panel.

**What to build**

1. **Open app button.** It appears only after a successful build, as the one blue button of the panel. It installs and then launches the app. After a failed build the same place shows View errors.
2. **Install permission.** The app declares the install packages permission. On Android 12 and later, the user must allow installs from CloseCode once in the system settings. Show one plain line that explains why, and a button that opens the right settings page. When the user comes back, continue the install without starting over.
3. **Install with PackageInstaller.** Create a session, write the APK into it, and commit it with a status receiver. Show the real result: installed, cancelled by the user, or failed with the system's reason in plain words. Handle the case that a version signed with a different key is already installed (ask the user to uninstall it first).
4. **Launch.** After a successful install, start the app with the package manager's launch intent for its package.
5. **Back to editor.** A small floating button, 36 dp tall, pill shaped, background #1A1F2B, with an arrow-left icon, stays on top of the opened app for a few seconds and then shrinks to a small edge handle, so one tap returns to CloseCode. It needs the draw-over-other-apps permission. Ask for it once with a clear plain explanation. If the user refuses, show a notification with a Back to editor action instead.
6. **Share APK.** Share the built file through the system share sheet using a FileProvider.
7. **Build log.** A full-screen log with the same style as the console. Errors have a link that opens the file at that line.
8. **Logcat view of the opened app** (a simple list of the app's log lines) so the user can see why it crashed.
9. **Last build line.** Under the buttons show the time of the last build and the file size.

**Honesty rules**

- Never show Open app or Share APK when no APK exists.
- Never show an app as installed unless the system confirmed it.
- Keep the panel title as Compile check until a real build can produce an APK, then change it to Build.

**How to verify**

- On the phone: build Blank app, tap Open app, allow the install once, and see the app start.
- Tap the floating Back to editor button and return to CloseCode.
- Share APK opens the share sheet with app-debug.apk.
- Build a project with a deliberate error: there is no Open app button, only View errors.
- Build again after changing the app: the new version installs over the old one.
- Send the user a fresh APK as soon as install and open work.

## Phase 5: real screen previews

**Goal**

The screen preview cards show a true picture of each screen, with the app's real colors, buttons, images and layout. Today the cards are sketches. The sketch only reads the texts and button labels written inside each @Preview function and draws a simple card, so it cannot show real colors. It is labeled Approximate preview. A website preview already shows the real page in a browser view, and it stays as it is.

**Why this is possible on the phone**

The usual tool for pictures of screens (layoutlib) needs a desktop computer and does not run on a phone. But the phone already runs Android. After the app is built (Phase 3), the app can run each screen in the real Android runtime and take a picture of it. That needs no server.

**How it works**

1. When the user saves a Kotlin file, find all functions marked @Preview in the project.
2. After a successful build, generate a small harness in the project: for each @Preview function, a tiny screen that shows only that function.
3. Build a runner APK from the user's classes plus the harness, and install it (Phase 4 does the install). The runner has a static manifest and asks for no permissions from the user.
4. Start the runner's screen in a separate process (:preview), so a crash cannot take CloseCode down. In it, show each preview in a view with the chosen device size and theme, and draw the view into a bitmap (for example with drawToBitmap).
5. Return the pictures to CloseCode as PNG files, scaled down to the card size before they cross between processes, so large pictures do not fail.
6. Show the pictures on the cards.

The user's Theme file is just code, so it is compiled with the rest and the preview uses the real theme.

**Card behavior (keep the existing design)**

- While a picture is not ready, the card shows the sketch with the Approximate preview label, dimmed with a spinner and the word Rendering. Never show a blank card.
- When the real picture arrives, show it and remove the Approximate preview label.
- Offline, or when the build or the render fails, show the sketch with the label again, and say why in one plain line.
- Failed state, device list, Light and Dark, All screens, Split view and Full screen all stay as described in the App Screens Design Spec.
- These pictures are static. Taps inside them do nothing. To test taps, the user uses Open app.

**Things that can go wrong, and what to do**

- The picture is too large to pass between processes: scale it first.
- The manifest of the runner is missing something: keep it static and minimal.
- A screen needs resources or a theme: compile the user's Theme file and resources into the runner.
- A function takes parameters: support @Preview functions without parameters first, and show a plain message for the others.

**How to verify**

- On the phone, with the test file that has 8 @Preview functions: eight real pictures show with real colors, and the label is gone.
- Change a color in the code and save: the card updates to the new color.
- Switch Light and Dark and the device size: the pictures change.
- Switch Wi-Fi off: pictures still render, because nothing is uploaded.
- Break a function on purpose: the card falls back to the sketch with a plain reason.
- Send the user an APK as soon as one real picture shows.

## Phase 6: the real terminal

**Goal**

The terminal behaves like a real terminal. Today it runs the shell through plain pipes. Programs such as ls and git see that they are not talking to a real terminal, so they print no colors, and Ctrl+C, the arrow keys and interactive programs do not work properly. A real terminal uses a PTY (a pseudo terminal): a pair of connections that make the shell believe a person is typing at a real screen.

**What the user decided**

The user's app can be open source, so GPL code may be reused. The earlier developer planned to write his own PTY bridge and screen parser because he believed a GPL license was a problem. That reason no longer applies. Use what is fastest and safest.

**Suggested way**

1. **Reuse the Termux terminal code** (the terminal emulator, the terminal view and the native code that opens the PTY), or the Apache-licensed emulator it came from, whichever is faster. Check the license of every module you take. Keep all license files and notices, and tell the user what they must do for the license.
2. **The native piece.** Build the small native library that starts the shell on a PTY (a forkpty style call) with the Android NDK on your machine. Ship it inside the APK as a library file (jniLibs) and load it with System.loadLibrary. This avoids the rule that blocks running downloaded programs.
3. **Screen grid.** The emulator keeps a grid of characters with colors, a cursor and scrollback. Draw this grid in the terminal screen. Keep the existing look: the header, session row, key row, Commands panel and Latest pill as in the design documents.
4. **Size.** Tell the PTY how many columns and rows fit on the screen whenever the size changes (keyboard open or closed, key row, rotation). This makes programs such as gradle format their lines to fit the width.
5. **Input.** Typed text goes to the PTY exactly as typed (no auto capital letters or corrections). Ctrl and Alt on the key row are sticky: one tap arms the key for the next character. Ctrl+C stops the running program. The up and down arrow keys move through the command history. Tab completes.
6. **Environment.** Set TERM=xterm-256color, and the project folder as the starting folder.
7. **Colors.** Parse 16, 256 and true colors. Programs now color their own output, so remove the forced color flags that were added for the pipe version.
8. **Keep the pipe version behind a switch** as a fallback until the PTY is proven on the phone.
9. **Everything already working stays:** follow mode with the Latest pill, the running indicator with the Stop button, the Commands panel, the New command form, and the 5,000 line limit.

**Limits to say plainly in the app if they apply**

Android's own shell has few tools. Programs that are not on the phone (for example git, if it is not installed) still print not found. Make that message short and plain: sh: git: command not found.

**How to verify**

- On the phone: run ls --color=auto in the Biz folder. Folders are blue.
- Run ping -c 5 127.0.0.1 and press Ctrl+C in the middle. It stops and the prompt returns.
- Press the up arrow: the last command returns.
- Run a command with long output: lines wrap at the screen width.
- Rotate the phone or open the keyboard: the text still fits the width.
- Run a command that does not exist: one short red line.
- Send the user an APK as soon as the PTY runs a shell.

## Part A leftovers

Part A is the small fixes. The user wants them done before the rebuild, and Phase 0 tells you what is still open. Use this section as the list to check against. The details live in the three design documents.

**The newest list: Screens Update Spec**

The third document describes nine screens and the problems seen in the last APK. Everything in it is Part A, unless Phase 0 shows that it is already done.

- Terminal: the New command form and its layout, the Latest pill rule, the header, text input settings, short error lines, and the broken last line of an ls -la listing.
- File drawer: no empty block, full height over the bottom bar, compact folders, middle ellipsis for long names.
- Diff view: one column with line numbers on a phone, color rows, Show more lines, sideways scrolling.
- Project search results: grouped by file, the real file name on every result, highlighted matches.
- Editor small fixes: sideways scroll snapping to whole characters, no capital letters from the keyboard, removal of the red test line.
- Source control: the changes screen with the action row and banner, the More menu, the History page and the Commit page.

**Older Part A items to check in the code**

These were reported by the earlier developer as fixed. The user saw some of them work and not others. Re-check all of them.

- System Back and the back gesture go back one step at a time. The Terminal's Back button works. On the project list the first Back shows the message Press back again to exit and a second Back within 2 seconds exits.
- The editor key row replaces the Search / AI / More bar while the keyboard is open, and the bar returns when it closes.
- A tapped line scrolls into view above the key row and keyboard.
- Editor scrolling stays smooth with a 5,000 line file and many errors, and the scroll position moves only for the user's own actions.
- Selection toolbar: one toolbar only (not two), with Cut, Copy, Paste, Select all, Comment and Format, and full labels.
- More and Search panels open as bottom sheets directly above the bottom bar.
- The top bar has no back arrow, and the menu button is at the far left.
- Terminal follows streaming output and shows a running indicator.
- Screen preview cards read their texts and buttons only from inside their own function, and the banner replaces wrong failed cards.

**Working in the latest APK, do not break it**

The key row show and hide, the tapped line scroll, the terminal follow mode, the New command form in the panel with proper Cancel and Create, and the preview card fixes.

**How to proceed**

Phase 0 produces the status of every item. Fix what is Broken, Different or Missing, in small steps. Then continue with Phase 1. If a fix is large, tell the user which one, and do it after the phase it blocks, or in the order the user chooses.

## Reporting and testing rules

The earlier developer reported work as done that did not work on the phone. This cost the user a lot of time. These rules prevent it.

**1. Status labels**

Every item you report has exactly one of these labels:

| Label | Meaning |
| --- | --- |
| Not started | You have not touched it |
| Written but not tested on a phone | The code is written and your tests pass, but nobody has tried it on the phone |
| Tested on a phone, works | The user tried it on the phone and it works |
| Tested on a phone, fails | The user tried it and it fails. Say how |

You are not allowed to write done, finished or fixed for anything that is not Tested on a phone, works. You cannot test on the user's phone yourself, so most of your own items are Written but not tested on a phone until the user answers.

**2. Evidence, not descriptions**

For each item, say how you checked it: the test name that passes, or the log lines. Do not describe what the code is supposed to do.

**3. One APK per step**

Build a debug APK with Gradle on your machine after each phase, and as soon as any step works. Its path is app/build/outputs/apk/debug/app-debug.apk. Send the file to the user together with a short list: what works, what does not work yet, and what the user should test, in a few taps each. Never send an APK you did not build from the latest commit.

**4. Version label**

Show the version name, the build number and the short commit code at the bottom of the More sheet (About). Print the same in the debug log. The user can then tell which APK is installed. Raise the build number for every APK.

**5. Debug log inside the app**

Add a Debug log item in the More sheet, off by default and off again after every app start. When on, the app keeps the last 500 lines of useful facts: every Back press with the screen stack and which handler consumed it, whether the keyboard is visible, whether the key row and bottom bar are visible, every scroll change with its reason, build steps with times, and errors. It has a Copy button, so the user can paste the log into a message from the phone. This is how you will see what happens on a phone you cannot touch.

**6. Tests on your machine**

Unit tests for every new piece, the whole suite before every commit, one commit per phase, and a clear commit message. Do not make the tests pass by weakening them. Where a Compose test can run on your machine (for example Back handling with a Compose test rule or Robolectric), write one. If it passes on your machine but fails on the phone, say so and use the debug log to find the difference.

**7. Messages to the user**

- Short and plain. One idea per paragraph.
- Start with the result: what works, what does not.
- End with what you need from the user, if anything.
- No time estimates.
- If something blocks you, say it the same day with the exact reason.
- When the user sends a screenshot with a problem, answer with the cause you found and the fix, not with a list of possible causes.

**8. Temporary things must not stay**

Any test drawing, test label or extra logging you add while working must be removed or put behind the Debug log switch before you send the APK. The red 2 dp line in the editor is an example of what must not stay.

## Glossary

Short meanings of the words used in this brief.

| Word | Meaning |
| --- | --- |
| APK | The file that contains an Android app and can be installed on a phone |
| Debug APK | An APK signed with a development key, for testing. It is not for the Play Store |
| Dex | The form of compiled code that the Android runtime runs. Desktop Java is compiled to class files, and D8 turns them into dex |
| D8 and R8 | Google's tools for making dex. D8 converts, R8 also shrinks. Both are Java libraries |
| aapt2 | Google's native program that compiles an app's resources (layouts, strings, images) and its manifest |
| apksig | Google's Java library that signs an APK |
| ECJ | The Eclipse compiler for Java. It can run inside an app |
| Kotlin compiler (embeddable) | The Kotlin compiler as a library, so it can run inside another program |
| Compose compiler plugin | A part of the Kotlin compiler that understands Jetpack Compose |
| BOM | A list of matching library versions. One entry sets the versions of many libraries |
| AAR | The package format of an Android library. It holds classes, resources and a manifest |
| android.jar | The file that describes the Android API. The compiler needs it to know what Android offers |
| Manifest | AndroidManifest.xml: the app's name, package, permissions and screens |
| BuildConfig | A small generated class with values such as the app version |
| R class | A generated class that gives names to the app's resources |
| PackageInstaller | The Android API that installs an APK and reports the result |
| FileProvider | The Android way to share a file with another app safely |
| Foreground service | A background job with a visible notification, so Android does not stop it |
| jniLibs | A folder inside the APK for native library files, which the app can load and run |
| PTY | A pseudo terminal. It lets a shell behave as if a person were typing at a real terminal |
| ANSI colors | Special characters in text that tell a terminal to change colors |
| JGit | A Java library for git that runs inside an app |
| Device flow | A way to sign in to GitHub by typing a short code in a browser, without a password in the app |
| Contract (project contract) | The app's own check of whether a project can be built |
| GPL | An open source license. Code under it can be reused if the new work is also published as open source under it |
| Part A and Part B | Part A is the small fixes. Part B is the rebuild described in this brief |

## Acceptance checklist

An item is finished only when the user has tested it on the phone and it works. Report each item with the labels from Reporting and testing rules, and send an APK for the user to test.

**Phase 0: check of the current code**

- [ ] You can build the debug APK on your machine and the full test suite runs. You wrote down how many tests pass and fail.
- [ ] Every requirement in the three documents has a status: Matches, Different, Missing or Broken.
- [ ] The known trouble spots in Phase 0 were checked, and the Broken ones were fixed or listed.
- [ ] The user received a short audit report and a fresh APK.

**Phase 1: unblock Biz**

- [ ] A new project gets the package com.closecode.\<name>, and the field is editable.
- [ ] A legal package that looks foreign only warns. An illegal package gives a plain error.
- [ ] A clash with another project's package warns and does not block.
- [ ] Imported projects keep their package name.
- [ ] Biz no longer says Not packable because of its package.
- [ ] The user has a plain table of what else Biz needs (plugins, libraries, SDK levels) and whether the first build supports it.

**Phase 2: GitHub**

- [ ] Sign-in works with three steps, a countdown and automatic connecting.
- [ ] Expired code, denied access and no internet each show a correct message.
- [ ] The token is stored only in Keystore backed storage, with no plain text fallback.
- [ ] Clone of a public link and of a private repository works, and Cancel removes the partial folder.
- [ ] A commit and a push to the user's repository work.

**Phase 3: the build chain**

- [ ] The setup screen downloads the tools resumably, checks them, and refuses under 1.5 GB of free storage.
- [ ] With Wi-Fi off, Blank app builds and app-debug.apk exists.
- [ ] A build with an error shows an error card with file and line, and tapping it opens the file at that line.
- [ ] A failed build keeps the last good APK and shows no Open app button.
- [ ] You gave the user the measured peak memory of a build on the phone.

**Phase 4: install, open, share**

- [ ] Open app installs and starts the built app.
- [ ] Back to editor returns to CloseCode.
- [ ] Share APK opens the share sheet.
- [ ] Rebuilding after a change installs over the old version.

**Phase 5: real previews**

- [ ] Real pictures show with the app's real colors and buttons, and the Approximate preview label disappears.
- [ ] Changing a color in the code and saving changes the card.
- [ ] Light and Dark and the device size change the pictures.
- [ ] Previews still work with Wi-Fi off.
- [ ] A broken function falls back to the sketch with a plain reason.

**Phase 6: real terminal**

- [ ] ls shows blue folders without forced color flags.
- [ ] Ctrl+C stops a running program and the up arrow brings back the last command.
- [ ] Long lines wrap at the screen width, also after the keyboard opens or the phone rotates.
- [ ] A command that does not exist shows one short red line.

**Part A**

- [ ] Every item in Part A leftovers is Tested on a phone, works.

**Always**

- [ ] The version label and the Debug log switch exist, and no test drawings or red line remain.
- [ ] No time estimates in any message to the user, and no item is called done before the phone test.
