package com.closecode.lang

enum class TokenKind {
    NEWLINE,
    LINE_COMMENT,
    BLOCK_COMMENT,
    IDENTIFIER,
    BACKTICK_IDENT,
    KEYWORD,
    NUMBER,
    STRING,
    CHARACTER,
    ANNOTATION,
    OPERATOR,
    DOT,
    COMMA,
    SEMICOLON,
    COLON,
    AT,
    OPEN_PAREN,
    CLOSE_PAREN,
    OPEN_BRACKET,
    CLOSE_BRACKET,
    OPEN_BRACE,
    CLOSE_BRACE,
    ARROW,
    UNKNOWN;

    val isTrivia: Boolean
        get() = this == NEWLINE || this == LINE_COMMENT || this == BLOCK_COMMENT

    val isStringLike: Boolean
        get() = this == STRING || this == CHARACTER
}

data class Token(
    val kind: TokenKind,
    val text: String,
    val start: Int,
    val end: Int,
    val line: Int,
    val column: Int,
) {
    val isIdentifierLike: Boolean
        get() = kind == TokenKind.IDENTIFIER || kind == TokenKind.BACKTICK_IDENT

    val isNameLike: Boolean
        get() = isIdentifierLike || (kind == TokenKind.KEYWORD && text in KotlinKeywords.soft)
}

data class LexIssue(val offset: Int, val line: Int, val message: String)

data class LexResult(
    val tokens: List<Token>,
    val issues: List<LexIssue>,
) {
    val significant: List<Token> = tokens.filterNot { it.kind.isTrivia }

    val code: List<Token> = tokens.filterNot {
        it.kind == TokenKind.LINE_COMMENT || it.kind == TokenKind.BLOCK_COMMENT
    }

    val startToIndex: Map<Int, Int> = buildMap(code.size) {
        code.forEachIndexed { index, token -> put(token.start, index) }
    }

    fun indexOfTokenAt(offset: Int): Int = startToIndex[offset] ?: -1

    fun significantAtOrAfter(offset: Int): Int {
        var i = 0
        while (i < significant.size && significant[i].start < offset) i++
        return i
    }
}

object KotlinKeywords {
    val hard: Set<String> = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "import", "in", "interface", "is", "null", "object", "package", "return",
        "super", "this", "throw", "true", "try", "typealias", "val", "var",
        "when", "while",
    )

    val soft: Set<String> = setOf(
        "abstract", "actual", "annotation", "by", "catch", "companion", "const",
        "constructor", "crossinline", "data", "enum", "expect", "external",
        "field", "final", "finally", "get", "init", "infix", "inline", "inner",
        "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "set", "setparam",
        "suspend", "tailrec", "value", "vararg", "where",
    )

    val all: Set<String> = hard + soft

    fun isKeyword(text: String): Boolean = text in all
}

private val MULTI_CHAR_OPERATORS: List<String> = listOf(
    "===", "!==", "..<", "?.", "?:", "->", "..", "::", "!!", "&&", "||",
    "==", "!=", "<=", ">=", "++", "--", "+=", "-=", "*=", "/=", "%=",
)

object KotlinLexer {
    fun lex(source: String): LexResult = Lexer(source).run()
}

private class Lexer(private val src: String) {
    private val tokens = ArrayList<Token>()
    private val issues = ArrayList<LexIssue>()
    private var pos = 0
    private var line = 1
    private var lineStart = 0
    private var litPos = 0
    private var litLine = 1
    private var litLineStart = 0

    fun run(): LexResult {
        while (pos < src.length) nextToken()
        return LexResult(tokens, issues)
    }

    private fun advance() {
        if (src[pos] == '\n') {
            pos++
            line++
            lineStart = pos
        } else {
            pos++
        }
    }

    private fun beginLiteral() {
        litPos = pos
        litLine = line
        litLineStart = lineStart
    }

    private fun flushLiteral() {
        if (pos > litPos) add(TokenKind.STRING, litPos, pos, litLine, litLineStart)
        litPos = pos
        litLine = line
        litLineStart = lineStart
    }

    private fun add(kind: TokenKind, start: Int, end: Int, atLine: Int, atLineStart: Int) {
        if (end <= start) return
        tokens.add(
            Token(
                kind = kind,
                text = src.substring(start, end),
                start = start,
                end = end,
                line = atLine,
                column = start - atLineStart + 1,
            ),
        )
    }

    private fun issue(message: String) {
        issues.add(LexIssue(pos, line, message))
    }

