package com.closecode.lang

enum class ScopeKind { FILE, CLASS, INTERFACE, OBJECT, FUNCTION, BLOCK, LAMBDA, FOR_LOOP }

enum class SymbolKind {
    CLASS, INTERFACE, OBJECT, COMPANION, ENUM_ENTRY, FUNCTION, EXTENSION_FUNCTION,
    PROPERTY, EXTENSION_PROPERTY, PARAMETER, LOCAL_VARIABLE, CONSTRUCTOR,
}

data class Symbol(
    val name: String,
    val kind: SymbolKind,
    val declaredType: String?,
    val receiverType: String? = null,
    val offset: Int,
    val line: Int,
) {
    val isCallable: Boolean
        get() = kind == SymbolKind.FUNCTION ||
            kind == SymbolKind.EXTENSION_FUNCTION ||
            kind == SymbolKind.CONSTRUCTOR

    val isType: Boolean
        get() = kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE ||
            kind == SymbolKind.OBJECT || kind == SymbolKind.COMPANION
}

class Scope internal constructor(
    val kind: ScopeKind,
    val name: String?,
    val start: Int,
    var end: Int,
    val parent: Scope?,
    val isClassLike: Boolean,
    val virtual: Boolean = false,
    /**
     * Set when this scope's opening brace directly follows a call's `)`, making it a trailing
     * lambda. Unqualified calls inside one resolve against the lambda's receiver.
     */
    var isTrailingLambda: Boolean = false,
) {
    val symbols: MutableList<Symbol> = ArrayList()
    val children: MutableList<Scope> = ArrayList()
    var supertypeNames: List<String> = emptyList()

    /** Supertypes this file never imported, so nothing in the index can vouch for them. */
    var unknownSupertypeNames: List<String> = emptyList()

    val hasUnknownSupertype: Boolean get() = unknownSupertypeNames.isNotEmpty()

    fun contains(offset: Int): Boolean = offset >= start && offset < end

    fun isPositionSensitive(): Boolean = !isClassLike
}

data class ImportEntry(
    val fqn: String,
    val simpleName: String,
    val isWildcard: Boolean,
    val line: Int,
    val offset: Int,
)

class SymbolTable internal constructor(
    val packageName: String,
    val imports: List<ImportEntry>,
    val root: Scope,
    val typeParameters: Set<String> = emptySet(),
) {
    val hasWildcardImport: Boolean = imports.any { it.isWildcard }

    val allScopes: List<Scope> by lazy { flatten(root) }

    private fun flatten(scope: Scope): List<Scope> {
        val out = ArrayList<Scope>()
        val stack = ArrayDeque<Scope>()
        stack.addLast(scope)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            out.add(current)
            for (i in current.children.indices.reversed()) stack.addLast(current.children[i])
        }
        return out
    }

    fun chainAt(offset: Int): List<Scope> {
        val chain = ArrayList<Scope>()
        var scope: Scope? = root
        while (scope != null) {
            chain.add(scope)
            scope = scope.children.firstOrNull { it.contains(offset) }
        }
        return chain
    }

    fun visibleAt(offset: Int): List<Symbol> {
        val out = ArrayList<Symbol>()
        for (scope in chainAt(offset)) {
            for (sym in scope.symbols) {
                if (scope.isPositionSensitive() && sym.offset > offset) continue
                out.add(sym)
            }
            scope.children.filter { it.isClassLike && it.name == "Companion" }.forEach { companion ->
                out.addAll(companion.symbols)
            }
        }
        return out
    }

    fun hasVisible(offset: Int, name: String): Boolean {
        var scope: Scope? = root
        while (scope != null) {
            for (sym in scope.symbols) {
                if (sym.name == name && !(scope.isPositionSensitive() && sym.offset > offset)) return true
            }
            for (companion in scope.children) {
                if (companion.isClassLike && companion.name == "Companion") {
                    for (sym in companion.symbols) {
                        if (sym.name == name) return true
                    }
                }
            }
            scope = scope.children.firstOrNull { it.contains(offset) }
        }
        return false
    }

    fun shouldSuppressUnresolved(offset: Int): Boolean {
        var scope: Scope? = root
        while (scope != null) {
            if (scope.kind == ScopeKind.LAMBDA) return true
            if (scope.isClassLike && scope.hasUnknownSupertype) return true
            scope = scope.children.firstOrNull { it.contains(offset) }
        }
        return false
    }

    fun classScopeAt(offset: Int): Scope? = chainAt(offset).lastOrNull { it.isClassLike }

    fun allSymbols(): List<Symbol> = allScopes.flatMap { it.symbols }

    fun topLevelTypes(): List<Symbol> = root.symbols.filter { it.isType }

    fun typeParameterNames(): Set<String> = typeParameters

    fun scopeKindAt(offset: Int): ScopeKind? = chainAt(offset).lastOrNull()?.kind

    fun inClassWithUnknownSupertype(offset: Int): Boolean =
        chainAt(offset).any { it.isClassLike && it.hasUnknownSupertype }

    /**
     * Whether [offset] sits inside a class that extends anything at all.
     *
     * Distinct from [inClassWithUnknownSupertype]: an imported supertype is known, so the body is
     * checked for unresolved types. This looser question is what the diagnostics ask when a
     * lower-case name could be an inherited member of a supertype we do not have an index for.
     */
    fun inClassWithSupertypeAt(offset: Int): Boolean =
        chainAt(offset).any { it.isClassLike && it.supertypeNames.isNotEmpty() }

    /**
     * Whether [offset] sits inside a brace that directly follows a call's closing parenthesis.
     *
     * That is a trailing lambda, and unqualified calls inside one resolve against the lambda's own
     * receiver, which is not written down anywhere in the file. Recorded while building, because the
     * token list is not kept on the finished table.
     */
    fun insideTrailingLambda(offset: Int): Boolean =
        allScopes.any { it.isTrailingLambda && it.contains(offset) }

    companion object {
        fun build(source: String, lex: LexResult): SymbolTable =
            SymbolTableBuilder(source).build(lex)
    }
}

