package com.closecode.lang

enum class Severity { ERROR, WARNING }

data class Diagnostic(
    val severity: Severity,
    val message: String,
    val offset: Int,
    val length: Int,
    val line: Int,
    val column: Int,
    val code: String,
    /**
     * The identifier this diagnostic is about, when structured data is available: the simple
     * name for UNRESOLVED, the fully-qualified name for UNUSED_IMPORT. Quick fixes read this
     * instead of parsing the human-facing message.
     */
    val data: String? = null,
)

class KotlinDiagnostics(
    private val source: String,
    private val lex: LexResult,
    private val symbols: SymbolTable,
    private val api: AndroidApiIndex,
    /**
     * Every name declared anywhere in the project, across all its files.
     *
     * Resolution here is per file, so without this a call to a function declared in a sibling file
     * would be reported as an error. The set is deliberately generous: it exists only to stop false
     * positives, never to clear a real one.
     */
    private val projectSymbols: Set<String> = emptySet(),
) {
    private data class Delimiter(
        val open: TokenKind,
        val close: TokenKind,
        val openText: String,
    )

    private companion object {
        val DELIMITERS = mapOf(
            TokenKind.OPEN_PAREN to Delimiter(TokenKind.OPEN_PAREN, TokenKind.CLOSE_PAREN, "("),
            TokenKind.OPEN_BRACKET to Delimiter(TokenKind.OPEN_BRACKET, TokenKind.CLOSE_BRACKET, "["),
            TokenKind.OPEN_BRACE to Delimiter(TokenKind.OPEN_BRACE, TokenKind.CLOSE_BRACE, "{"),
        )
    }

    private val out = ArrayList<Diagnostic>()
    private val declarationOffsets: Set<Int> = symbols.allSymbols().map { it.offset }.toSet()

    private val callableNames: Set<String> =
        symbols.allSymbols().filter { it.isCallable }.map { it.name }.toSet()

    private val typeParameterNames: Set<String> = symbols.typeParameterNames()

    /**
     * Member names reachable from the classes this file imports, worked out once per file.
     *
     * `api.membersOf` walks a class and its supertypes, so building this per identifier would be
     * far too slow on a large file.
     */
    private val importedMemberNames: Set<String> by lazy {
        val out = HashSet<String>()
        for (entry in symbols.imports) {
            for (member in api.membersOf(entry.simpleName)) out.add(member.name)
        }
        out
    }

    fun compute(): List<Diagnostic> {
        checkLexIssues()
        checkDelimiterBalance()
        checkDuplicateDeclarations()
        checkUnresolvedReferences()
        checkLiteralTypeMismatches()
        checkUnusedImports()
        checkUnusedLocalVariables()
        checkTodoComments()
        return out.sortedWith(compareBy({ it.line }, { it.column }))
    }

    /**
     * Spec 6.5, W0202: a TODO comment. Only the marker word is underlined, not the whole comment,
     * so the wave lines up with the characters a reader actually scans for.
     */
    private fun checkTodoComments() {
        // Comments are trivia, so they are not in lex.code; lex.tokens is the full stream.
        for (line in lex.tokens) {
            if (line.kind != TokenKind.LINE_COMMENT) continue
            val text = line.text
            val at = text.indexOf("TODO")
            if (at < 0) continue
            val rest = text.substring(at + 4).trimStart(':', ' ', '\t')
            val message = if (rest.isEmpty()) "TODO" else "TODO: $rest"
            val token = Token(
                kind = TokenKind.IDENTIFIER,
                text = "TODO",
                start = line.start + at,
                end = line.start + at + 4,
                line = line.line,
                column = line.column + at,
            )
            report(
                severity = Severity.WARNING,
                message = message,
                token = token,
                code = DiagnosticCodes.W0202_TODO,
                length = 4,
            )
        }
    }

    private fun report(
        severity: Severity,
        message: String,
        token: Token,
        code: String,
        length: Int = token.text.length.coerceAtLeast(1),
        data: String? = null,
    ) {
        out.add(Diagnostic(severity, message, token.start, length, token.line, token.column, code, data))
    }

    private fun checkLexIssues() {
        for (issue in lex.issues) {
            out.add(
                Diagnostic(
                    Severity.ERROR,
                    issue.message,
                    issue.offset,
                    1,
                    issue.line,
                    1,
                    DiagnosticCodes.E0105_UNTERMINATED,
                ),
            )
        }
    }

    private fun checkDelimiterBalance() {
        val stack = ArrayDeque<Token>()
        for (t in lex.significant) {
            val opening = DELIMITERS[t.kind]
            if (opening != null) {
                stack.addLast(t)
                continue
            }
            val closing = DELIMITERS.values.firstOrNull { it.close == t.kind } ?: continue
            val top = stack.lastOrNull()
            if (top == null || DELIMITERS[top.kind]?.open != closing.open) {
                report(Severity.ERROR, "Unexpected '${t.text}'", t, DiagnosticCodes.E0104_UNEXPECTED_CLOSE)
            } else {
                stack.removeLast()
            }
        }
        while (stack.isNotEmpty()) {
            val left = stack.removeLast()
            val name = DELIMITERS[left.kind]?.openText ?: "?"
            report(Severity.ERROR, "'$name' is never closed", left, DiagnosticCodes.E0103_UNCLOSED)
        }
    }

    private fun checkDuplicateDeclarations() {
        for (scope in symbols.allScopes) {
            val seen = HashMap<String, Token>()
            for (sym in scope.symbols) {
                val tokenIdx = lex.indexOfTokenAt(sym.offset)
                if (tokenIdx < 0) continue
                val token = lex.code[tokenIdx]
                val previous = seen.put(sym.name, token)
                if (previous != null && !isOverloadable(sym)) {
                    report(
                        Severity.ERROR,
                        "'${sym.name}' is already declared in this scope",
                        token,
                        DiagnosticCodes.E0107_DUPLICATE,
                    )
                }
            }
        }
    }

    private fun isOverloadable(sym: Symbol): Boolean =
        sym.kind == SymbolKind.FUNCTION ||
            sym.kind == SymbolKind.EXTENSION_FUNCTION ||
            sym.kind == SymbolKind.CONSTRUCTOR

    private fun checkUnresolvedReferences() {
        if (symbols.hasWildcardImport) return
        val sig = lex.code
        val used = HashSet<String>()
        var skipUntilNewline = false
        val importEnd = importStatementEnds()

        for ((index, t) in sig.withIndex()) {
            if (t.kind == TokenKind.NEWLINE) {
                skipUntilNewline = false
                continue
            }
            if (skipUntilNewline) continue
            if (t.start in importEnd) continue
            if (t.kind == TokenKind.KEYWORD) {
                if (t.text == "import" || t.text == "package") skipUntilNewline = true
                continue
            }
            if (!t.isIdentifierLike) continue

            used.add(t.text.trim('`'))

            val prev = previousCodeToken(index)
            val next = nextCodeToken(index)

            if (prev != null && (prev.kind == TokenKind.DOT || prev.kind == TokenKind.AT)) continue
            if (next != null && (next.kind == TokenKind.AT || next.kind == TokenKind.OPERATOR)) continue
            if (prev != null && prev.kind == TokenKind.OPEN_PAREN && next != null &&
                next.kind == TokenKind.OPERATOR && next.text == "="
            ) continue
            if (t.start in declarationOffsets) continue

            val name = t.text.trim('`')
            if (name.isBlank()) continue
            if (name == "_") continue

            // Everything that can prove the name exists settles it, whatever shape the use took.
            if (symbols.imports.any { it.simpleName == name }) continue
            if (symbols.hasVisible(t.start, name)) continue
            if (name in callableNames) continue
            if (typeParameterNames.contains(name)) continue
            // A name declared in a sibling file of the same project. Resolution is per file, so
            // without this every call into another file would be reported as an error.
            if (name in projectSymbols) continue

            // A lower-case name is only reported when nothing could plausibly provide it. These are the
            // cases where something unseen can: a member reached through an implicit receiver, a
            // stdlib extension we have no index of, or a name the file's own context implies. Being
            // wrong here produces an error the user cannot act on, which is worse than silence.
            if (name.first().isLowerCase() && hasUnseenProvider(symbols, t.start, name, next)) continue

            // A class the Android index knows, referenced without an import, is genuinely
            // unresolved: Kotlin requires the import, so report it and let the quick fix add one.
            if (needsAndroidImport(name)) {
                report(
                    Severity.ERROR,
                    "Unresolved reference '$name'",
                    t,
                    DiagnosticCodes.E0101_UNRESOLVED,
                    data = name,
                )
                continue
            }

            // Import-free builtins, and the short list of common framework names, are the only names
            // a missing import cannot be blamed on. The Compose group is deliberately not here:
            // Kotlin requires `import androidx.compose.material3.Text` before `Text("x")` compiles.
            if (KnownNames.needsNoImport(name)) continue
            if (api.hasClass(name)) continue
            // A constructor call can be a member-invoking helper from an imported class.
            if (looksLikeCall(next) && name in importedMemberNames) continue

            if (symbols.shouldSuppressUnresolved(t.start)) continue
            if (isPartOfQualifiedName(sig, index)) continue

            report(
                Severity.ERROR,
                "Unresolved reference '$name'",
                t,
                DiagnosticCodes.E0101_UNRESOLVED,
                data = name,
            )
        }
    }

    /**
     * True when [name] looks like a class the Android SDK provides but the file has not imported.
 * Kotlin's own builtins are excluded: `String` and `Int` exist in `android.jar` only as
 * `java.lang` classes and never need an import, so flagging them would flood every file.
 */
/** Whether the token after an identifier is a call or a trailing lambda rather than a type use. */
private fun looksLikeCall(next: Token?): Boolean =
    next != null && (
        next.kind == TokenKind.OPEN_PAREN ||
            next.kind == TokenKind.OPEN_BRACE ||
            (next.kind == TokenKind.OPERATOR && next.text == "<")
        )

/**
 * Members every data class has whether or not it writes them down.
 *
 * `copy` is the one that matters in practice: `fun withName(n: String) = copy(name = n)` is
 * idiomatic and legal, and without this it reads as a call to something undefined.
 */
private val DATA_CLASS_MEMBERS = setOf("copy", "equals", "hashCode", "toString")

/** Names whose shape or length makes them a `componentN` of some data class. */
private val COMPONENT_FUNCTION = Regex("""^component(\d+)$""")

/**
     * Whether a lower-case name could be provided by something this file does not name.
     *
     * Four situations, each of which was a false error reported against real, idiomatic Kotlin:
     *
     * 1. The enclosing class extends something, so the name may be one of its inherited members.
     *    `ActivityMainBinding.inflate(layoutInflater)` inside an `AppCompatActivity` is the case
     *    that motivated this.
     * 2. The name is a data class's implicit `copy`, or a `componentN`.
     * 3. It is followed by a trailing lambda, which is nearly always a stdlib extension on the
     *    receiver: `maxByOrNull { it.length }`.
     * 4. It sits inside a trailing lambda, where unqualified calls resolve against the lambda's
     *    own receiver: `buildList { add(5) }`.
     *
     * None of these can be decided without type inference. Reporting them would mean an error the
     * user can do nothing about, so they are left alone.
     */
private fun hasUnseenProvider(symbols: SymbolTable, offset: Int, name: String, next: Token?): Boolean {
    if (KnownNames.isKnown(name)) return true
    if (name in DATA_CLASS_MEMBERS) return true
    if (COMPONENT_FUNCTION.matches(name)) return true
    if (next != null && next.kind == TokenKind.OPEN_BRACE) return true
    if (symbols.inClassWithSupertypeAt(offset)) return true
    if (symbols.insideTrailingLambda(offset)) return true
    return false
}

private fun needsAndroidImport(name: String): Boolean =
        name.firstOrNull()?.isUpperCase() == true &&
            name !in KnownNames.kotlinTypes &&
            api.hasClass(name)

private fun isPartOfQualifiedName(sig: List<Token>, index: Int): Boolean {
        val prev = sig.getOrNull(index - 1)
        val next = sig.getOrNull(index + 1)
        if (next != null && next.kind == TokenKind.DOT) return true
        if (prev != null && prev.kind == TokenKind.DOT) {
            val before = sig.getOrNull(index - 2)
            return before != null && before.isIdentifierLike
        }
        return false
    }

    /**
     * Spec 6.5, W0203: a local variable that is written and never read.
 *
     * Deliberately narrow. A declaration is only reported when its name appears exactly once in the
 * whole file's code, which means it is never mentioned again at all, and only when it sits in a
 * function body rather than being a class property, where it may be part of the type's surface. A
 * variable that is read, written elsewhere, or shadowed is left alone.
 *
 * The count is over code tokens only, so a name that appears inside a comment or a string does not
 * count as a use.
 */
private fun KotlinDiagnostics.checkUnusedLocalVariables() {
    val occurrences = HashMap<String, Int>()
    for (token in lex.code) {
        if (!token.isIdentifierLike) continue
        occurrences.merge(token.text.trim('`'), 1, Int::plus)
    }
    if (occurrences.isEmpty()) return

    val sig = lex.code
    for ((index, token) in sig.withIndex()) {
        if (token.kind != TokenKind.KEYWORD) continue
        if (token.text != "val" && token.text != "var") continue
        // Skip `val (a, b) = pair`, where the next token is a bracket rather than a name.
        val name = nextCodeToken(index)?.takeIf { it.isIdentifierLike } ?: continue
        val nameText = name.text.trim('`')
        if (occurrences[nameText] != 1) continue
        // Only function-locals. A property on a class may be read by the framework or by other code.
        val scopeKind = symbols.scopeKindAt(name.start) ?: continue
        if (scopeKind != ScopeKind.FUNCTION && scopeKind != ScopeKind.BLOCK) continue
        report(
            severity = Severity.WARNING,
            message = "Variable '$nameText' is never used",
            token = name,
            code = DiagnosticCodes.W0203_UNUSED_VARIABLE,
            length = nameText.length,
            data = nameText,
        )
    }
}

private fun previousCodeToken(index: Int): Token? {
        var k = index - 1
        while (k >= 0 && lex.code[k].kind == TokenKind.NEWLINE) k--
        return if (k >= 0) lex.code[k] else null
    }

    private fun nextCodeToken(index: Int): Token? {
        var k = index + 1
        while (k < lex.code.size && lex.code[k].kind == TokenKind.NEWLINE) k++
        return if (k < lex.code.size) lex.code[k] else null
    }

    private fun importStatementEnds(): Set<Int> {
        val covered = HashSet<Int>()
        var i = 0
        val all = lex.tokens
        while (i < all.size) {
            if (all[i].kind == TokenKind.KEYWORD && all[i].text == "import") {
                var j = i + 1
                while (j < all.size && all[j].kind != TokenKind.NEWLINE &&
                    all[j].kind != TokenKind.SEMICOLON
                ) {
                    covered.add(all[j].start)
                    j++
                }
                i = j
                continue
            }
            i++
        }
        return covered
    }

    private fun checkLiteralTypeMismatches() {
        val sig = lex.code
        for (scope in symbols.allScopes) {
            for (sym in scope.symbols) {
                val declared = sym.declaredType ?: continue
                val expected = JvmTypes.stripGenerics(declared)
                if (!isComparablePrimitive(expected)) continue
                val declIdx = lex.indexOfTokenAt(sym.offset)
                if (declIdx < 0) continue
                var eqIdx = -1
                for (k in declIdx + 1 until sig.size) {
                    val t = sig[k]
                    val isAssign = t.kind == TokenKind.OPERATOR && t.text == "="
                    if (isAssign || t.kind == TokenKind.SEMICOLON || t.kind == TokenKind.NEWLINE ||
                        t.kind == TokenKind.CLOSE_PAREN
                    ) {
                        eqIdx = k
                        break
                    }
                }
                if (eqIdx < 0) continue
                val eq = sig[eqIdx]
                if (!(eq.kind == TokenKind.OPERATOR && eq.text == "=")) continue
                val value = sig.getOrNull(eqIdx + 1) ?: continue
                val actual = literalKind(value) ?: continue
                if (!actual.isCompatibleWith(expected)) {
                    report(
                        Severity.ERROR,
                        "Type mismatch: expected '$expected', but found ${actual.describe()}",
                        value,
                        DiagnosticCodes.E0106_TYPE_MISMATCH,
                    )
                }
            }
        }
    }

    private fun isComparablePrimitive(type: String): Boolean =
        type in setOf("Int", "Long", "Short", "Byte", "Char", "Boolean", "Float", "Double", "String")

    private fun literalKind(t: Token): LiteralKind? = when {
        t.kind == TokenKind.STRING -> LiteralKind.STRING
        t.kind == TokenKind.CHARACTER -> LiteralKind.CHAR
        t.kind == TokenKind.NUMBER -> LiteralKind.NUMBER
        t.kind == TokenKind.KEYWORD && t.text == "true" -> LiteralKind.BOOLEAN
        t.kind == TokenKind.KEYWORD && t.text == "false" -> LiteralKind.BOOLEAN
        t.kind == TokenKind.IDENTIFIER && t.text.trim('`') in setOf("null") -> LiteralKind.NULL
        else -> null
    }

    private enum class LiteralKind(val describe: () -> String) {
        STRING({ "a String" }),
        CHAR({ "a Char" }),
        NUMBER({ "a number" }),
        BOOLEAN({ "a Boolean" }),
        NULL({ "null" });

        fun isCompatibleWith(expected: String): Boolean = when (this) {
            NULL -> true
            NUMBER -> expected in setOf("Int", "Long", "Short", "Byte", "Float", "Double")
            CHAR -> expected == "Char"
            BOOLEAN -> expected == "Boolean"
            STRING -> expected == "String"
        }
    }

    private fun checkUnusedImports() {
        val covered = importStatementEnds()
        val usedNames = HashSet<String>()
        for (t in lex.code) {
            if (t.start in covered) continue
            if (t.isIdentifierLike) {
                usedNames.add(t.text.trim('`'))
                continue
            }
            if (t.kind == TokenKind.ANNOTATION) {
                val name = t.text.removePrefix("@").substringBefore(':').trim()
                if (name.isNotBlank()) usedNames.add(name)
            }
        }
        for (imp in symbols.imports) {
            if (imp.isWildcard) continue
            if (imp.simpleName.isBlank() || imp.simpleName == "*") continue
            if (imp.simpleName in usedNames) continue
            val anchorIdx = lex.indexOfTokenAt(imp.offset)
            val anchor = if (anchorIdx >= 0) lex.code[anchorIdx] else null
            out.add(
                Diagnostic(
                    Severity.WARNING,
                    "Unused import '${imp.fqn}'",
                    imp.offset,
                    imp.fqn.length.coerceAtLeast(1),
                    imp.line,
                    anchor?.column ?: 1,
                    DiagnosticCodes.W0201_UNUSED_IMPORT,
                    imp.fqn,
                ),
            )
        }
    }
}