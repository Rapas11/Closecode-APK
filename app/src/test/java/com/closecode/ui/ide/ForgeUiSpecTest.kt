package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec coverage for the pieces added after the engine work: the bottom bar, the tool row order,
 * the log cap, image downscaling and the preview formatters.
 */
class ForgeUiSpecTest {

    // -- bottom bar ---------------------------------------------------------------------

    @Test
    fun theBottomBarIsThreeEqualDoorsInOrder() {
        assertEquals(
            listOf("Search", "AI", "More"),
            com.closecode.ui.ide.model.IdeNavItems,
        )
    }

    @Test
    fun theBottomBarKeepsThreeItemsBecauseOnlyThreeArePlaces() {
        assertEquals(3, com.closecode.ui.ide.model.IdeNavItems.size)
    }

    @Test
    fun noLongLivedPanelIsStillOnTheBottomBar() {
        // The editor stays on screen; Preview moved to the path bar, Logcat and Build moved to
        // More, and Terminal took the header's search slot, so none of them can occupy a
        // bottom-bar item.
        listOf("Code", "Preview", "Logcat", "Build", "Problems", "Terminal").forEach { item ->
            assertTrue(
                "$item must not be a bottom bar item",
                item !in com.closecode.ui.ide.model.IdeNavItems,
            )
        }
    }

    @Test
    fun searchOpensFirstSoTheLeftmostItemIsTheOneUsersReachFor() {
        assertEquals("Search", com.closecode.ui.ide.model.IdeNavItems.first())
    }

    // -- explorer drawer ---------------------------------------------------------------

    @Test
    fun sourceControlLeadsTheDrawerAndIsTheTabThatOpens() {
        // Declared once so the strip's order and the default cannot drift apart: the account,
        // clone and publish controls all live behind it, and none of them need a repository.
        assertEquals(listOf("Source Control", "Files"), ExplorerTabs)
        assertEquals("Source Control", ExplorerTabs.first())
    }

    @Test
    fun theDrawerOffersExactlyTwoDistinctTabs() {
        assertEquals(2, ExplorerTabs.size)
        assertEquals(2, ExplorerTabs.distinct().size)
    }

    // -- path bar -----------------------------------------------------------------------

    @Test
    fun previewIsOfferedForEveryKindThePreviewPanelCanRender() {
        assertTrue(isPreviewable(FileKind.Kotlin, "MainActivity.kt"))
        assertTrue(isPreviewable(FileKind.Html, "index.html"))
        assertTrue(isPreviewable(FileKind.Json, "data.json"))
        assertTrue(isPreviewable(FileKind.Xml, "AndroidManifest.xml"))
        assertTrue(isPreviewable(FileKind.Csv, "rows.csv"))
        assertTrue(isPreviewable(FileKind.Markdown, "README.md"))
        // Images are recognised by extension, because the decoder never sees the FileKind first.
        assertTrue(isPreviewable(null, "screenshot.png"))
        assertTrue(isPreviewable(FileKind.Unknown, "photo.webp"))
    }

    @Test
    fun previewIsNeverOfferedForThingsItCannotDraw() {
        listOf(
            FileKind.Gradle to "build.gradle",
            FileKind.Properties to "gradle.properties",
            FileKind.Unknown to "LICENSE",
            FileKind.Folder to "src",
            // The panel renders a whole web project from its entry HTML, never a loose stylesheet
            // or script — offering them only ever produced plain text.
            FileKind.Css to "app.css",
            FileKind.Js to "app.js",
        ).forEach { (kind, name) ->
            assertFalse("preview must not be offered for $name", isPreviewable(kind, name))
        }
    }

    // -- find in project ----------------------------------------------------------------

    @Test
    fun findInSourcesMatchesCaseInsensitivelyAndKeepsLineNumbers() {
        val files = listOf(
            file("MainActivity.kt", "src/MainActivity.kt") to "fun onCreate() {\n  setContentView()\n}",
            file("Theme.kt", "src/Theme.kt") to "val panel = 1",
        )
        val hits = findInSources("setcontentview", files)
        assertEquals(1, hits.size)
        assertEquals("MainActivity.kt", hits[0].fileName)
        assertEquals(2, hits[0].line)
        assertEquals("setContentView()", hits[0].text)
    }

