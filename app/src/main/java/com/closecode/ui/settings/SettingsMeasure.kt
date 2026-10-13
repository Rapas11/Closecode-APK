package com.closecode.ui.settings

import java.io.File
import java.util.Locale

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

/**
 * Bytes in the form a storage screen shows them.
 *
 * Always Locale.US: a size is a number, not prose, and "1,4 GB" beside "1.4 GB" in the same app
 * would look like a bug rather than a translation.
 */
fun humanSize(bytes: Long): String = when {
    bytes < KB -> "$bytes B"
    bytes < MB -> String.format(Locale.US, "%.0f", bytes.toDouble() / KB) + " KB"
    bytes < GB -> String.format(Locale.US, "%.1f", bytes.toDouble() / MB) + " MB"
    else -> String.format(Locale.US, "%.1f", bytes.toDouble() / GB) + " GB"
}

/**
 * The size of a directory and everything under it.
 *
 * Returns 0 for anything that is not there rather than throwing: this is a number shown on a
 * settings row, and a row that reports nothing is better than a row that crashes the screen.
 * Symlinked directories are not followed, so a cycle cannot hang the walk.
 */
fun dirSize(root: File): Long {
    if (!root.exists()) return 0L
    if (root.isFile) return root.length()
    var total = 0L
    val children = root.listFiles() ?: return 0L
    for (child in children) {
        if (child.isDirectory) {
            total += dirSize(child)
        } else {
            total += child.length()
        }
    }
    return total
}
