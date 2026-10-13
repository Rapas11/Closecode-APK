package com.closecode.lang

import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPerformanceTest {

    private fun buildLargeSource(targetChars: Int): String {
        val sb = StringBuilder()
        sb.append("package com.example.app\n\n")
        sb.append("import android.os.Bundle\n")
        sb.append("import androidx.appcompat.app.AppCompatActivity\n\n")
        var index = 0
        while (sb.length < targetChars) {
            index++
            sb.append("class Screen").append(index).append(" : AppCompatActivity() {\n")
            sb.append("    private var counter").append(index).append(": Int = 0\n")
            sb.append("    private val label").append(index).append(": String = \"screen ")
                .append(index).append("\"\n\n")
            sb.append("    override fun onCreate(savedInstanceState: Bundle?) {\n")
            sb.append("        super.onCreate(savedInstanceState)\n")
            sb.append("        val total").append(index)
                .append(" = listOf(1, 2, 3).map { it * ").append(index).append(" }.sum()\n")
            sb.append("        for (item in listOf(\"a\", \"b\")) {\n")
            sb.append("            if (item.isNotEmpty()) counter").append(index).append(" += total")
                .append(index).append("\n")
            sb.append("        }\n")
            sb.append("    }\n\n")
            sb.append("    fun describe").append(index).append("(): String = when {\n")
            sb.append("        counter").append(index).append(" > 10 -> \"big \" + label")
                .append(index).append("\n")
            sb.append("        else -> \"small \" + label").append(index).append("\n")
            sb.append("    }\n")
            sb.append("}\n\n")
        }
        return sb.toString()
    }

    @Test
    fun largeFileAnalysisStaysUnderBudget() {
        val source = buildLargeSource(190_000)
        assertTrue("test source too small: ${source.length}", source.length > 150_000)

        val started = System.nanoTime()
        val result = KotlinAnalyzer.analyze(source, AndroidApiIndex.EMPTY)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        println("PERF analyzed ${source.length} chars in ${elapsedMs}ms")
        assertTrue("analysis took ${elapsedMs}ms, budget is 1200ms", elapsedMs < 1_200)
        assertTrue("expected many scopes", result.symbols.allScopes.size > 100)
    }
}