    @Test
    fun findInSourcesStopsAtTheLimitSoOneLetterQueriesStillAnswer() {
        val many = (1..(MAX_SEARCH_HITS + 50)).joinToString("\n") { "line $it todo" }
        val hits = findInSources("todo", listOf(file("A.kt", "a/A.kt") to many))
        assertEquals(MAX_SEARCH_HITS, hits.size)
    }

    @Test
    fun findInSourcesReturnsNothingForABlankQuery() {
        assertTrue(findInSources("   ", listOf(file("A.kt", "a/A.kt") to "hello")).isEmpty())
    }

    @Test
    fun findInSourcesWholeWordSkipsLongerWords() {
        val sources = listOf(file("A.kt", "a/A.kt") to "val name = 1\nval names = 2\nrename()")
        assertEquals(1, findInSources("name", sources, wholeWord = true).size)
        assertEquals(3, findInSources("name", sources).size)
        // A needle with no word characters still matches: there is no word to bound it.
        assertEquals(1, findInSources("= 1", sources, wholeWord = true).size)
    }

    private fun file(name: String, path: String) =
        com.closecode.ui.model.ProjectFile(
            name = name,
            kind = FileKind.Kotlin,
            language = "Kotlin",
            depth = 0,
            path = path,
        )

    // -- Spec 6.8 tool row --------------------------------------------------------------

    @Test
    fun theToolRowKeepsTheSpecOrder() {
        assertEquals(
            listOf(
                ToolAction.TAB,
                ToolAction.COMMENT,
                ToolAction.LINE_UP,
                ToolAction.LINE_DOWN,
                ToolAction.COPY,
                ToolAction.PROBLEMS,
                ToolAction.OPEN_BRACE,
                ToolAction.CLOSE_BRACE,
                ToolAction.OPEN_PAREN,
                ToolAction.CLOSE_PAREN,
                ToolAction.SEMICOLON,
                ToolAction.EQUALS,
                ToolAction.QUOTE,
                ToolAction.OPEN_ANGLE,
                ToolAction.CLOSE_ANGLE,
            ),
            ToolRowOrder,
        )
    }

    @Test
    fun everyInsertButtonTypesItsCharacter() {
        assertEquals("{", ToolRowInserts[ToolAction.OPEN_BRACE])
        assertEquals("}", ToolRowInserts[ToolAction.CLOSE_BRACE])
        assertEquals("(", ToolRowInserts[ToolAction.OPEN_PAREN])
        assertEquals(")", ToolRowInserts[ToolAction.CLOSE_PAREN])
        assertEquals(";", ToolRowInserts[ToolAction.SEMICOLON])
        assertEquals("=", ToolRowInserts[ToolAction.EQUALS])
        assertEquals("\"", ToolRowInserts[ToolAction.QUOTE])
        assertEquals("<", ToolRowInserts[ToolAction.OPEN_ANGLE])
        assertEquals(">", ToolRowInserts[ToolAction.CLOSE_ANGLE])
    }

    @Test
    fun nonInsertActionsHaveNoCharacter() {
        for (action in listOf(ToolAction.TAB, ToolAction.COPY, ToolAction.LINE_UP)) {
            assertTrue(action.name, ToolRowInserts[action] == null)
        }
    }

    @Test
    fun theToolRowIsThereWheneverTheKeyboardIs() {
        // The row used to park itself four seconds after the last keystroke, which took tab, brace
        // and comment away in the middle of a thought while the keyboard was still up.
        assertTrue(showToolRow(imeVisible = true, suggestionsOpen = false))
        assertFalse(showToolRow(imeVisible = false, suggestionsOpen = false))
    }

    @Test
    fun theSuggestionPopupOutranksTheToolRow() {
        // A row under a popup only steals taps, so it stands down either way.
        assertFalse(showToolRow(imeVisible = true, suggestionsOpen = true))
        assertFalse(showToolRow(imeVisible = false, suggestionsOpen = true))
    }

    // -- menus ---------------------------------------------------------------------

    @Test
    fun theHeaderOverflowOffersOnlyTheRowsAboutTheOpenFile() {
        // Terminal holds a header button again, so the overflow keeps only the two rows that act
        // on the file that is open.
        assertEquals(listOf("Search and commands", "Format file"), OverflowLabels)
    }

    @Test
    fun theBottomMoreSheetOffersThePanelsAndThePreferences() {
        assertEquals(listOf("Logcat", "Build", "Settings", "Toggle theme"), MoreLabels)
    }