    private fun nextToken() {
        val atLine = line
        val atLineStart = lineStart
        val start = pos
        val c = src[pos]
        when {
            c == '\n' -> {
                add(TokenKind.NEWLINE, start, start + 1, atLine, atLineStart)
                advance()
            }

            c.isWhitespace() -> advance()

            c == '/' && src.getOrNull(pos + 1) == '/' -> {
                while (pos < src.length && src[pos] != '\n') advance()
                add(TokenKind.LINE_COMMENT, start, pos, atLine, atLineStart)
            }

            c == '/' && src.getOrNull(pos + 1) == '*' -> lexBlockComment(atLine, atLineStart)

            c == '"' -> lexStringLiteral()

            c == '\'' -> lexCharLiteral(atLine, atLineStart)

            c == '`' -> lexBacktick(atLine, atLineStart)

            c == '@' -> lexAnnotation(atLine, atLineStart)

            c.isDigit() -> lexNumber(atLine, atLineStart)

            c == '_' || c.isLetter() -> lexWord(atLine, atLineStart)

            else -> lexPunctuation(atLine, atLineStart)
        }
    }

    private fun lexBlockComment(atLine: Int, atLineStart: Int) {
        val start = pos
        advance()
        advance()
        var depth = 1
        while (pos < src.length && depth > 0) {
            if (src[pos] == '/' && src.getOrNull(pos + 1) == '*') {
                depth++
                advance()
                advance()
            } else if (src[pos] == '*' && src.getOrNull(pos + 1) == '/') {
                depth--
                advance()
                advance()
            } else {
                advance()
            }
        }
        if (depth > 0) issue("Block comment is never closed")
        add(TokenKind.BLOCK_COMMENT, start, pos, atLine, atLineStart)
    }

    private fun lexStringLiteral() {
        val raw = src.startsWith("\"\"\"", pos)
        if (raw) {
            advance(); advance(); advance()
        } else {
            advance()
        }
        beginLiteral()
        var closed = false
        while (pos < src.length && !closed) {
            val c = src[pos]
            when {
                c == '\\' -> {
                    advance()
                    if (pos < src.length) advance()
                }

                raw && c == '"' && src.startsWith("\"\"\"", pos) -> {
                    flushLiteral()
                    advance(); advance(); advance()
                    beginLiteral()
                    closed = true
                }

                !raw && c == '"' -> {
                    flushLiteral()
                    advance()
                    beginLiteral()
                    closed = true
                }

                !raw && c == '\n' -> Unit

                c == '$' -> lexTemplatePart()

                else -> advance()
            }
            if (!raw && src.getOrNull(pos) == '\n') break
        }
        flushLiteral()
        if (!closed) issue(if (raw) "Raw string is never closed" else "String is never closed")
    }

    private fun lexTemplatePart() {
        val n = src.getOrNull(pos + 1)
        if (n == '{') {
            flushLiteral()
            val line = line
            val lineStart = lineStart
            advance()
            val braceStart = pos
            advance()
            add(TokenKind.OPEN_BRACE, braceStart, pos, line, lineStart)
            lexTemplateExpression()
            beginLiteral()
            return
        }
        if (n != null && (n == '_' || n.isLetter())) {
            flushLiteral()
            advance()
            val l = line
            val ls = lineStart
            val s = pos
            while (pos < src.length && (src[pos] == '_' || src[pos].isLetterOrDigit())) advance()
            add(TokenKind.IDENTIFIER, s, pos, l, ls)
            beginLiteral()
            return
        }
        advance()
    }

    private fun lexTemplateExpression() {
        var depth = 0
        while (pos < src.length) {
            if (src[pos] == '{') {
                depth++
                val l = line
                val ls = lineStart
                val s = pos
                advance()
                add(TokenKind.OPEN_BRACE, s, pos, l, ls)
            } else if (src[pos] == '}') {
                if (depth == 0) {
                    val l = line
                    val ls = lineStart
                    val s = pos
                    advance()
                    add(TokenKind.CLOSE_BRACE, s, pos, l, ls)
                    return
                }
                depth--
                val l = line
                val ls = lineStart
                val s = pos
                advance()
                add(TokenKind.CLOSE_BRACE, s, pos, l, ls)
            } else {
                nextToken()
            }
        }
        issue("String template is never closed")
    }

