package com.closecode.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ClosecodeTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    forceDark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val useDark = forceDark ?: darkTheme
    val colors = if (useDark) DarkColors else LightColors

    CompositionLocalProvider(
        LocalPageColors provides colors,
        // Compose tints the text cursor's drag handle and the selection with these. Left at their
        // defaults the handle came out in Material's own purple, a colour that appears nowhere in
        // this palette, which is what put a stray lavender shape in the middle of the code.
        LocalTextSelectionColors provides TextSelectionColors(
            backgroundColor = colors.textSelection,
            handleColor = colors.accent,
        ),
    ) {
        MaterialTheme(
            // primary and the surfaces are mapped from the spec's own tokens. primary was
            // previously never set, so every Material-drawn affordance fell back to its default.
            colorScheme = if (useDark) {
                androidx.compose.material3.darkColorScheme(
                    primary = colors.ac,
                    onPrimary = colors.af,
                    primaryContainer = colors.selection,
                    onPrimaryContainer = colors.tx,
                    background = colors.bg,
                    surface = colors.cd,
                    onBackground = colors.tx,
                    onSurface = colors.tx,
                    surfaceVariant = colors.panel2,
                    outline = colors.ln,
                )
            } else {
                androidx.compose.material3.lightColorScheme(
                    primary = colors.ac,
                    onPrimary = colors.af,
                    primaryContainer = colors.selection,
                    onPrimaryContainer = colors.tx,
                    background = colors.bg,
                    surface = colors.cd,
                    onBackground = colors.tx,
                    onSurface = colors.tx,
                    surfaceVariant = colors.panel2,
                    outline = colors.ln,
                )
            },
            typography = AppTypography,
            content = content,
        )
    }
}