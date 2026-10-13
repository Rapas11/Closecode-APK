package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Build panel claims, and when.
 *
 * The panel replaced a list of Gradle task names that it never ran, so the thing worth pinning is
 * that every row it draws now describes work that actually happened. All of it is pure, so it is
 * checked here rather than by running a build on a phone.
 */
class BuildPanelTest {

    // -- The steps ------------------------------------------------------------

    @Test
    fun theStepsAreTheSweepsOwnTwoPhases() {
        assertEquals(listOf("Read project symbols", "Analyse 7 files"), buildStepLabels(7))
    }

    @Test
    fun oneFileIsNotSeven() {
        assertEquals("Analyse 1 file", buildStepLabels(1)[1])
        assertEquals("Analyse 2 files", buildStepLabels(2)[1])
    }

    @Test
    fun aProjectWithNoKotlinSaysSoRatherThanCountingZeroFiles() {
        assertEquals(listOf("Read project symbols", "No Kotlin files"), buildStepLabels(0))
    }

    @Test
    fun nothingHasRunBeforeTheButtonIsPressed() {
        assertEquals(listOf(StepState.PENDING, StepState.PENDING), buildStepStates(BuildPhase.IDLE))
    }

    @Test
    fun readingSymbolsComesBeforeAnalysingFiles() {
        assertEquals(listOf(StepState.RUNNING, StepState.PENDING), buildStepStates(BuildPhase.SYMBOLS))
        assertEquals(listOf(StepState.DONE, StepState.RUNNING), buildStepStates(BuildPhase.FILES))
    }

    @Test
    fun aFinishedBuildHasFinishedEveryStep() {
        assertEquals(listOf(StepState.DONE, StepState.DONE), buildStepStates(BuildPhase.DONE))
    }

    @Test
    fun aStepNeverRunsAfterALaterOneHasStarted() {
        // Reading the project's symbols is one quick pass and analysing the files is the long haul, so
        // the second step must not begin until the first has finished. Dividing the bar into equal
        // shares got this backwards, which is what made the list read as nonsense on a real project.
        for (phase in BuildPhase.entries) {
            val states = buildStepStates(phase)
            val analysingStarted = states[1] != StepState.PENDING
            assertTrue("$phase: $states", !analysingStarted || states[0] == StepState.DONE)
        }
    }

    // -- The result line ------------------------------------------------------

    @Test
    fun aCleanBuildSaysSoAndSaysHowLongItTook() {
        assertEquals("BUILD SUCCESSFUL · 0 errors in 412 ms", buildResultLine(0, 0, 412))
    }

    @Test
    fun aFailedBuildReportsBothCountsAndPointsAtTheFix() {
        val line = buildResultLine(3, 1, 900)
        assertTrue(line, line.startsWith("BUILD FAILED · 3 errors, 1 warning"))
        assertTrue(line, line.contains("marked lines"))
    }

    @Test
    fun aCleanBuildWithWarningsStillSaysSuccessful() {
        val line = buildResultLine(0, 2, 120)
        assertTrue(line, line.startsWith("BUILD SUCCESSFUL · 2 warnings"))
    }

    @Test
    fun oneIsSpelledSingular() {
        assertTrue(buildResultLine(0, 1, 10).contains("1 warning"))
        assertTrue(buildResultLine(1, 0, 10).contains("1 error,"))
        assertTrue(buildResultLine(2, 0, 10).contains("2 errors,"))
    }

    @Test
    fun aSubSecondBuildIsNeverReportedAsZero() {
        assertEquals("0 ms", formatBuildTime(0))
        assertEquals("1 ms", formatBuildTime(1))
        assertEquals("999 ms", formatBuildTime(999))
    }

    @Test
    fun aLongBuildIsReportedInSeconds() {
        assertEquals("1.0 s", formatBuildTime(1_000))
        assertEquals("2.5 s", formatBuildTime(2_540))
    }

    @Test
    fun timesAreFormattedTheSameWayEverywhere() {
        // Locale.US on purpose: a size or a duration is a number, and "1,2 s" beside "1.2 s" in the
        // same app would look like a bug rather than a translation.
        assertEquals("1.2 s", formatBuildTime(1_234))
    }

    // -- App identity ----------------------------------------------------------

    @Test
    fun thePackageComesFromTheManifestAttribute() {
        assertEquals(
            "com.closecode.blankapp",
            manifestPackageOf("<manifest package=\"com.closecode.blankapp\"></manifest>"),
        )
        assertEquals(
            "com.example.app",
            manifestPackageOf("<manifest\n    package='com.example.app'\n>"),
        )
    }

    @Test
    fun noManifestMeansNoPackage() {
        assertNull(manifestPackageOf(null))
        assertNull(manifestPackageOf(""))
        assertNull(manifestPackageOf("<manifest></manifest>"))
    }

    @Test
    fun theVersionComesFromTheGradleFile() {
        assertEquals("1.0", gradleVersionOf("versionName \"1.0\""))
        assertEquals("2.3-beta", gradleVersionOf("    versionName='2.3-beta'\n"))
    }

    @Test
    fun noVersionMeansNoVersion() {
        assertNull(gradleVersionOf(null))
        assertNull(gradleVersionOf("applicationId \"com.x\""))
    }

}
