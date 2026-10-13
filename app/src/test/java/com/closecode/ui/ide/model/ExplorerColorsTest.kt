package com.closecode.ui.ide.model

import com.closecode.ui.components.StatusKind
import com.closecode.ui.git.GitChangeKind
import com.closecode.ui.model.FileKind
import com.closecode.ui.theme.DarkColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the app screens spec's file-icon colors and Git letters: the color carries the file
 * type, and the tree's letter matches the change kind by name.
 */
class ExplorerColorsTest {

    @Test
    fun iconColorsMatchTheSpecTable() {
        val c = DarkColors
        assertEquals(c.codeFunction, kindColor(FileKind.Folder, c))
        assertEquals(c.codeKeyword, kindColor(FileKind.Kotlin, c))
        assertEquals(c.htmlIcon, kindColor(FileKind.Html, c))
        assertEquals(c.accent, kindColor(FileKind.Css, c))
        assertEquals(c.codeType, kindColor(FileKind.Js, c))
        assertEquals(c.iconTint, kindColor(FileKind.Markdown, c))
        assertEquals(c.popupMuted, kindColor(FileKind.Unknown, c))
    }

    @Test
    fun changeKindsMapToStatusKindsByName() {
        assertEquals(StatusKind.Added, statusKindOf(GitChangeKind.Added))
        assertEquals(StatusKind.Modified, statusKindOf(GitChangeKind.Modified))
        assertEquals(StatusKind.Deleted, statusKindOf(GitChangeKind.Deleted))
    }
}