    @Test
    fun theTwoMenusNeverOfferTheSameRow() {
        // The app used to have two menus that overlapped: settings and the theme switch were in
        // both, so one app had two names for the same door and the lists could disagree. Every row
        // now has exactly one home.
        assertEquals(emptySet<String>(), OverflowLabels.intersect(MoreLabels.toSet()))
    }

    @Test
    fun noMenuOffersTheProjectExitsAnymore() {
        // Leaving a project is the back gesture, not a button in one of two overlapping menus.
        assertFalse("New project" in OverflowLabels)
        assertFalse("New project" in MoreLabels)
        assertFalse("Close project" in OverflowLabels)
        assertFalse("Close project" in MoreLabels)
    }

    // -- Spec 7 log cap ----------------------------------------------------------------

    @Test
    fun theLogIsTrimmedBackToOneThousandOnceItOvershoots() {
        val log = ArrayList<LogLine>()
        repeat(1_260) { log.appendCapped(LogLine(LogLevel.INFO, "T", "line $it")) }
        assertEquals(1_000, log.size)
        // The newest lines survive; the oldest are dropped.
        assertEquals("line 1259", log.last().message)
    }

    @Test
    fun theLogNeverExceedsOneThousandOverALongRun() {
        val log = ArrayList<LogLine>()
        repeat(5_000) { log.appendCapped(LogLine(LogLevel.DEBUG, "T", "m$it")) }
        assertTrue(log.size <= 1_000)
        assertEquals("m4999", log.last().message)
    }

    @Test
    fun aLogLineFormatsTheWayTheSpecShowsIt() {
        val line = LogLine(LogLevel.ERROR, "Forge", "boom", time = "10:20:30.400")
        assertEquals("10:20:30.400 E/Forge: boom", line.formatted)
    }

    @Test
    fun theBuildPanelReportsTheCheckItActuallyRuns() {
        // The panel used to name Gradle tasks it never ran, walked on a timer. There is no Gradle
        // daemon inside an Android app, so the list is now the sweep's own two phases — and the test
        // says so, rather than pinning names that describe work nobody does.
        assertEquals(listOf("Read project symbols", "Analyse 12 files"), buildStepLabels(12))
        assertEquals(listOf("Read project symbols", "Analyse 1 file"), buildStepLabels(1))
        assertEquals(listOf("Read project symbols", "No Kotlin files"), buildStepLabels(0))
    }

    // -- Spec item 5, image downscaling -------------------------------------------------

    @Test
    fun aSmallImageIsNotDownscaled() {
        assertEquals(1, sampleSizeFor(800, 600))
    }

    @Test
    fun anImageExactlyAtTheLimitIsNotDownscaled() {
        assertEquals(1, sampleSizeFor(2048, 1365))
    }

    @Test
    fun aLargeImageIsHalvedUntilItFits() {
        assertEquals(2, sampleSizeFor(4096, 3072))
        assertEquals(4, sampleSizeFor(8192, 6144))
        assertEquals(8, sampleSizeFor(16384, 12288))
    }

    @Test
    fun theLongestEdgeIsWhatDecidesTheSample() {
        // Very wide but short images still need shrinking.
        assertEquals(4, sampleSizeFor(8192, 100))
    }

    @Test
    fun anUnknownImageSizeDoesNotLoopForever() {
        assertEquals(1, sampleSizeFor(0, 0))
        assertEquals(1, sampleSizeFor(-5, -5))
    }

    // -- Spec item 4, preview formatters ------------------------------------------------

