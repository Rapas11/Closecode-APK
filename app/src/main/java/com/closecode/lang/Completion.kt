package com.closecode.lang

enum class CompletionKind {
    CLASS, MEMBER, FUNCTION, PROPERTY, PARAMETER, LOCAL, KEYWORD, ENUM_ENTRY,
    STDLIB_CLASS, STDLIB_FUNCTION, STDLIB_MEMBER,
}

data class CompletionItem(
    val label: String,
    val kind: CompletionKind,
    val detail: String? = null,
    val insertText: String = label,
    /** Package or fully-qualified name, shown on the popup's second line. */
    val subtitle: String? = null,
)

class KotlinCompletion(
    private val source: String,
    private val lex: LexResult,
    private val symbols: SymbolTable,
    private val api: AndroidApiIndex,
) {
    private val sig = lex.significant

    fun prefixAt(offset: Int): String {
        var start = offset
        while (start > 0) {
            val c = source[start - 1]
            if (c == '_' || c.isLetterOrDigit()) start-- else break
        }
        return source.substring(start, offset)
    }

    fun complete(offset: Int, limit: Int = 60): List<CompletionItem> {
        if (offset < 0 || offset > source.length) return emptyList()
        val prefix = prefixAt(offset)
        val items = if (isMemberAccess(offset)) memberItems(offset) else scopeItems(offset)
        val filtered = items.filter { it.label.startsWith(prefix, ignoreCase = false) }
        val seen = HashSet<String>()
        return filtered.filter { seen.add(it.label + it.kind.name) }
            .sortedWith(compareBy({ rank(it.kind) }, { it.label }))
            .take(limit)
    }

    private fun rank(kind: CompletionKind): Int = when (kind) {
        CompletionKind.LOCAL -> 0
        CompletionKind.PARAMETER -> 1
        CompletionKind.PROPERTY -> 2
        CompletionKind.FUNCTION -> 3
        CompletionKind.CLASS -> 4
        CompletionKind.MEMBER -> 5
        CompletionKind.ENUM_ENTRY -> 6
        CompletionKind.STDLIB_CLASS -> 7
        CompletionKind.STDLIB_FUNCTION -> 8
        CompletionKind.STDLIB_MEMBER -> 9
        // Bare keywords rank last. There are ~30 of them and they crowd out real names, so they
        // only surface once the prefix actually matches one, such as "wh" -> "while".
        CompletionKind.KEYWORD -> 10
    }

    private fun isMemberAccess(offset: Int): Boolean = isMemberAccessAt(offset)

    fun isMemberAccessAt(offset: Int): Boolean {
        var k = offset
        while (k > 0 && source[k - 1].isWhitespace()) k--
        return k > 0 && (source[k - 1] == '.' || source[k - 1] == '?')
    }

    private fun receiverTypeAt(offset: Int): String? {
        var k = offset
        while (k > 0 && source[k - 1].isWhitespace()) k--
        if (k > 0 && source[k - 1] == '?') k--
        while (k > 0 && source[k - 1].isWhitespace()) k--
        if (k > 0 && source[k - 1] == '.') k-- else return null

        while (k > 0 && source[k - 1].isWhitespace()) k--
        if (k == 0) return null

        return when {
            source[k - 1] == ')' -> null
            source[k - 1] == ']' -> null
            source[k - 1].isLetterOrDigit() || source[k - 1] == '_' || source[k - 1] == '`' -> {
                val end = k
                var start = k
                while (start > 0 && (source[start - 1] == '_' || source[start - 1].isLetterOrDigit() ||
                        source[start - 1] == '`')
                ) {
                    start--
                }
                val name = source.substring(start, end).trim('`')
                inferTypeOf(name, offset, depth = 0)
            }

            else -> null
        }
    }

    private fun inferTypeOf(name: String, offset: Int, depth: Int): String? {
        if (depth > 3) return null
        if (name == "this") {
            return symbols.classScopeAt(offset)?.name
        }
        symbols.visibleAt(offset).firstOrNull { it.name == name }?.let { sym ->
            sym.declaredType?.let { return it }
            if (sym.isType) return sym.name
            if (sym.isCallable) {
                val fqn = JvmTypes.resolve(sym.name, api)
                if (fqn != null) return fqn.substringAfterLast('.')
            }
            return inferFromInitializer(sym.offset, depth)
        }
        if (api.hasClass(name)) return name
        return JvmTypes.KOTLIN_BUILTIN_RETURN.get(name)
    }

    private fun inferFromInitializer(declOffset: Int, depth: Int): String? {
        val idx = sig.indexOfFirst { it.start == declOffset }
        if (idx < 0) return null
        var eq = -1
        for (k in idx + 1 until sig.size) {
            val t = sig[k]
            if (t.kind == TokenKind.OPERATOR && t.text == "=") {
                eq = k
                break
            }
            if (t.kind == TokenKind.NEWLINE || t.kind == TokenKind.SEMICOLON) break
        }
        if (eq < 0) return null
        val first = sig.getOrNull(eq + 1) ?: return null
        return when {
            first.kind == TokenKind.STRING -> "String"
            first.kind == TokenKind.NUMBER -> "Int"
            first.isIdentifierLike && sig.getOrNull(eq + 2)?.kind == TokenKind.OPEN_PAREN ->
                first.text.trim('`').takeIf { api.hasClass(it) || it.firstOrNull()?.isUpperCase() == true }

            first.isIdentifierLike -> first.text.trim('`').let { n -> inferTypeOf(n, first.start, depth + 1) }
            else -> null
        }
    }

    private fun memberItems(offset: Int): List<CompletionItem> {
        val type = receiverTypeAt(offset) ?: return emptyList()
        val out = ArrayList<CompletionItem>()
        val simple = JvmTypes.simpleNameOf(type)
        symbols.allScopes
            .firstOrNull { it.isClassLike && it.name == simple }
            ?.symbols
            ?.forEach { out.add(symbolItem(it.name, kindOf(it), it.declaredType)) }
        val fq = JvmTypes.resolve(type, api)
        if (fq != null) {
            val ownerPackage = fq.substringBeforeLast('.', "").ifEmpty { null }
            for (member in api.membersOfFq(fq, limit = 800)) {
                val kind =
                    if (member.kind == MemberKind.FIELD) CompletionKind.PROPERTY else CompletionKind.MEMBER
                val insert = if (member.kind == MemberKind.METHOD) "${member.name}()" else member.name
                out.add(CompletionItem(member.name, kind, member.display(), insert, ownerPackage))
            }
        }
        return out
    }

    private fun symbolItem(name: String, kind: CompletionKind, type: String?): CompletionItem {
        val insert = if (kind == CompletionKind.FUNCTION) "$name()" else name
        return CompletionItem(name, kind, type, insert)
    }

    private fun scopeItems(offset: Int): List<CompletionItem> {
        val out = ArrayList<CompletionItem>()
        for (sym in symbols.visibleAt(offset)) {
            out.add(symbolItem(sym.name, kindOf(sym), sym.declaredType))
        }
        for (imp in symbols.imports) {
            if (imp.isWildcard || imp.simpleName.isBlank() || imp.simpleName == "*") continue
            out.add(CompletionItem(imp.simpleName, CompletionKind.CLASS, imp.fqn, imp.simpleName, imp.fqn))
        }
        // Grouped so each entry gets a real badge and a package subtitle, the way the reference
        // editor shows "android.os" under a class name. These rank below everything declared in
        // the file so a user's own names always win the limited result list.
        for ((name, group) in KnownNames.grouped()) {
            val kind = when {
                name in KnownNames.kotlinTypes || name in KnownNames.javaLangTypes ->
                    CompletionKind.STDLIB_CLASS

                name in KnownNames.kotlinTopLevel -> CompletionKind.STDLIB_FUNCTION
                else -> CompletionKind.STDLIB_MEMBER
            }
            out.add(CompletionItem(name, kind, group, name, group.ifBlank { null }))
        }
        for (simple in api.knownSimpleNames) {
            out.add(CompletionItem(simple, CompletionKind.CLASS, null, simple, api.packageOf(simple)))
        }
        for (kw in KotlinKeywords.hard) {
            out.add(CompletionItem(kw, CompletionKind.KEYWORD))
        }
        return out
    }

    private fun kindOf(sym: Symbol): CompletionKind = when (sym.kind) {
        SymbolKind.CLASS, SymbolKind.INTERFACE, SymbolKind.OBJECT, SymbolKind.COMPANION -> CompletionKind.CLASS
        SymbolKind.FUNCTION, SymbolKind.EXTENSION_FUNCTION, SymbolKind.CONSTRUCTOR -> CompletionKind.FUNCTION
        SymbolKind.PARAMETER -> CompletionKind.PARAMETER
        SymbolKind.LOCAL_VARIABLE -> CompletionKind.LOCAL
        SymbolKind.ENUM_ENTRY -> CompletionKind.ENUM_ENTRY
        SymbolKind.PROPERTY, SymbolKind.EXTENSION_PROPERTY -> CompletionKind.PROPERTY
    }
}