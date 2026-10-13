package com.closecode.lang
import com.closecode.lang.DiagnosticCodes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardrail suite: real-world Android / Compose / idiomatic Kotlin that must never produce a
 * false ERROR. Any failure here means the analyzer is crying wolf, which is worse than silence.
 */
class RealisticKotlinTest {

    private fun errorMessages(src: String): List<String> {
        val result = KotlinAnalyzer.analyze(src)
        val lines = src.lines()
        return result.errors.map {
            val text = lines.getOrNull((it.line - 1).coerceIn(0, lines.lastIndex))?.trim().orEmpty()
            "line ${it.line} [${it.code}]: ${it.message}   >>>  $text"
        }
    }

    private fun assertClean(label: String, src: String) {
        val errors = errorMessages(src)
        assertTrue("$label produced false errors:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    @Test
    fun composeScreen() = assertClean(
        "composeScreen",
        """
        package com.example.app

        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.padding
        import androidx.compose.material3.Button
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.getValue
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.remember
        import androidx.compose.runtime.setValue
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.unit.dp

        @Composable
        fun CounterScreen() {
            var count by remember { mutableStateOf(0) }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Count: ${'$'}count")
                Button(onClick = { count++ }) {
                    Text("Increment")
                }
            }
        }
        """.trimIndent(),
    )

    @Test
    fun activityWithRecyclerView() = assertClean(
        "activityWithRecyclerView",
        """
        package com.example.app

        import android.os.Bundle
        import android.view.LayoutInflater
        import android.view.ViewGroup
        import android.widget.TextView
        import androidx.appcompat.app.AppCompatActivity
        import androidx.recyclerview.widget.LinearLayoutManager
        import androidx.recyclerview.widget.RecyclerView

        class MainActivity : AppCompatActivity() {

            private val items = mutableListOf<String>()

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.activity_main)
                val list = findViewById<RecyclerView>(R.id.recycler)
                list.layoutManager = LinearLayoutManager(this)
                list.adapter = ItemAdapter(items)
                items.add("first")
                items.add("second")
            }
        }

        class ItemAdapter(private val data: List<String>) : RecyclerView.Adapter<ItemAdapter.Holder>() {

            class Holder(view: View) : RecyclerView.ViewHolder(view) {
                val label: TextView = view.findViewById(R.id.label)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_row, parent, false)
                return Holder(view)
            }

            override fun onBindViewHolder(holder: Holder, position: Int) {
                holder.label.text = data[position]
            }

            override fun getItemCount(): Int = data.size
        }
        """.trimIndent(),
    )

    @Test
    fun dataClassWithDefaultsAndCopy() = assertClean(
        "dataClassWithDefaultsAndCopy",
        """
        package com.example.model

        data class User(
            val id: Long,
            val name: String,
            val email: String = "",
            val active: Boolean = true,
        ) {
            fun display(): String = if (active) name else "${'$'}name (inactive)"

            fun withName(newName: String): User = copy(name = newName)
        }
        """.trimIndent(),
    )

    @Test
    fun sealedClassAndWhen() = assertClean(
        "sealedClassAndWhen",
        """
        package com.example.model

        sealed class Result<out T> {
            data class Success<T>(val value: T) : Result<T>()
            data class Failure(val message: String) : Result<Nothing>()
            object Loading : Result<Nothing>()
        }

        fun render(result: Result<String>): String = when (result) {
            is Result.Success -> result.value
            is Result.Failure -> "error: ${'$'}{result.message}"
            Result.Loading -> "loading"
        }
        """.trimIndent(),
    )

    @Test
    fun enumWithConstructorAndBody() = assertClean(
        "enumWithConstructorAndBody",
        """
        package com.example.model

        enum class Priority(val weight: Int) {
            LOW(1) {
                override fun label(): String = "low priority"
            },
            HIGH(10) {
                override fun label(): String = "high priority"
            };

            abstract fun label(): String
        }
        """.trimIndent(),
    )

    @Test
    fun extensionFunctionsAndProperties() = assertClean(
        "extensionFunctionsAndProperties",
        """
        package com.example.util

        val Int.doubled: Int
            get() = this * 2

        fun String.shout(): String = uppercase() + "!"

        fun List<String>.longestOrNull(): String? =
            if (isEmpty()) null else maxByOrNull { it.length }

        fun <T> List<T>.secondOrNull(): T? = if (size >= 2) this[1] else null
        """.trimIndent(),
    )

    @Test
    fun lambdasWithUnderscoreAndDestructuring() = assertClean(
        "lambdasWithUnderscoreAndDestructuring",
        """
        package com.example.app

        fun run() {
            listOf(1, 2, 3).forEach { _ -> println("ignored") }
            listOf(1, 2, 3).mapIndexed { index, _ -> index }
            val (a, b) = Pair(1, 2)
            val (key, value) = "k" to "v"
            println(a + b + key + value)
        }
        """.trimIndent(),
    )

    @Test
    fun labelsLoopsAndRanges() = assertClean(
        "labelsLoopsAndRanges",
        """
        package com.example.app

        fun find(matrix: Array<IntArray>, target: Int): Boolean {
            outer@ for (row in matrix) {
                for (cell in row) {
                    if (cell == target) return true
                    if (cell < 0) continue@outer
                }
            }
            return false
        }

        fun range(): Int = (1..10).sumOf { it * it }
        """.trimIndent(),
    )

    @Test
    fun tryCatchFinallyAndThrow() = assertClean(
        "tryCatchFinallyAndThrow",
        """
        package com.example.app

        fun parse(input: String): Int {
            try {
                return input.trim().toInt()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("not a number: ${'$'}input", e)
            } finally {
                println("done")
            }
        }
        """.trimIndent(),
    )

    @Test
    fun lazyDelegationAndObservable() = assertClean(
        "lazyDelegationAndObservable",
        """
        package com.example.app

        class Repo {
            private val cache: MutableMap<String, String> by lazy { mutableMapOf() }

            var title: String by mutableStateOf("start")

            val heavy: String by lazy {
                "computed"
            }

            fun get(key: String): String? = cache[key]
        }
        """.trimIndent(),
    )

    @Test
    fun genericClassAndVariance() = assertClean(
        "genericClassAndVariance",
        """
        package com.example.util

        class Box<out T : Any>(private val value: T) {
            fun get(): T = value
        }

        fun <T> identity(value: T): T = value

        interface Repo<T> {
            fun all(): List<T>
            fun byId(id: Long): T?
        }

        class StringRepo : Repo<String> {
            override fun all(): List<String> = emptyList()
            override fun byId(id: Long): String? = null
        }
        """.trimIndent(),
    )

    @Test
    fun objectExpressionsAndCompanion() = assertClean(
        "objectExpressionsAndCompanion",
        """
        package com.example.app

        class Counter {
            companion object {
                const val MAX = 100
                fun reset(): Counter = Counter()
            }

            var value = 0

            val listener = object : Runnable {
                override fun run() {
                    value = 0
                }
            }
        }
        """.trimIndent(),
    )

    @Test
    fun annotationsWithUseSiteTargets() = assertClean(
        "annotationsWithUseSiteTargets",
        """
        package com.example.app

        import androidx.annotation.VisibleForTesting

        @file:JvmName("Utils")

        class Holder {
            @get:VisibleForTesting
            internal val internalThing: String = "x"

            @Deprecated("use newThing", ReplaceWith("newThing"))
            fun oldThing() {}
        }
        """.trimIndent(),
    )

    @Test
    fun aliasedAndWildcardImports() = assertClean(
        "aliasedAndWildcardImports",
        """
        package com.example.app

        import android.view.View as AndroidView
        import androidx.compose.material3.*
        import androidx.compose.runtime.Composable

        @Composable
        fun screen(root: AndroidView) {
            Text(text = "hi")
        }
        """.trimIndent(),
    )

    @Test
    fun backtickedNames() = assertClean(
        "backtickedNames",
        """
        package com.example.app

        fun `test something odd`() {}

        class `My Widget` {
            fun `do it now`() {}
        }
        """.trimIndent(),
    )

    @Test
    fun numericLiterals() = assertClean(
        "numericLiterals",
        """
        package com.example.app

        fun numbers(): Double {
            val hex = 0xFF
            val bin = 0b1010
            val big = 1_000_000L
            val f = 1.5f
            val d = 3.14
            val exp = 1e10
            return hex + bin + big + f + d + exp
        }
        """.trimIndent(),
    )

    @Test
    fun nestedAndInnerClasses() = assertClean(
        "nestedAndInnerClasses",
        """
        package com.example.app

        class Outer {
            private val tag = "outer"

            inner class Inner {
                fun tag(): String = tag
            }

            class Nested {
                fun standalone(): Int = 1
            }

            fun make(): Inner = Inner()
        }
        """.trimIndent(),
    )

    @Test
    fun secondaryConstructorsAndInit() = assertClean(
        "secondaryConstructorsAndInit",
        """
        package com.example.app

        class Person {
            val name: String
            var age: Int = 0

            constructor(name: String) {
                this.name = name
            }

            constructor(name: String, age: Int) : this(name) {
                this.age = age
            }

            init {
                require(name.isNotEmpty()) { "name required" }
            }
        }
        """.trimIndent(),
    )

    @Test
    fun typealiasAndVariance() = assertClean(
        "typealiasAndVariance",
        """
        package com.example.app

        typealias Handler = (String) -> Unit
        typealias Names = MutableList<String>

        class Bus {
            private val handlers: MutableList<Handler> = mutableListOf()

            fun subscribe(handler: Handler) {
                handlers.add(handler)
            }

            fun emit(message: String) {
                for (h in handlers) h(message)
            }
        }
        """.trimIndent(),
    )

    @Test
    fun stringTemplates() = assertClean(
        "stringTemplates",
        """
        package com.example.app

        fun render(name: String, count: Int): String {
            val simple = "hello ${'$'}name"
            val nested = "outer ${'$'}{ "inner ${'$'}name" } end"
            val escaped = "cost: ${'$'}{'$'}{amount}"
            val multiline = ${'"'}${'"'}${'"'}
                first
                second ${'$'}count
            ${'"'}${'"'}${'"'}.trimIndent()
            return simple + nested + escaped + multiline
        }
        """.trimIndent(),
    )

    @Test
    fun safeCallsAndNotNull() = assertClean(
        "safeCallsAndNotNull",
        """
        package com.example.app

        class Holder {
            var child: Holder? = null
            var name: String? = null
        }

        fun label(holder: Holder?): String {
            val n = holder?.name ?: "none"
            val forced = holder!!.name
            val chained = holder?.child?.name?.length ?: 0
            return n + forced + chained
        }
        """.trimIndent(),
    )

    @Test
    fun collectionBuildersAndOperators() = assertClean(
        "collectionBuildersAndOperators",
        """
        package com.example.app

        fun build(): Map<String, List<Int>> {
            val out = mutableMapOf<String, List<Int>>()
            out["a"] = listOf(1, 2)
            out["b"] = listOfNotNull(3, null, 4)
            out["c"] = buildList { add(5) }
            val filtered = out.filter { (key, _) -> key.isNotEmpty() }
            val mapped = out.mapValues { (_, value) -> value.size }
            val joined = out.keys.joinToString(separator = ", ")
            return filtered + mapped + mapOf("d" to listOf(joined.length))
        }
        """.trimIndent(),
    )

    @Test
    fun suspendAndCoroutines() = assertClean(
        "suspendAndCoroutines",
        """
        package com.example.app

        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.delay
        import kotlinx.coroutines.withContext

        suspend fun load(id: Long): String = withContext(Dispatchers.IO) {
            delay(10)
            "item ${'$'}id"
        }

        suspend fun loadAll(ids: List<Long>): List<String> {
            val out = ArrayList<String>()
            for (id in ids) {
                out.add(load(id))
            }
            return out
        }
        """.trimIndent(),
    )

    @Test
    fun inlineReifiedAndOperators() = assertClean(
        "inlineReifiedAndOperators",
        """
        package com.example.app

        inline fun <reified T> typeOf(name: String): String = T::class.simpleName ?: name

        infix fun Int.plusOne(other: Int): Int = this + other + 1

        operator fun Int.times(s: String): String = s.repeat(this)

        tailrec fun countDown(n: Int): Int = if (n == 0) 0 else countDown(n - 1)
        """.trimIndent(),
    )

    @Test
    fun coroutineBuildersAndFlow() = assertClean(
        "coroutineBuildersAndFlow",
        """
        package com.example.app

        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        import kotlinx.coroutines.flow.asStateFlow
        import kotlinx.coroutines.launch

        class ViewModel {
            private val _state = MutableStateFlow(0)
            val state: StateFlow<Int> = _state.asStateFlow()

            fun increment() {
                _state.value = _state.value + 1
            }
        }

        fun scope(block: suspend () -> Unit) {
            launch { block() }
        }
        """.trimIndent(),
    )

    @Test
    fun viewBindingAndResources() = assertClean(
        "viewBindingAndResources",
        """
        package com.example.app

        import android.os.Bundle
        import androidx.appcompat.app.AppCompatActivity
        import com.example.app.databinding.ActivityMainBinding

        class MainActivity : AppCompatActivity() {

            private lateinit var binding: ActivityMainBinding

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)
                binding.submit.setOnClickListener {
                    val text = binding.input.text.toString()
                    binding.output.text = text
                }
            }
        }
        """.trimIndent(),
    )

    @Test
    fun deepNestingDoesNotCrash() {
        val src = buildString {
            append("package com.example.app\n\nfun deep() {\n")
            repeat(600) { append("  if (true) {\n") }
            repeat(600) { append("  }\n") }
            append("}\n")
        }
        val result = KotlinAnalyzer.analyze(src)
        assertTrue(result.errors.none { it.message.contains("StackOverflow", ignoreCase = true) })
    }

    @Test
    fun unresolvedReferenceAfterPackageIsReported() {
        val src = """
            package com.example.app

            fun demo() {
                val x = notDefinedAnywhere
            }
        """.trimIndent()
        val messages = errorMessages(src)
        assertTrue(
            "expected an unresolved-reference error, got: ${messages.joinToString()}",
            messages.any { it.contains("notDefinedAnywhere") },
        )
    }

    @Test
    fun defaultTemplateStaysClean() {
        val src = """
            package com.example.app

            import androidx.compose.material3.Text

            fun App() {
                ClosecodeTheme {
                    Text(text = "Hello")
                }
            }
        """.trimIndent()
        // ClosecodeTheme lives in the template's other file, Theme.kt. The app resolves each file
        // with the names declared across the whole project, so the template has to be checked the
        // same way rather than as a file on its own.
        val theme = """
            package com.example.app

            fun ClosecodeTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
            }
        """.trimIndent()
        val project = KotlinAnalyzer.projectSymbols(listOf(src, theme))
        assertTrue(
            "ClosecodeTheme must be visible project-wide",
            "ClosecodeTheme" in project,
        )
        val messages = KotlinAnalyzer.analyze(src, projectSymbols = project).errors.map { it.message }
        assertTrue(
            "default template produced errors:\n${messages.joinToString("\n")}",
            messages.none { it.contains("ClosecodeTheme") },
        )
    }

    @Test
    fun annotationImportsAreNotReportedUnused() {
        val src = """
            package com.example.app

            import androidx.compose.runtime.Composable

            @Composable
            fun Screen() {}
        """.trimIndent()
        val result = KotlinAnalyzer.analyze(src)
        assertEquals(emptyList<String>(), result.warnings.filter { it.code == DiagnosticCodes.W0201_UNUSED_IMPORT }.map { it.message })
    }
}
