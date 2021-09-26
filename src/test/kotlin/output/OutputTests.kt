package output

import kotlin.test.*

import com.dodolfin.diff.compare.stringsToLines
import com.dodolfin.diff.output.plainOutput
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

internal class OutputTests {
    private val standardOut = System.out
    private val stream = ByteArrayOutputStream()

    @BeforeTest
    fun setUp() {
        System.setOut(PrintStream(stream))
    }

    @AfterTest
    fun tearDown() {
        System.setOut(standardOut)
    }

    @Test
    fun produceOutputTemplateTests() {
        val file1Values = listOf(
            listOf("a", "b", "c", "d", "e", "f", "g", "h"),
            listOf("a", "b", "c", "d"),
            listOf("a", "b", "c", "d", "e", "f", "g"),
        )
        val file2Values = listOf(
            listOf("b", "c", "e", "g", "h"),
            listOf("e", "f", "g"),
            listOf("a", "b", "p", "q", "r", "f", "g"),
        )
        val answers = listOf(
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            listOf(0, 1, 2, 3, 4, 5, 6),
            listOf(0, 1, 2, 3, 4, 7, 8, 9, 5, 6)
        )

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])

            comparisonOutputData.comparisonAndOutputTemplate()

            assertEquals(answers[i], comparisonOutputData.outputTemplate.map {it.stringIndex})
        }
    }

    @Test
    fun plainOutputTests() {
        val file1Values = listOf(
            listOf("a", "b", "c", "d", "e", "f", "g", "h"),
            listOf("a", "b", "c", "d"),
            listOf("a", "b", "c", "d", "e", "f", "g"),
        )
        val file2Values = listOf(
            listOf("b", "c", "e", "g", "h"),
            listOf("e", "f", "g"),
            listOf("a", "b", "p", "q", "r", "f", "g"),
        )
        val answers = listOf(
            "- a\n  b\n  c\n- d\n  e\n- f\n  g\n  h\n".lines(),
            "- a\n- b\n- c\n- d\n+ e\n+ f\n+ g\n".lines(),
            "  a\n  b\n- c\n- d\n- e\n+ p\n+ q\n+ r\n  f\n  g\n".lines(),
        )

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])

            comparisonOutputData.comparisonAndOutputTemplate()

            plainOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate)
            assertEquals(answers[i], stream.toString().lines())
            stream.reset()
        }
    }
}