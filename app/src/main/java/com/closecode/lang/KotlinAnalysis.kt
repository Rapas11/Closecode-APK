package com.closecode.lang

class KotlinFileAnalysis(
    val source: String,
    val lex: LexResult,
    val symbols: SymbolTable,
    val diagnostics: List<Diagnostic>,
    private val completion: KotlinCompletion,
    /** The Android API index this analysis resolved against, for quick fixes. */
    val api: AndroidApiIndex = AndroidApiIndex.EMPTY,
) {
    val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.ERROR }
    val warnings: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.WARNING }

    fun diagnosticsOnLine(line: Int): List<Diagnostic> = diagnostics.filter { it.line == line }

    /**
     * The editor analyses asynchronously, so a caller can hand us a caret offset taken from text
     * that is newer than this analysis. Every offset-based entry point clamps here so a stale
     * result can never throw.
     */
    private fun safe(offset: Int): Int = offset.coerceIn(0, source.length)

    fun completionsAt(offset: Int, limit: Int = 40): List<CompletionItem> =
        completion.complete(safe(offset), limit)

    fun prefixAt(offset: Int): String = completion.prefixAt(safe(offset))

    fun isMemberAccess(offset: Int): Boolean = completion.isMemberAccessAt(safe(offset))
}

object KotlinAnalyzer {
    /**
     * Every name declared anywhere in the project, gathered once and reused for each file.
     *
     * This only has to exist so that a call into a sibling file is not reported as an error, so it
     * is intentionally permissive: any declared name counts, wherever it was declared.
     */
    fun projectSymbols(sources: Collection<String>): Set<String> {
        if (sources.size <= 1) return declaredNames(sources.firstOrNull().orEmpty())
        val out = HashSet<String>()
        for (text in sources) out.addAll(declaredNames(text))
        return out
    }

    private fun declaredNames(source: String): Set<String> {
        if (source.isBlank()) return emptySet()
        return runCatching {
            SymbolTable.build(source, KotlinLexer.lex(source)).allSymbols().mapTo(HashSet()) { it.name }
        }.getOrDefault(emptySet())
    }

    fun analyze(
        source: String,
        api: AndroidApiIndex = AndroidApiIndex.EMPTY,
        projectSymbols: Set<String> = emptySet(),
    ): KotlinFileAnalysis {
        val lex = KotlinLexer.lex(source)
        val symbols = SymbolTable.build(source, lex)
        val diagnostics = KotlinDiagnostics(source, lex, symbols, api, projectSymbols).compute()
        val completion = KotlinCompletion(source, lex, symbols, api)
        return KotlinFileAnalysis(source, lex, symbols, diagnostics, completion, api)
    }
}