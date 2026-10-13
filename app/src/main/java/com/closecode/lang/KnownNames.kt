package com.closecode.lang

object KnownNames {
    val implicit: Set<String> = setOf(
        "it", "this", "field", "value", "R",
    )

    val kotlinTopLevel: Set<String> = setOf(
        "println", "print", "readLine", "readln", "readlnOrNull",
        "listOf", "mutableListOf", "arrayListOf", "emptyList",
        "setOf", "mutableSetOf", "hashSetOf", "emptySet",
        "mapOf", "mutableMapOf", "hashMapOf", "emptyMap", "linkedMapOf",
        "arrayOf", "emptyArray", "intArrayOf", "doubleArrayOf", "booleanArrayOf",
        "buildString", "buildList", "buildMap", "sequenceOf",
        "lazy", "lazyOf", "require", "requireNotNull", "check", "checkNotNull",
        "error", "TODO", "repeat", "run", "with", "apply", "let", "also", "takeIf",
        "synchronized", "assert", "arrayOfNulls", "generateSequence",
        "maxOf", "minOf", "compareValuesBy", "Pair", "Triple",
        "listOfNotNull", "mutableListOfNotNull", "setOfNotNull", "arrayOfNulls",
        "to", "until", "downTo", "step", "rangeTo", "and", "or", "xor", "shl", "shr", "ushr", "inv",
        "checkNotNull", "TODO", "typeOf", "arrayOf", "emptyArray", "doubleArrayOf",
        "runBlocking", "launch", "async", "await", "delay", "withContext", "coroutineScope",
        "synchronized", "use", "useLines", "bufferedReader", "bufferedWriter",
        "appendLine", "append", "insert", "deleteCharAt", "setLength", "reverse",
    )

    val javaLangTypes: Set<String> = setOf(
        "NumberFormatException", "IllegalArgumentException", "IllegalStateException",
        "UnsupportedOperationException", "NullPointerException", "IndexOutOfBoundsException",
        "StringIndexOutOfBoundsException", "ArrayIndexOutOfBoundsException",
        "ClassCastException", "ArithmeticException", "NumberFormat", "System",
        "Math", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte",
        "Short", "StringBuffer", "Thread", "Runnable", "AutoCloseable", "Closeable",
        "Cloneable", "Comparable", "Iterable", "ThreadLocal", "StackTraceElement",
        "Deprecated", "Override", "SuppressWarnings", "FunctionalInterface",
        "ReplaceWith", "JvmName", "JvmStatic", "JvmField", "JvmOverloads", "Throws",
        "OptIn", "RequiresOptIn", "Target", "Retention", "Volatile", "Synchronized",
    )

    val kotlinMembers: Set<String> = setOf(
        "toString", "hashCode", "equals", "let", "also", "apply", "run", "with", "takeIf", "takeUnless",
        "isEmpty", "isNotEmpty", "isBlank", "isNotBlank", "isNullOrEmpty", "isNullOrBlank",
        "size", "length", "indices", "first", "last", "firstOrNull", "lastOrNull",
        "single", "singleOrNull", "toList", "toMutableList", "toSet", "toTypedArray",
        "map", "mapNotNull", "filter", "filterNot", "filterNotNull", "forEach", "forEachIndexed",
        "mapIndexed", "flatMap", "fold", "foldRight", "reduce", "sumOf", "count", "any", "all", "none",
        "sorted", "sortedBy", "sortedWith", "sortedDescending", "reversed", "distinct", "distinctBy",
        "groupBy", "associate", "associateWith", "partition", "chunked", "zip", "take", "drop",
        "takeWhile", "dropWhile", "takeLast", "dropLast", "joinToString", "maxOrNull", "minOrNull",
        "sum", "average", "indexOf", "lastIndexOf", "contains", "containsAll", "subList",
        "plus", "minus", "getOrNull", "getOrElse", "getOrDefault", "isNotEmpty",
        "uppercase", "lowercase", "trim", "trimStart", "trimEnd", "split", "substring",
        "substringBefore", "substringAfter", "substringBeforeLast", "substringAfterLast",
        "startsWith", "endsWith", "replace", "format", "toInt", "toLong", "toDouble", "toFloat",
        "toBoolean", "toChar", "toByte", "toShort", "capitalize", "repeat", "padStart", "padEnd",
        "lines", "lineSequence", "chars", "indices", "compareTo", "plusAssign", "minusAssign",
        "inc", "dec", "and", "or", "xor", "shl", "shr", "ushr", "inv", "rangeTo", "until", "downTo",
        "step", "reversed", "asReversed", "binarySearch", "average", "readText", "writeText",
        "readLines", "writeLines", "exists", "mkdirs", "delete", "name", "parentFile", "absolutePath",
        "currentTimeMillis", "nanoTime", "exitProcess", "getProperty", "setProperty", "array",
        "emptyArray", "copyOf", "copyOfRange", "contentEquals", "joinToString",
        "Random", "nextInt", "nextDouble", "nextBoolean", "current",
        "startActivity", "finish", "runOnUiThread", "setContentView", "findViewById",
        "getSharedPreferences", "putString", "getString", "putInt", "getInt",
        "setOnClickListener", "setOnLongClickListener", "setText", "setOnEditorActionListener",
        "makeText", "show", "dismiss", "inflate", "obtain", "recycle", "commit", "apply",
    )