    @Test
    fun jsonIsIndentedWithoutReorderingKeys() {
        val out = prettyFormat("""{"b":1,"a":[1,2],"c":{}}""", isJson = true)
        assertEquals(
            """
            {
              "b": 1,
              "a": [
                1,
                2
              ],
              "c": {}
            }
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun anEmptyJsonObjectStaysOnOneLine() {
        assertEquals("{}", prettyFormat("{}", isJson = true))
        assertEquals("[]", prettyFormat("[]", isJson = true))
    }

    @Test
    fun bracesInsideJsonStringsDoNotChangeTheIndentation() {
        val out = prettyFormat("""{"s":"a}b","t":"c{d"}""", isJson = true)
        assertTrue(out.contains("\"a}b\""))
        assertTrue(out.contains("\"c{d\""))
    }

    @Test
    fun xmlIsIndentedByItsTags() {
        val out = prettyFormat("<a><b>text</b></a>", isJson = false)
        assertTrue(out, out.contains("<a>"))
        assertTrue(out, out.contains("</a>"))
        // The child sits on its own line, one level deeper.
        assertTrue(out, out.lines().any { it.trimStart().startsWith("<b>") })
    }

    @Test
    fun markdownHeadingsKeepTheirLevel() {
        val blocks = parseMarkdown("# One\n\n## Two\n\nbody text")
        val headings = blocks.filterIsInstance<MarkdownBlock.Heading>()
        assertEquals(listOf(1, 2), headings.map { it.level })
        assertEquals("One", headings[0].text)
        assertEquals("Two", headings[1].text)
    }

    @Test
    fun markdownFencedCodeIsCapturedWhole() {
        val blocks = parseMarkdown("before\n```kotlin\nval a = 1\nval b = 2\n```\nafter")
        val code = blocks.filterIsInstance<MarkdownBlock.Code>().single()
        assertTrue(code.text.contains("val a = 1"))
        assertTrue(code.text.contains("val b = 2"))
    }

    @Test
    fun markdownBulletsBecomeBulletBlocks() {
        val bullets = parseMarkdown("- one\n* two\n+ three").filterIsInstance<MarkdownBlock.Bullet>()
        assertEquals(listOf("one", "two", "three"), bullets.map { it.text })
    }

    @Test
    fun markdownRulesBecomeRuleBlocks() {
        assertTrue(parseMarkdown("a\n\n---\n\nb").any { it is MarkdownBlock.Rule })
    }

    @Test
    fun csvQuotedCellsWithCommasStayInOneField() {
        val rows = parseCsv("""a,b,c""" + "\n" + """"x,y",z,3""")
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("x,y", "z", "3"), rows[1])
    }

    @Test
    fun csvHandlesDoubledQuotesInsideQuotedCells() {
        val rows = parseCsv("\"say \"\"hi\"\"\",b")
        assertEquals("say \"hi\"", rows[0][0])
    }

    @Test
    fun emptyCsvRowsAreDropped() {
        assertEquals(1, parseCsv("\n\na,b\n\n").size)
    }

    // -- Spec 5 palette ----------------------------------------------------------------

    @Test
    fun anEmptyPaletteQueryKeepsTheDeclaredOrder() {
        val commands = listOf(
            PaletteCommand("Save file", "Ctrl S") {},
            PaletteCommand("Show logcat", null) {},
        )
        assertEquals(commands, paletteMatch(commands, ""))
    }

    @Test
    fun thePaletteMatchesSubsequencesNotJustPrefixes() {
        val commands = listOf(
            PaletteCommand("Open MainActivity", null) {},
            PaletteCommand("Save file", null) {},
        )
        assertEquals(1, paletteMatch(commands, "oma").size)
        assertEquals(0, paletteMatch(commands, "zzz").size)
    }

    @Test
    fun thePaletteRejectsCharactersThatAreOutOfOrder() {
        val commands = listOf(PaletteCommand("abcdef", null) {})
        assertEquals(1, paletteMatch(commands, "abc").size)
        assertEquals(0, paletteMatch(commands, "cba").size)
    }

    // -- Spec 3 typography -------------------------------------------------------------

    @Test
    fun theCodeLineHeightIsTheFixedTwentyTheSpecDemands() {
        assertEquals(20, CodeLineHeight.value.toInt())
    }

    @Test
    fun theGutterHoldsTheMarksAtItsLeftEdge() {
        // 56 dp, five fixed slots from the spec: git marker 0–3, error bar 4–6, bulb tile 6–24,
        // numbers ending at 38, fold arrow 41–53. Nothing touching, nothing shifting.
        assertEquals(56, GutterWidth.value.toInt())
        assertEquals(3, GutterGitWidth.value.toInt())
        assertEquals(2, GutterBarWidth.value.toInt())
        assertEquals(4, GutterBarLeft.value.toInt())
        assertEquals(6, GutterBulbLeft.value.toInt())
        assertEquals(18, GutterBulbSize.value.toInt())
        assertEquals(14, GutterBulbIcon.value.toInt())
        // The tile is smaller than a finger, so the tap area is 36 by 20 centred on the slot.
        assertEquals(36, GutterBulbTapWidth.value.toInt())
        assertEquals(20, GutterBulbTapHeight.value.toInt())
        assertEquals(41, GutterFoldLeft.value.toInt())
        assertEquals(12, GutterFoldSize.value.toInt())
    }

    @Test
    fun theGutterSlotsDoNotOverlap() {
        // Each slot ends where the next begins, in spec order.
        assertTrue(GutterGitWidth.value <= GutterBarLeft.value)
        assertTrue(GutterBarLeft.value + GutterBarWidth.value <= GutterBulbLeft.value)
        assertTrue(GutterBulbLeft.value + GutterBulbSize.value <= 38)
        assertTrue(GutterFoldLeft.value + GutterFoldSize.value <= GutterWidth.value)
    }

    @Test
    fun theGutterBarAndBulbDoNotReachTheLineNumber() {
        val numberWidth = GutterNumberPadding.value
        // The widest number the spec allows is four digits. At the editor's 11 sp monospace that is
        // about 26.4 dp, so a number can start no further left than this.
        val widestNumber = numberWidth + 4 * 6.6
        val bulbRight = GutterBulbLeft.value + GutterBulbSize.value
        assertTrue(
            "the bulb ends at $bulbRight dp but a four-digit number starts at $widestNumber dp",
            bulbRight <= widestNumber,
        )
        assertTrue(GutterBarLeft.value + GutterBarWidth.value <= GutterBulbLeft.value)
    }

    @Test
    fun theNumberPaddingLeavesRoomForTheFoldChevron() {
        // The number's right edge is GutterWidth - padding; the chevron sits at the far right. They
        // only meet if the padding is smaller than the chevron's own slot.
        assertTrue(GutterNumberPadding.value < GutterWidth.value - 8)
        assertEquals(18, GutterNumberPadding.value.toInt())
    }

    @Test
    fun theCodeColumnStartsExactlyAtTheGutterEdge() {
        // No gap and no divider: the code starts at x = 56 dp.
        assertEquals(0, CodeLeftInset.value.toInt())
        assertEquals(56, CodeColumnStart.value.toInt())
        assertEquals(GutterWidth, CodeColumnStart)
    }

    // -- Spec 4.1 header -----------------------------------------------------------------

    @Test
    fun theHeaderHoldsExactlyTheSpecItemsInOrder() {
        // The bar shows the project name and eight items, nothing else: the logo tile is gone, and
        // the terminal is back between Save and Run. Asserted as an exact list, so adding any
        // button back fails here.
        assertEquals(
            listOf(
                HeaderItem.MENU,
                HeaderItem.PROJECT_NAME,
                HeaderItem.UNDO,
                HeaderItem.REDO,
                HeaderItem.SAVE,
                HeaderItem.TERMINAL,
                HeaderItem.RUN,
                HeaderItem.OVERFLOW,
            ),
            HeaderItems,
        )
    }

    @Test
    fun theHeaderHasNoFormatButton() {
        // No Format button in the header. Format lives in the command palette, the overflow menu
        // and the selection toolbar instead. Asserted as an exact list, so adding any button back
        // fails here.
        assertEquals(8, HeaderItems.size)
        assertEquals(HeaderItems.distinct(), HeaderItems)
    }

    @Test
    fun theTerminalHoldsAHeaderButtonAgain() {
        // Terminal is back in the top bar between Save and Run, so it leaves the overflow alone.
        assertTrue(HeaderItems.contains(HeaderItem.TERMINAL))
        assertTrue("Terminal" !in OverflowLabels)
        assertTrue("Terminal" in ToolPanels)
    }

    @Test
    fun searchIsStillReachableWithoutTheHeaderButton() {
        // The header button became the terminal, so search has to be somewhere else: the bottom
        // nav carries it, and the overflow's "Search and commands" opens the palette.
        assertTrue("Search" in com.closecode.ui.ide.model.IdeNavItems)
        assertTrue("Search and commands" in OverflowLabels)
    }

    @Test
    fun theTabStripHasABoundedHeight() {
        // A Row inside a Column hands its children the height that is still free, so without a
        // stated strip height an unbounded child would stretch this strip over the whole editor:
        // the code area collapsed and the tabs floated in the middle of the screen.
        assertEquals(34, TAB_STRIP_HEIGHT.value.toInt())
        assertEquals(34, TAB_HEIGHT.value.toInt())
        assertEquals(36, TAB_EDGE_FADE_WIDTH.value.toInt())
    }


    @Test
    fun fileKindsThatGetTheirOwnIconTileAreDistinct() {
        // Kotlin and XML are the two the spec gives a dedicated tile to.
        assertTrue(FileKind.Kotlin != FileKind.Xml)
        assertTrue(FileKind.Kotlin != FileKind.Folder)
    }

    // -- path bar ---------------------------------------------------------------------------

    @Test
    fun aPathThatFitsTheBarIsShownInFullBecauseThatIsThePointOfABreadcrumb() {
        assertEquals(
            listOf("app", "build.gradle.kts"),
            breadcrumbOf("app/build.gradle.kts"),
        )
        assertEquals(
            listOf("src", "main", "AndroidManifest.xml"),
            breadcrumbOf("src/main/AndroidManifest.xml"),
        )
    }

    @Test
    fun aFileAtTheProjectRootShowsOnlyItsOwnName() {
        assertEquals(listOf("README.md"), breadcrumbOf("README.md"))
    }

    @Test
    fun aPathTooLongToReadIsShortenedFromTheLeftSoThePackageSurvives() {
        val crumbs = breadcrumbOf("app/src/main/java/com/example/myapp/MainActivity.kt")
        assertEquals("MainActivity.kt", crumbs.last())
        assertEquals("myapp", crumbs[crumbs.size - 2])
        assertEquals("…", crumbs.first())
        assertTrue(crumbs.size <= 3)
        // Shortening never invents or drops the file itself.
        assertTrue(crumbs.contains("MainActivity.kt"))
    }

    @Test
    fun aPathThatIsOnlyWhitespaceOrSeparatorsHasNoCrumbs() {
        assertEquals(emptyList<String>(), breadcrumbOf(""))
        assertEquals(emptyList<String>(), breadcrumbOf("   "))
        assertEquals(emptyList<String>(), breadcrumbOf("///"))
    }

    @Test
    fun theTruncationLimitIsHonouredWhateverThePathLooksLike() {
        val deep = (1..20).joinToString("/") { "part$it" } + "/File.kt"
        val crumbs = breadcrumbOf(deep, maxCrumbs = 4)
        assertEquals(4, crumbs.size)
        assertEquals("File.kt", crumbs.last())
        assertEquals("…", crumbs.first())
    }
}

class RetainTabsTest {

    private fun tab(name: String) =
        ProjectFile(name = name, kind = FileKind.Kotlin, language = "Kotlin", depth = 0)

    private val nothingDirty: (String) -> Boolean = { false }

    @Test
    fun aStripUnderTheCapIsLeftAlone() {
        val tabs = listOf(tab("a"), tab("b"))
        assertEquals(tabs, retainTabs(tabs, active = "a", isDirty = nothingDirty))
    }

    @Test
    fun openingAFourthFileDropsTheOldestOneThatIsNeitherActiveNorDirty() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "d", isDirty = nothingDirty)
        assertEquals(listOf("b", "c", "d"), kept.map { it.name })
    }

    @Test
    fun theActiveTabIsNeverDroppedEvenWhenItIsTheOldest() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "a", isDirty = nothingDirty)
        assertEquals(listOf("a", "c", "d"), kept.map { it.name })
    }

    @Test
    fun unsavedWorkIsNeverDropped() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "d", isDirty = { it == "a" })
        assertEquals(listOf("a", "c", "d"), kept.map { it.name })
    }

    @Test
    fun aStripOfUnsavedTabsKeepsThemAllRatherThanLosingWork() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val kept = retainTabs(tabs, active = "d", isDirty = { true })
        assertEquals(4, kept.size)
    }

    @Test
    fun twoTabsWithTheSameNameAreTwoTabs() {
        // Identity is the path: opening src/index.js must not merge into, close, or evict
        // an open index.js from another folder — that duplicate key crashed the strip.
        val tabs = listOf(
            ProjectFile(name = "index.js", kind = FileKind.Js, language = "Js", depth = 1, path = "a/index.js"),
            ProjectFile(name = "index.js", kind = FileKind.Js, language = "Js", depth = 1, path = "b/index.js"),
            tab("c"),
            tab("d"),
        )
        val kept = retainTabs(tabs, active = "b/index.js", isDirty = nothingDirty)
        assertEquals(listOf("b/index.js", "c", "d"), kept.map { it.path })
    }
}