private class Pending(
    val kind: ScopeKind,
    val name: String?,
    val start: Int,
    val line: Int,
    val isClassLike: Boolean,
    val isEnumClass: Boolean = false,
    val params: List<Symbol> = emptyList(),
    val supertypes: List<String> = emptyList(),
)

private class SymbolTableBuilder(private val source: String) {
    private lateinit var toks: List<Token>
    private lateinit var root: Scope
    private val scopeStack = ArrayList<Scope>()
    private val pending = ArrayList<Pending>()
    private val imports = ArrayList<ImportEntry>()
    private var packageName = ""
    private var currentClassName: String? = null
    private var i = 0

    fun build(lex: LexResult): SymbolTable {
        toks = lex.tokens.filterNot {
            it.kind == TokenKind.LINE_COMMENT || it.kind == TokenKind.BLOCK_COMMENT
        }
        root = Scope(ScopeKind.FILE, null, 0, source.length, null, isClassLike = true)
        scopeStack.add(root)

        while (i < toks.size) {
            val t = toks[i]
            when (t.kind) {
                TokenKind.OPEN_BRACE -> openBrace()
                TokenKind.CLOSE_BRACE -> closeBrace()
                TokenKind.ANNOTATION, TokenKind.AT -> i++
                TokenKind.KEYWORD -> {
                    discardVirtualScopes()
                    i = when (t.text) {
                    "val", "var" -> parseProperty()
                    "fun" -> parseFunction()
                    "class", "interface", "object" -> parseTypeDeclaration()
                    "enum" -> parseEnumOrAnnotation()
                    "companion" -> parseCompanion()
                    "for" -> parseForLoop()
                    "catch" -> parseCatch()
                    "import" -> parseImport()
                    "package" -> parsePackage()
                        else -> i + 1
                    }
                }

                else -> i++
            }
        }

        scopeStack.forEach { if (it.end == Int.MAX_VALUE) it.end = source.length }
        resolveKnownSupertypes(imports.mapTo(HashSet()) { it.simpleName })
        return SymbolTable(packageName, imports, root, collectTypeParameters())
    }