    val androidGlobals: Set<String> = setOf(
        "Log", "Toast", "Handler", "Looper", "Uri", "Intent", "Bundle", "AlertDialog",
        "Build", "Bundle", "Context", "Activity", "View", "TextView", "Button", "EditText",
        "ImageView", "LinearLayout", "FrameLayout", "RelativeLayout", "ScrollView",
        "ViewGroup", "ViewPager", "RecyclerView", "TextWatcher", "SharedPreferences",
        "Notification", "NotificationManager", "PendingIntent", "ConnectivityManager",
        "NotificationCompat", "Environment", "SystemClock", "Settings",
        "d", "v", "i", "w", "e", "wtf",
        "applicationContext", "packageName", "resources", "cacheDir", "filesDir",
        "getSystemService", "getString", "getStringArray", "getDrawable", "getColor",
        "dpToPx", "pxToDp", "hideKeyboard", "showToast", "dp",
    )

    val composeGlobals: Set<String> = setOf(
        "setContent", "remember", "rememberSaveable", "mutableStateOf", "derivedStateOf",
        "Column", "Row", "Box", "Text", "Button", "Icon", "Spacer", "Image", "LazyColumn",
        "LazyRow", "LazyVerticalGrid", "Surface", "Card", "Scaffold", "TopAppBar",
        "MaterialTheme", "TextField", "OutlinedTextField", "Checkbox", "Switch", "Slider",
        "Modifier", "fillMaxWidth", "fillMaxHeight", "fillMaxSize", "padding", "background",
        "size", "width", "height", "align", "weight", "clickable", "verticalScroll",
        "horizontalScroll", "rememberScrollState", "collectAsState", "LaunchedEffect",
        "composable", "Color", "TextStyle", "FontWeight", "Dp", "dp", "sp",
    )

    val kotlinTypes: Set<String> = setOf(
        "Any", "Unit", "Nothing", "String", "CharSequence", "Boolean", "Byte", "Short",
        "Int", "Long", "Char", "Float", "Double", "Number", "Comparable", "Throwable",
        "Exception", "RuntimeException", "IllegalArgumentException", "IllegalStateException",
        "Array", "IntArray", "LongArray", "DoubleArray", "FloatArray", "BooleanArray",
        "CharArray", "ByteArray", "ShortArray", "IntRange", "LongRange", "CharRange",
        "Collection", "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
        "Iterable", "MutableIterable", "Iterator", "MutableIterator", "Sequence",
        "ListIterator", "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet",
        "StringBuilder", "Enum", "Result", "Pair", "Triple", "Lazy", "Regex", "MatchResult",
        "MutableMap.MutableEntry", "Function", "Runnable", "Thread", "Deprecated",
        "JvmStatic", "JvmField", "Suppress", "Throws", "Volatile", "Synchronized",
        "Override", "Deprecated", "OptIn", "RequiresOptIn", "Target", "Retention",
        "Serializable", "Cloneable", "AutoCloseable", "Closeable", "Iterator",
    )

    val all: Set<String> = implicit + kotlinTopLevel + kotlinMembers + androidGlobals +
        composeGlobals + kotlinTypes + javaLangTypes

    /**
     * Every known name with the group it belongs to. Completion needs this so each suggestion can
     * carry a real badge and package line instead of all of them looking like plain keywords.
     */
    fun grouped(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(all.size)
        for (name in kotlinTypes) out.add(name to "kotlin")
        for (name in javaLangTypes) out.add(name to "java.lang")
        for (name in kotlinTopLevel) out.add(name to "kotlin.collections")
        for (name in kotlinMembers) out.add(name to "kotlin")
        for (name in androidGlobals) out.add(name to "android")
        for (name in composeGlobals) out.add(name to "androidx.compose")
        for (name in implicit) out.add(name to "")
        return out.distinctBy { it.first }
    }

    fun isKnown(name: String): Boolean = name in all

    /**
     * Names this checker treats as import-free, for the purpose of deciding whether a missing
     * import is worth reporting.
     *
     * Narrower than [isKnown] on purpose: `Text` and `Button` are listed here for completion, but
     * Kotlin will not compile `Text("x")` without `import androidx.compose.material3.Text`, so
     * appearing in a completion list cannot excuse an unresolved reference. The Compose group is
     * deliberately left out. The android group is kept because it is a short, stable list of the
     * commonest framework names and the checker has to stay quiet when it has no API index loaded.
     */
    fun needsNoImport(name: String): Boolean =
        name in kotlinTypes || name in javaLangTypes || name in implicit || name in androidGlobals

    fun isTypeName(name: String): Boolean = name in kotlinTypes
}