    private fun lexCharLiteral(atLine: Int, atLineStart: Int) {
        val start = pos
        advance()
        if (pos < src.length && src[pos] == '\\') {
            advance()
            if (pos < src.length) advance()
        } else if (pos < src.length && src[pos] != '\'') {
            advance()
        }
        if (pos < src.length && src[pos] == '\'') {
            advance()
        } else {
            issue("Character literal is never closed")
        }
        add(TokenKind.CHARACTER, start, pos, atLine, atLineStart)
    }

    private fun lexBacktick(atLine: Int, atLineStart: Int) {
        val start = pos
        advance()
        while (pos < src.length && src[pos] != '`' && src[pos] != '\n') advance()
        if (pos < src.length && src[pos] == '`') {
            advance()
        } else {
            issue("Backtick identifier is never closed")
        }
        add(TokenKind.BACKTICK_IDENT, start, pos, atLine, atLineStart)
    }

    private fun lexAnnotation(atLine: Int, atLineStart: Int) {
        val start = pos
        advance()
        val n = src.getOrNull(pos)
        if (n != null && (n == '_' || n.isLetter())) {
            while (pos < src.length && (src[pos] == '_' || src[pos].isLetterOrDigit())) advance()
            if (src.getOrNull(pos) == ':') {
                val save = pos
                val afterColon = pos + 1
                pos = afterColon
                while (pos < src.length && (src[pos] == '_' || src[pos].isLetterOrDigit())) advance()
                if (pos > afterColon) {
                    add(TokenKind.ANNOTATION, start, pos, atLine, atLineStart)
                    return
                }
                pos = save
            }
            add(TokenKind.ANNOTATION, start, pos, atLine, atLineStart)
        } else if (n == '[') {
            add(TokenKind.AT, start, start + 1, atLine, atLineStart)
        } else {
            add(TokenKind.AT, start, start + 1, atLine, atLineStart)
        }
    }

    private fun lexNumber(atLine: Int, atLineStart: Int) {
        val start = pos
        if (src[pos] == '0' && (src.getOrNull(pos + 1) == 'x' || src.getOrNull(pos + 1) == 'X' ||
                src.getOrNull(pos + 1) == 'b' || src.getOrNull(pos + 1) == 'B')
        ) {
            advance()
            advance()
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_')) advance()
            add(TokenKind.NUMBER, start, pos, atLine, atLineStart)
            return
        }
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '_')) advance()
        if (pos < src.length && src[pos] == '.' && src.getOrNull(pos + 1)?.isDigit() == true) {
            advance()
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '_')) advance()
        }
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            val save = pos
            advance()
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) advance()
            if (pos < src.length && src[pos].isDigit()) {
                while (pos < src.length && src[pos].isDigit()) advance()
            } else {
                pos = save
            }
        }
        while (pos < src.length && (src[pos] in "fFdDlLuU")) advance()
        add(TokenKind.NUMBER, start, pos, atLine, atLineStart)
    }

    private fun lexWord(atLine: Int, atLineStart: Int) {
        val start = pos
        while (pos < src.length && (src[pos] == '_' || src[pos].isLetterOrDigit())) advance()
        val text = src.substring(start, pos)
        val kind = if (KotlinKeywords.isKeyword(text)) TokenKind.KEYWORD else TokenKind.IDENTIFIER
        add(kind, start, pos, atLine, atLineStart)
    }

    private fun lexPunctuation(atLine: Int, atLineStart: Int) {
        val start = pos
        val op = MULTI_CHAR_OPERATORS.firstOrNull { src.startsWith(it, pos) }
        when {
            op != null -> {
                repeat(op.length) { advance() }
                val kind = if (op == "->") TokenKind.ARROW else TokenKind.OPERATOR
                add(kind, start, pos, atLine, atLineStart)
            }

            else -> {
                val kind = when (src[pos]) {
                    '(' -> TokenKind.OPEN_PAREN
                    ')' -> TokenKind.CLOSE_PAREN
                    '[' -> TokenKind.OPEN_BRACKET
                    ']' -> TokenKind.CLOSE_BRACKET
                    '{' -> TokenKind.OPEN_BRACE
                    '}' -> TokenKind.CLOSE_BRACE
                    '.' -> TokenKind.DOT
                    ',' -> TokenKind.COMMA
                    ';' -> TokenKind.SEMICOLON
                    ':' -> TokenKind.COLON
                    '@' -> TokenKind.AT
                    '=', '<', '>', '+', '-', '*', '/', '%', '!', '?', '&', '|', '~' -> TokenKind.OPERATOR
                    else -> TokenKind.UNKNOWN
                }
                advance()
                add(kind, start, pos, atLine, atLineStart)
            }
        }
    }
}