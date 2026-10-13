package com.closecode.ui.ide

import com.closecode.lang.KotlinAnalyzer
import org.junit.Test
import kotlin.random.Random

class DiagnosticsTypingFuzzTest {
    private val template =
        "package com.closecode.blankapp\n\n" +
        "import android.os.Bundle\n" +
        "import androidx.activity.ComponentActivity\n" +
        "import androidx.activity.compose.setContent\n" +
        "import androidx.compose.material3.Text\n" +
        "import androidx.compose.runtime.Composable\n\n" +
        "class MainActivity : ComponentActivity() {\n" +
        "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
        "        super.onCreate(savedInstanceState)\n" +
        "        setContent {\n" +
        "            Greeting(\"World\")\n" +
        "        }\n" +
        "    }\n" +
        "}\n\n" +
        "@Composable\n" +
        "fun Greeting(name: String) {\n" +
        "    Text(\"Hello \$name\")\n" +
        "}\n"

    @Test
    fun typingNeverThrowsInTheDiagnosticsPipeline() {
        val rng = Random(1234)
        var text = template
        // Simulate 300 keystrokes: insert or delete one char at a random caret.
        repeat(300) { step ->
            if (rng.nextBoolean() && text.isNotEmpty()) {
                val at = rng.nextInt(text.length)
                text = text.removeRange(at, at + 1)
            } else {
                val at = rng.nextInt(text.length + 1)
                val ch = listOf('x', '{', '}', '(', '"', '\n', ' ', '.', 'f')[rng.nextInt(9)]
                text = text.substring(0, at) + ch + text.substring(at)
            }
            // The app guards analyze itself; here we want every downstream consumer to survive
            // whatever diagnostics (or none) come out.
            val diagnostics = try {
                KotlinAnalyzer.analyze(text).diagnostics
            } catch (e: Exception) {
                // Analyzer throws are swallowed in the app; only downstream must not throw.
                // Fail loudly here so we notice, but keep fuzzing the pipeline with empty input.
                throw AssertionError("analyzer threw at step $step on ${text.length} chars: ${e.message}")
            }
            val ranges = foldableRanges(text)
            val folded = ranges.filter { rng.nextFloat() < 0.3f }.map { it.first }.toSet()
            val map = FoldMap.build(text, folded)
            val mapped = diagnostics.mapNotNull { d ->
                mapDiagnosticToDisplay(map, d)
            }
            val problems = ProblemsByLine.from(mapped)
            // Every unguarded consumer of `problems` in composition:
            for (line in problems.byLine.keys) {
                problems.on(line)
                problems.worstOn(line)
                buildQuickFixActions(line, problems, KotlinAnalyzer.analyze(text), text)
            }
            buildQuickFixActions(null, problems, null, text)
            errorTickFractions(problems.linesWithErrors, text.lines().size, 1, text.lines().size)
            gutterRowCount(text.lines(), 1)
            gutterWindow(text.lines().size, 0f, 1000f, 20f)
            codeWindowRange(text.lines().size, 0f, 1000f, 20f)
            enclosingDeclarationAt(text, rng.nextInt(1, text.lines().size + 1))
        }
    }
}
