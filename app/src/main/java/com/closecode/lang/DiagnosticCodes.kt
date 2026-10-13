package com.closecode.lang

/**
 * Forge spec section 6.5 diagnostic codes.
 *
 * The spec asks for these identifiers to stay stable and to be extended "in the same format"
 * (E = error, W = warning), so new rules join here rather than inventing literals at call sites.
 * The UI switches on these values for pill colors, the code chip in the problem card and the
 * quick-fix menu, so renaming one is a visible behaviour change.
 */
object DiagnosticCodes {
    const val E0101_UNRESOLVED = "E0101"
    const val E0102_MISSING_SEMICOLON = "E0102"
    const val E0103_UNCLOSED = "E0103"
    const val E0104_UNEXPECTED_CLOSE = "E0104"
    const val E0105_UNTERMINATED = "E0105"

    /** Added in the spec's own format: inferred type does not match the declaration. */
    const val E0106_TYPE_MISMATCH = "E0106"

    /** Added in the spec's own format: the same name declared twice in one scope. */
    const val E0107_DUPLICATE = "E0107"

    const val W0201_UNUSED_IMPORT = "W0201"
    const val W0202_TODO = "W0202"
    const val W0203_UNUSED_VARIABLE = "W0203"

    /** An element left open, or a closing tag with no matching opener. */
    const val HTML_UNCLOSED_TAG = "E0201"

    /** True when [code] is an error code. Drives pill, gutter dot and underline colour. */
    fun isError(code: String): Boolean = code.startsWith("E")

    fun isWarning(code: String): Boolean = code.startsWith("W")
}