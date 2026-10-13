package com.closecode.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The two measurements the storage section shows.
 *
 * A size row is compared against other rows on the same screen, so it must not change shape with
 * the device's locale, and a path that is not there must report nothing rather than blow up the
 * settings screen — the number is the least important thing on that row.
 */
class SettingsMeasureTest {

    @Test
    fun aSizeIsFormattedInTheUnitItsRowShows() {
        assertEquals("0 B", humanSize(0))
        assertEquals("1023 B", humanSize(1023))
        assertEquals("1 KB", humanSize(1024))
        assertEquals("1.0 MB", humanSize(1024L * 1024))
        assertEquals("1.5 MB", humanSize(1024L * 1024 + 512L * 1024))
        assertEquals("1.0 GB", humanSize(1024L * 1024 * 1024))
    }

    @Test
    fun aSizeNeverPicksUpTheDevicesDecimalSeparator() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5 MB", humanSize(1024L * 1024 + 512L * 1024))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun theSizeOfATreeAddsUpEveryFileUnderIt() {
        val root = File.createTempFile("closecode", "projects").apply {
            delete()
            mkdirs()
        }
        try {
            File(root, "top.txt").writeBytes(ByteArray(100))
            val nested = File(root, "nested").apply { mkdirs() }
            File(nested, "inner.txt").writeBytes(ByteArray(50))

            assertEquals(150L, dirSize(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aPlainFileMeasuresAsItsOwnLength() {
        val file = File.createTempFile("closecode", ".kt")
        try {
            file.writeBytes(ByteArray(42))
            assertEquals(42L, dirSize(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun somethingThatIsNotThereMeasuresAsZeroRatherThanThrowing() {
        assertEquals(0L, dirSize(File("/closecode/definitely/not/here")))
    }
}