    /**
     * Marks a class as having an unaccountable supertype only when it extends something this file
     * never mentions.
     *
     * This used to be "has any supertype at all", which meant a single `: ComponentActivity()`
     * switched off every unresolved-reference check in the whole class body. Since the Android API
     * index only holds framework classes, any Compose or AndroidX supertype looked unknown, so the
     * bodies of essentially every Android activity were never checked and real errors went
     * unreported. An explicitly imported supertype is declared, not unknown.
     */
    private fun resolveKnownSupertypes(importedSimpleNames: Set<String>) {
        // Walked from the root, not from the live stack: a class scope is popped as soon as its
        // closing brace is reached, so by this point every class but the outermost is off the stack.
        val pending = ArrayDeque<Scope>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val scope = pending.removeLast()
            if (scope.isClassLike && scope.supertypeNames.isNotEmpty()) {
                scope.unknownSupertypeNames =
                    scope.supertypeNames.filterNot { it.substringAfterLast('.') in importedSimpleNames }
            }
            for (child in scope.children) pending.addLast(child)
        }
    }

    private fun collectTypeParameters(): Set<String> {
        val out = HashSet<String>()
        var j = 0
        while (j < toks.size) {
            val t = toks[j]
            val isHeaderKeyword = t.kind == TokenKind.KEYWORD &&
                (t.text == "class" || t.text == "interface" || t.text == "object" ||
                    t.text == "fun" || t.text == "typealias")
            if (!isHeaderKeyword) {
                j++
                continue
            }
            var k = j + 1
            while (k < toks.size) {
                val h = toks[k]
                if (h.kind == TokenKind.OPERATOR && h.text == "<") {
                    val end = skipAngle(k)
                    var d = 1
                    for (m in k + 1 until end) {
                        when {
                            toks[m].kind == TokenKind.OPERATOR && toks[m].text == "<" -> d++
                            toks[m].kind == TokenKind.OPERATOR && toks[m].text == ">" -> d--
                            d == 1 && toks[m].isNameLike && toks[m].text !in NON_TYPE_PARAMS &&
                                toks[m].text !in KotlinKeywords.soft -> out.add(toks[m].text)

                            toks[m].kind == TokenKind.COLON && d == 1 -> {
                                var b = m + 1
                                while (b < end && toks[b].kind == TokenKind.KEYWORD &&
                                    toks[b].text == "out"
                                ) b++
                                if (b < end && toks[b].isNameLike) out.add(toks[b].text)
                            }
                        }
                    }
                    k = end
                    continue
                }
                if (h.kind == TokenKind.OPEN_PAREN || h.kind == TokenKind.OPEN_BRACE ||
                    h.kind == TokenKind.COLON || h.kind == TokenKind.OPERATOR && h.text == "=" ||
                    h.kind == TokenKind.SEMICOLON
                ) break
                k++
            }
            j = k
        }
        return out
    }

    private fun cur(): Scope = scopeStack.last()

    private fun discardVirtualScopes() {
        while (scopeStack.size > 1 && scopeStack.last().virtual) {
            val stale = scopeStack.removeAt(scopeStack.size - 1)
            if (stale.end == Int.MAX_VALUE) stale.end = statementEnd(i)
        }
    }

    private fun push(scope: Scope) {
        if (scopeStack.size >= MAX_SCOPE_DEPTH) return
        cur().children.add(scope)
        scopeStack.add(scope)
        if (scope.isClassLike) currentClassName = scope.name ?: currentClassName
    }

    private fun pop() {
        discardVirtualScopes()
        if (scopeStack.size <= 1) return
        val scope = scopeStack.removeAt(scopeStack.size - 1)
        if (scope.end == Int.MAX_VALUE) scope.end = toks.getOrNull(i)?.start ?: source.length
        if (scope.isClassLike) {
            var p = scope.parent
            currentClassName = null
            while (p != null) {
                if (p.isClassLike) {
                    currentClassName = p.name
                    break
                }
                p = p.parent
            }
        }
    }

    private fun openBrace() {
        val open = toks[i]
        val p = pending.removeLastOrNull()
        val isLambda = p == null && looksLikeLambda(i)
        val scope = when {
            p != null -> Scope(p.kind, p.name, p.start, Int.MAX_VALUE, cur(), p.isClassLike)
            isLambda -> Scope(ScopeKind.LAMBDA, null, open.start, Int.MAX_VALUE, cur(), false)
            else -> Scope(ScopeKind.BLOCK, null, open.start, Int.MAX_VALUE, cur(), false)
        }
        // A brace right after a `)` is only a trailing lambda when nothing was declared just
        // before it. `fun demo() { ... }` also has a `)` in front of its brace, but that is a
        // declaration's parameter list, and treating its body as a lambda would exempt the whole
        // function from unresolved-reference checks.
        //
        // The brace can also follow the callee directly, because Kotlin lets a trailing lambda stand
        // in for the parentheses: `buildList { add(5) }`. A bare `{` after a name is only legal in
        // that shape, so it counts too.
        if (p == null && i > 0) {
            val before = toks[i - 1]
            if (before.kind == TokenKind.CLOSE_PAREN ||
                before.kind == TokenKind.CLOSE_BRACKET ||
                before.isIdentifierLike
            ) {
                scope.isTrailingLambda = true
            }
        }
        p?.params?.let { scope.symbols.addAll(it) }
        if (p != null && p.supertypes.isNotEmpty()) scope.supertypeNames = p.supertypes
        push(scope)
        if (isLambda) {
            val arrow = findArrowAfter(i)
            if (arrow > 0) parseLambdaParams(i + 1, arrow, scope)
        }
        if (p?.isEnumClass == true) {
            val closeIdx = matchOpen(i)
            parseEnumEntries(i + 1, closeIdx, scope)
        }
        i++
    }

    private fun findArrowAfter(openIdx: Int): Int {
        var depth = 0
        var j = openIdx + 1
        while (j < toks.size) {
            when (toks[j].kind) {
                TokenKind.OPEN_BRACE, TokenKind.OPEN_PAREN, TokenKind.OPEN_BRACKET -> depth++
                TokenKind.CLOSE_BRACE, TokenKind.CLOSE_PAREN, TokenKind.CLOSE_BRACKET -> {
                    depth--
                    if (depth < 0) return -1
                }

                TokenKind.ARROW -> if (depth == 0) return j
                else -> {}
            }
            j++
        }
        return -1
    }

    private fun parseLambdaParams(from: Int, until: Int, scope: Scope) {
        var j = from
        var depth = 0
        while (j < until) {
            val t = toks[j]
            when {
                t.kind == TokenKind.OPEN_PAREN -> {
                    if (depth == 0) {
                        val close = matchOpen(j)
                        parseDestructuredParams(j + 1, close, scope)
                        j = close + 1
                        continue
                    }
                    depth++
                }

                t.kind == TokenKind.OPEN_BRACKET -> depth++
                t.kind == TokenKind.CLOSE_PAREN || t.kind == TokenKind.CLOSE_BRACKET -> depth--
                t.isIdentifierLike && depth == 0 && identName(j) != "_" ->
                    scope.symbols.add(
                        Symbol(identName(j), SymbolKind.PARAMETER, null, offset = t.start, line = t.line),
                    )

                else -> {}
            }
            j++
        }
    }

    private fun parseDestructuredParams(from: Int, until: Int, scope: Scope) {
        var depth = 0
        for (j in from until until) {
            val t = toks[j]
            when {
                t.kind == TokenKind.OPEN_PAREN -> depth++
                t.kind == TokenKind.CLOSE_PAREN -> depth--
                t.isIdentifierLike && depth == 0 && identName(j) != "_" && t.text != "in" ->
                    scope.symbols.add(
                        Symbol(identName(j), SymbolKind.PARAMETER, null, offset = t.start, line = t.line),
                    )

                else -> {}
            }
        }
    }

    private fun closeBrace() {
        pop()
        i++
    }

    private fun parseEnumEntries(from: Int, until: Int, scope: Scope) {
        var j = from
        var expectEntry = true
        while (j < until) {
            val t = toks[j]
            when {
                t.kind == TokenKind.NEWLINE || t.kind == TokenKind.COMMA || t.kind == TokenKind.AT ||
                    t.kind == TokenKind.ANNOTATION -> j++

                t.kind == TokenKind.SEMICOLON -> {
                    expectEntry = false
                    j++
                }

                expectEntry && t.isNameLike -> {
                    scope.symbols.add(
                        Symbol(identName(j), SymbolKind.ENUM_ENTRY, scope.name, offset = t.start, line = t.line),
                    )
                    var k = nextNonNewline(j + 1)
                    if (k < until && toks[k].kind == TokenKind.OPEN_PAREN) k = matchOpen(k) + 1
                    k = nextNonNewline(k)
                    if (k < until && toks[k].kind == TokenKind.OPEN_BRACE) k = matchOpen(k) + 1
                    j = nextNonNewline(k)
                }

                else -> j++
            }
        }
    }

    private fun parsePackage(): Int {
        var j = nextNonNewline(i + 1)
        val startIdx = j
        while (j < toks.size && toks[j].kind != TokenKind.NEWLINE && toks[j].kind != TokenKind.SEMICOLON) j++
        packageName = text(startIdx, j).trim()
        return j
    }

    private fun parseImport(): Int {
        var j = nextNonNewline(i + 1)
        val startIdx = j
        while (j < toks.size && toks[j].kind != TokenKind.NEWLINE && toks[j].kind != TokenKind.SEMICOLON) j++
        var fqn = text(startIdx, j).trim()
        var alias: String? = null
        val asIndex = fqn.indexOf(" as ")
        if (asIndex >= 0) {
            alias = fqn.substring(asIndex + 4).trim().trim('`')
            fqn = fqn.substring(0, asIndex).trim()
        }
        val wildcard = fqn.endsWith(".*")
        val simple = when {
            alias != null -> alias
            wildcard -> fqn.removeSuffix(".*").substringAfterLast('.').ifEmpty { "*" }
            else -> fqn.substringAfterLast('.').removeSurrounding("`")
        }
        imports.add(ImportEntry(fqn, simple, wildcard, toks[i].line, toks[startIdx].start))
        return j
    }

    private fun parseEnumOrAnnotation(): Int {
        val j = nextNonNewline(i + 1)
        if (j < toks.size && (toks[j].text == "class" || toks[j].text == "interface")) {
            i = j
            return parseTypeDeclaration(enumDeclared = true)
        }
        return i + 1
    }

    private fun parseCompanion(): Int {
        val j = nextNonNewline(i + 1)
        if (j < toks.size && toks[j].text == "object") {
            i = j
            return parseTypeDeclaration(companion = true)
        }
        return i + 1
    }

    private fun parseTypeDeclaration(enumDeclared: Boolean = false, companion: Boolean = false): Int {
        val kwIdx = i
        val kw = toks[i]
        var j = nextNonNewline(kwIdx + 1)
        while (j < toks.size && isModifier(j)) j = nextNonNewline(j + 1)
        var name: String? = null
        var nameIdx = -1
        if (j < toks.size && toks[j].isNameLike && !isModifier(j)) {
            nameIdx = j
            name = identName(j)
            j = nextNonNewline(j + 1)
            if (j < toks.size && toks[j].kind == TokenKind.OPERATOR && toks[j].text == "<") {
                j = nextNonNewline(skipAngle(j))
            }
        }
        val effectiveName = if (companion) "Companion" else name
        if (effectiveName != null) {
            val kind = when {
                companion -> SymbolKind.COMPANION
                kw.text == "interface" -> SymbolKind.INTERFACE
                kw.text == "object" -> SymbolKind.OBJECT
                else -> SymbolKind.CLASS
            }
            val anchorIdx = if (nameIdx >= 0) nameIdx else kwIdx
            cur().symbols.add(
                Symbol(
                    effectiveName,
                    kind,
                    null,
                    offset = toks[anchorIdx].start,
                    line = toks[anchorIdx].line,
                ),
            )
        }
        val params = ArrayList<Symbol>()
        if (j < toks.size && toks[j].kind == TokenKind.OPEN_PAREN) {
            val close = matchOpen(j)
            params.addAll(parseParams(j, close))
            j = nextNonNewline(close + 1)
        }
        val supertypes = ArrayList<String>()
        if (j < toks.size && toks[j].kind == TokenKind.COLON) {
            val superStart = nextNonNewline(j + 1)
            j = nextNonNewline(scanType(superStart))
            var s = superStart
            var depth = 0
            while (s < j) {
                val st = toks[s]
                val prev = if (s > superStart) toks[s - 1] else null
                val startsType = s == superStart ||
                    (prev != null && (prev.text == "," || prev.kind == TokenKind.DOT))
                when {
                    st.kind == TokenKind.OPERATOR && st.text == "<" -> depth++
                    st.kind == TokenKind.OPERATOR && st.text == ">" -> depth--
                    st.kind == TokenKind.OPEN_PAREN -> {
                        val close = matchOpen(s)
                        if (depth == 0 && startsType) supertypes.add(identName(s))
                        s = close
                    }

                    depth == 0 && startsType && st.isNameLike && !isModifier(s) ->
                        supertypes.add(identName(s))
                }
                s++
            }
        }
        val scopeKind = when {
            kw.text == "interface" -> ScopeKind.INTERFACE
            kw.text == "object" -> ScopeKind.OBJECT
            else -> ScopeKind.CLASS
        }
        val isBlockBody = j < toks.size && toks[j].kind == TokenKind.OPEN_BRACE
        if (isBlockBody) {
            pending.add(
                Pending(
                    scopeKind, effectiveName, toks[kwIdx].start, toks[kwIdx].line, true,
                    enumDeclared, params, supertypes,
                ),
            )
        } else {
            val scope = Scope(
                scopeKind, effectiveName, toks[kwIdx].start, statementEnd(j), cur(),
                isClassLike = true, virtual = true,
            )
            scope.symbols.addAll(params)
            scope.supertypeNames = supertypes
            push(scope)
        }
        return j
    }

    private class ParsedName(val nameIdx: Int, val receiver: String?, val next: Int)

    private fun parseDeclarationName(start: Int): ParsedName {
        if (start >= toks.size || !toks[start].isNameLike) return ParsedName(-1, null, start)
        val chain = ArrayList<Int>()
        chain.add(start)
        var cursor = nextNonNewline(start + 1)
        while (cursor < toks.size && toks[cursor].kind == TokenKind.DOT) {
            val n = nextNonNewline(cursor + 1)
            if (n < toks.size && toks[n].isNameLike) {
                chain.add(n)
                cursor = nextNonNewline(n + 1)
            } else {
                break
            }
        }
        if (chain.size == 1) {
            if (cursor < toks.size && toks[cursor].kind == TokenKind.OPERATOR &&
                toks[cursor].text == "<"
            ) {
                val angleEnd = skipAngle(cursor)
                val afterAngle = nextNonNewline(angleEnd)
                if (afterAngle < toks.size && toks[afterAngle].kind == TokenKind.DOT) {
                    val n = nextNonNewline(afterAngle + 1)
                    if (n < toks.size && toks[n].isNameLike) {
                        return ParsedName(n, text(chain[0], angleEnd), nextNonNewline(n + 1))
                    }
                }
            }
            return ParsedName(chain[0], null, cursor)
        }
        val nameIdx = chain[chain.size - 1]
        val receiverLast = chain[chain.size - 2]
        return ParsedName(nameIdx, text(chain[0], receiverLast + 1), cursor)
    }

    private fun parseProperty(): Int {
        var j = nextNonNewline(i + 1)
        while (j < toks.size && (toks[j].kind == TokenKind.ANNOTATION || isModifier(j))) {
            j = nextNonNewline(j + 1)
        }
        val owner = cur()
        val kind = if (
            owner.kind == ScopeKind.BLOCK || owner.kind == ScopeKind.LAMBDA ||
            owner.kind == ScopeKind.FOR_LOOP || owner.kind == ScopeKind.FUNCTION
        ) SymbolKind.LOCAL_VARIABLE else SymbolKind.PROPERTY

        if (j < toks.size && toks[j].kind == TokenKind.OPEN_PAREN) {
            val close = matchOpen(j)
            var k = nextNonNewline(j + 1)
            while (k < close) {
                if (toks[k].isIdentifierLike && toks[k].text != "_") {
                    owner.symbols.add(
                        Symbol(identName(k), kind, null, offset = toks[k].start, line = toks[k].line),
                    )
                }
                k = nextNonNewline(k + 1)
            }
            return nextNonNewline(close + 1)
        }
        if (j >= toks.size || !toks[j].isNameLike) return j
        val parsed = parseDeclarationName(j)
        val nameIdx = parsed.nameIdx
        var type: String? = null
        var after = parsed.next
        if (after < toks.size && toks[after].kind == TokenKind.COLON) {
            val typeStart = nextNonNewline(after + 1)
            val typeEnd = scanType(typeStart)
            type = text(typeStart, typeEnd).trim().trimEnd('?').ifEmpty { null }
            after = nextNonNewline(typeEnd)
        }
        owner.symbols.add(
            Symbol(
                name = identName(nameIdx),
                kind = if (parsed.receiver != null) SymbolKind.EXTENSION_PROPERTY else kind,
                declaredType = type,
                receiverType = parsed.receiver,
                offset = toks[nameIdx].start,
                line = toks[nameIdx].line,
            ),
        )
        return after
    }

    private fun parseForLoop(): Int {
        var j = nextNonNewline(i + 1)
        if (j >= toks.size || toks[j].kind != TokenKind.OPEN_PAREN) return j
        val close = matchOpen(j)
        val params = ArrayList<Symbol>()
        var k = nextNonNewline(j + 1)
        while (k < close) {
            if (toks[k].isIdentifierLike && toks[k].text != "in" && toks[k].text != "until") {
                params.add(
                    Symbol(identName(k), SymbolKind.LOCAL_VARIABLE, null, offset = toks[k].start, line = toks[k].line),
                )
            }
            k = nextNonNewline(k + 1)
        }
        j = nextNonNewline(close + 1)
        if (j < toks.size && toks[j].kind == TokenKind.OPEN_BRACE) {
            pending.add(Pending(ScopeKind.FOR_LOOP, null, toks[i].start, toks[i].line, false, false, params))
        } else {
            val scope = Scope(ScopeKind.FOR_LOOP, null, toks[i].start, statementEnd(j), cur(), false)
            scope.symbols.addAll(params)
            push(scope)
        }
        return j
    }

    private fun parseCatch(): Int {
        val j = nextNonNewline(i + 1)
        if (j < toks.size && toks[j].kind == TokenKind.OPEN_PAREN) {
            val close = matchOpen(j)
            pending.add(
                Pending(
                    ScopeKind.BLOCK, null, toks[i].start, toks[i].line, false, false,
                    parseParams(j, close),
                ),
            )
            return close
        }
        return j
    }

    private fun parseFunction(): Int {
        val kwIdx = i
        var j = nextNonNewline(kwIdx + 1)
        if (j < toks.size && toks[j].text == "<") j = nextNonNewline(skipAngle(j))
        var isConstructor = false
        if (j < toks.size && toks[j].text == "constructor") {
            isConstructor = true
            j = nextNonNewline(j + 1)
        }
        var name: String? = null
        var nameIdx = -1
        var receiver: String? = null
        if (!isConstructor) {
            val parsed = parseDeclarationName(j)
            if (parsed.nameIdx >= 0) {
                nameIdx = parsed.nameIdx
                name = identName(parsed.nameIdx)
                receiver = parsed.receiver
                j = parsed.next
            }
        }
        val displayName = if (isConstructor) (currentClassName ?: "constructor") else name
        if (displayName != null && nameIdx >= 0) {
            cur().symbols.add(
                Symbol(
                    name = displayName,
                    kind = if (isConstructor) SymbolKind.CONSTRUCTOR else
                        if (receiver != null) SymbolKind.EXTENSION_FUNCTION else SymbolKind.FUNCTION,
                    declaredType = null,
                    receiverType = receiver,
                    offset = toks[nameIdx].start,
                    line = toks[nameIdx].line,
                ),
            )
        }
        val params = ArrayList<Symbol>()
        if (j < toks.size && toks[j].kind == TokenKind.OPEN_PAREN) {
            val close = matchOpen(j)
            params.addAll(parseParams(j, close))
            j = nextNonNewline(close + 1)
        }
        if (j < toks.size && toks[j].kind == TokenKind.COLON) {
            j = nextNonNewline(scanType(nextNonNewline(j + 1)))
        }
        if (j < toks.size && toks[j].kind == TokenKind.OPEN_BRACE) {
            pending.add(
                Pending(ScopeKind.FUNCTION, displayName, toks[kwIdx].start, toks[kwIdx].line, false, false, params),
            )
        } else {
            val scope = Scope(
                ScopeKind.FUNCTION,
                displayName,
                toks[kwIdx].start,
                statementEnd(j),
                cur(),
                false,
                virtual = true,
            )
            scope.symbols.addAll(params)
            push(scope)
        }
        return j
    }

    private fun parseParams(openIdx: Int, closeIdx: Int): List<Symbol> {
        val out = ArrayList<Symbol>()
        var k = nextNonNewline(openIdx + 1)
        while (k < closeIdx) {
            val t = toks[k]
            if (t.kind == TokenKind.COLON && out.isNotEmpty()) {
                var depth = 0
                var m = k + 1
                while (m < closeIdx) {
                    when (toks[m].kind) {
                        TokenKind.OPEN_PAREN, TokenKind.OPEN_BRACKET, TokenKind.OPEN_BRACE -> depth++
                        TokenKind.CLOSE_BRACE -> depth--
                        TokenKind.CLOSE_PAREN, TokenKind.CLOSE_BRACKET -> {
                            if (depth == 0) break
                            depth--
                        }

                        TokenKind.COMMA -> if (depth == 0) break
                        TokenKind.NEWLINE -> if (depth == 0) break
                        TokenKind.OPERATOR -> when (toks[m].text) {
                            "<" -> depth++
                            ">" -> if (depth > 0) depth--
                            "->" -> if (depth == 0) break
                        }

                        else -> {}
                    }
                    m++
                }
                k = nextNonNewline(m)
                continue
            }
            if (t.isIdentifierLike && t.text != "in") {
                var kind = SymbolKind.PARAMETER
                var back = k - 1
                while (back > openIdx && toks[back].kind == TokenKind.NEWLINE) back--
                if (back > openIdx && toks[back].kind == TokenKind.KEYWORD &&
                    (toks[back].text == "val" || toks[back].text == "var")
                ) {
                    kind = SymbolKind.PROPERTY
                }
                var type: String? = null
                val after = nextNonNewline(k + 1)
                if (after < closeIdx && toks[after].kind == TokenKind.COLON) {
                    val typeStart = nextNonNewline(after + 1)
                    val typeEnd = scanType(typeStart, closeIdx)
                    type = text(typeStart, typeEnd).trim().trimEnd('?').ifEmpty { null }
                }
                out.add(Symbol(identName(k), kind, type, offset = t.start, line = t.line))
            }
            k = nextNonNewline(k + 1)
        }
        return out
    }

    private fun looksLikeLambda(openIdx: Int): Boolean {
        var depth = 0
        var j = openIdx + 1
        var budget = 400
        while (j < toks.size && budget-- > 0) {
            val t = toks[j]
            when {
                t.kind == TokenKind.ARROW && depth == 0 -> return true
                t.kind == TokenKind.NEWLINE && depth == 0 -> return false
                t.kind == TokenKind.OPEN_BRACE || t.kind == TokenKind.OPEN_PAREN ||
                    t.kind == TokenKind.OPEN_BRACKET -> depth++
                t.kind == TokenKind.CLOSE_BRACE || t.kind == TokenKind.CLOSE_PAREN ||
                    t.kind == TokenKind.CLOSE_BRACKET -> {
                    depth--
                    if (depth < 0) return false
                }

                t.kind == TokenKind.SEMICOLON && depth == 0 -> return false
                t.kind == TokenKind.KEYWORD && depth == 0 &&
                    t.text in setOf("fun", "val", "var", "class", "return") -> return false
                t.kind == TokenKind.DOT && depth == 0 -> return false
                else -> {}
            }
            j++
        }
        return false
    }

    private fun statementEnd(from: Int): Int {
        var depth = 0
        var j = from
        while (j < toks.size) {
            when (toks[j].kind) {
                TokenKind.OPEN_BRACE, TokenKind.OPEN_PAREN, TokenKind.OPEN_BRACKET -> depth++
                TokenKind.CLOSE_BRACE, TokenKind.CLOSE_PAREN, TokenKind.CLOSE_BRACKET -> depth--
                TokenKind.NEWLINE -> if (depth <= 0) return toks[j].start
                else -> {}
            }
            j++
        }
        return source.length
    }

    private fun scanType(from: Int, hardLimit: Int = toks.size): Int {
        var depth = 0
        var j = from
        while (j < hardLimit && j < toks.size) {
            val t = toks[j]
            if (t.kind == TokenKind.OPEN_PAREN && depth <= 0 && j > 0 &&
                (toks[j - 1].text == "get" || toks[j - 1].text == "set")
            ) return j - 1
            val stop = when {
                t.kind == TokenKind.NEWLINE && depth <= 0 -> true
                t.kind == TokenKind.COMMA && depth <= 0 -> true
                t.kind == TokenKind.SEMICOLON && depth <= 0 -> true
                t.kind == TokenKind.OPEN_BRACE && depth <= 0 -> true
                t.kind == TokenKind.CLOSE_BRACE && depth <= 0 -> true
                t.kind == TokenKind.OPERATOR && t.text == "=" && depth <= 0 -> true
                t.kind == TokenKind.KEYWORD && (t.text == "by" || t.text == "where") && depth <= 0 -> true
                t.kind == TokenKind.CLOSE_PAREN || t.kind == TokenKind.CLOSE_BRACKET -> {
                    if (depth == 0) return j
                    depth--
                    false
                }

                t.kind == TokenKind.OPEN_PAREN || t.kind == TokenKind.OPEN_BRACKET -> {
                    depth++
                    false
                }

                t.kind == TokenKind.OPERATOR && t.text == "<" -> {
                    depth++
                    false
                }

                t.kind == TokenKind.OPERATOR && t.text == ">" -> {
                    if (depth > 0) depth--
                    false
                }

                else -> false
            }
            if (stop) return j
            j++
        }
        return j
    }

    private fun skipAngle(start: Int): Int {
        var depth = 0
        var j = start
        while (j < toks.size) {
            val t = toks[j]
            if (t.kind == TokenKind.OPERATOR && t.text == "<") depth++
            if (t.kind == TokenKind.OPERATOR && t.text == ">") {
                depth--
                if (depth == 0) return j + 1
            }
            if (t.kind == TokenKind.NEWLINE) return j
            j++
        }
        return j
    }

    private fun matchOpen(idx: Int): Int {
        var depth = 0
        var j = idx
        while (j < toks.size) {
            when (toks[j].kind) {
                TokenKind.OPEN_PAREN, TokenKind.OPEN_BRACKET, TokenKind.OPEN_BRACE -> depth++
                TokenKind.CLOSE_PAREN, TokenKind.CLOSE_BRACKET, TokenKind.CLOSE_BRACE -> {
                    depth--
                    if (depth == 0) return j
                }

                else -> {}
            }
            j++
        }
        return toks.size
    }

    private fun isModifier(idx: Int): Boolean {
        if (idx < 0 || idx >= toks.size) return false
        val t = toks[idx]
        return t.kind == TokenKind.KEYWORD && t.text in MODIFIERS
    }

    private fun nextNonNewline(idx: Int): Int {
        var j = idx
        while (j < toks.size && toks[j].kind == TokenKind.NEWLINE) j++
        return j
    }

    private fun identName(idx: Int): String = toks[idx].text.trim('`')

    private fun text(fromTok: Int, toTok: Int): String {
        if (fromTok >= toks.size || toTok <= fromTok || toTok > toks.size) return ""
        val a = toks[fromTok].start
        val b = toks[toTok - 1].end
        return if (b <= a) "" else source.substring(a, b)
    }

    private companion object {
        const val MAX_SCOPE_DEPTH = 200

        val NON_TYPE_PARAMS = setOf("in", "out", "reified")

        val MODIFIERS = setOf(
            "private", "public", "internal", "protected", "override", "open", "final",
            "abstract", "lateinit", "const", "sealed", "data", "inner", "suspend",
            "inline", "noinline", "crossinline", "vararg", "reified", "tailrec", "operator",
            "infix", "external", "expect", "actual", "value", "annotation", "companion",
        )
    }
}