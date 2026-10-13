package com.closecode.lang

object JvmTypes {
    private val KOTLIN_TO_JVM: Map<String, String> = mapOf(
        "Any" to "java.lang.Object",
        "Unit" to "void",
        "Nothing" to "java.lang.Object",
        "String" to "java.lang.String",
        "CharSequence" to "java.lang.CharSequence",
        "Int" to "int",
        "Long" to "long",
        "Short" to "short",
        "Byte" to "byte",
        "Char" to "char",
        "Boolean" to "boolean",
        "Float" to "float",
        "Double" to "double",
        "Number" to "java.lang.Number",
        "StringBuilder" to "java.lang.StringBuilder",
        "Throwable" to "java.lang.Throwable",
        "Exception" to "java.lang.Exception",
        "RuntimeException" to "java.lang.RuntimeException",
        "IllegalArgumentException" to "java.lang.IllegalArgumentException",
        "IllegalStateException" to "java.lang.IllegalStateException",
        "Comparable" to "java.lang.Comparable",
        "Runnable" to "java.lang.Runnable",
        "Thread" to "java.lang.Thread",
        "ArrayList" to "java.util.ArrayList",
        "HashMap" to "java.util.HashMap",
        "HashSet" to "java.util.HashSet",
        "LinkedHashMap" to "java.util.LinkedHashMap",
        "LinkedHashSet" to "java.util.LinkedHashSet",
        "MutableList" to "java.util.ArrayList",
        "MutableMap" to "java.util.HashMap",
        "MutableSet" to "java.util.HashSet",
        "MutableCollection" to "java.util.ArrayList",
        "MutableIterator" to "java.util.Iterator",
        "Iterable" to "java.lang.Iterable",
        "Iterator" to "java.util.Iterator",
        "IntArray" to "int[]",
        "LongArray" to "long[]",
        "DoubleArray" to "double[]",
        "FloatArray" to "float[]",
        "BooleanArray" to "boolean[]",
        "CharArray" to "char[]",
        "ByteArray" to "byte[]",
        "ShortArray" to "short[]",
        "IntRange" to "kotlin.ranges.IntRange",
        "LongRange" to "kotlin.ranges.LongRange",
        "Regex" to "kotlin.text.Regex",
    )

    private val PACKAGE_HINTS: List<Pair<String, String>> = listOf(
        "kotlin.collections." to "kotlin.collections.",
        "kotlin.io." to "kotlin.io.",
        "kotlin.text." to "kotlin.text.",
        "kotlinx.coroutines." to "kotlinx.coroutines.",
    )

    val KOTLIN_BUILTIN_RETURN: Map<String, String> = mapOf(
        "listOf" to "List", "mutableListOf" to "MutableList", "arrayListOf" to "ArrayList",
        "setOf" to "Set", "mutableSetOf" to "MutableSet", "hashSetOf" to "HashSet",
        "mapOf" to "Map", "mutableMapOf" to "MutableMap", "hashMapOf" to "HashMap",
        "arrayOf" to "Array", "buildString" to "String", "buildList" to "List",
        "buildMap" to "Map", "sequenceOf" to "Sequence", "toString" to "String",
        "toInt" to "Int", "toLong" to "Long", "toDouble" to "Double", "toFloat" to "Float",
        "toBoolean" to "Boolean", "toChar" to "Char", "trim" to "String",
        "uppercase" to "String", "lowercase" to "String", "substring" to "String",
        "plus" to "String", "readText" to "String", "format" to "String",
    )

    fun isPrimitive(kotlinType: String): Boolean =
        kotlinType in setOf("Int", "Long", "Short", "Byte", "Char", "Boolean", "Float", "Double")

    fun stripGenerics(raw: String): String {
        var s = raw.trim()
        while (s.endsWith("?")) s = s.dropLast(1).trim()
        val lt = s.indexOf('<')
        if (lt > 0) s = s.substring(0, lt)
        s = s.trim()
        while (s.endsWith("?")) s = s.dropLast(1).trim()
        return s
    }

    fun simpleNameOf(raw: String): String = stripGenerics(raw).substringAfterLast('.')

    fun firstTypeArgument(raw: String): String? {
        val lt = raw.indexOf('<')
        if (lt < 0) return null
        var depth = 0
        var i = lt
        while (i < raw.length) {
            when (raw[i]) {
                '<' -> depth++
                '>' -> {
                    depth--
                    if (depth == 0) {
                        val inner = raw.substring(lt + 1, i)
                        return inner.substringBefore(',').trim().ifEmpty { null }
                    }
                }
            }
            i++
        }
        return null
    }

    fun elementTypeOf(raw: String): String? =
        stripGenerics(raw).let { if (isCollection(it) || it == "Array") firstTypeArgument(raw) else null }

    fun isCollection(raw: String): Boolean {
        val base = stripGenerics(raw)
        return base.endsWith("List") || base.endsWith("Set") || base.endsWith("Collection") ||
            base.endsWith("Map") || base.endsWith("Sequence") || base.endsWith("Array")
    }

    fun toFqName(raw: String?): String? {
        val base = stripGenerics(raw ?: return null)
        if (base.isEmpty()) return null
        KOTLIN_TO_JVM[base]?.let { return it }
        if (base.startsWith("kotlin.collections.") || base.startsWith("kotlin.text.") ||
            base.startsWith("kotlin.io.") || base.startsWith("kotlinx.coroutines.")
        ) {
            return base
        }
        for ((prefix, mapped) in PACKAGE_HINTS) {
            if (base.startsWith(prefix)) return mapped + base.removePrefix(prefix)
        }
        if ('.' in base) return base
        return null
    }

    fun resolve(raw: String?, index: AndroidApiIndex): String? {
        val direct = toFqName(raw)
        if (direct != null && index.classesNamed(direct.substringAfterLast('.')).isNotEmpty()) return direct
        val simple = simpleNameOf(raw ?: return null)
        if (simple.isEmpty()) return null
        val matches = index.classesNamed(simple)
        return matches.firstOrNull()?.fqn
    }
}