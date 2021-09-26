package output.normal

import kotlin.test.*

import com.dodolfin.diff.compare.stringsToLines
import com.dodolfin.diff.output.normal.normalOutput
import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal class NormalOutputTests {
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
    fun normalOutputTests() {
        val file1Values = listOf(
            listOf("a", "b", "c", "d", "e", "f", "g", "h"),
            listOf("d", "e", "f"),
            listOf("a", "b", "c", "d"),
            listOf("a", "b", "c", "d", "e", "f", "g"),
        )
        val file2Values = listOf(
            listOf("b", "c", "e", "g", "h"),
            listOf("a", "b", "c", "d", "e", "f"),
            listOf("e", "f", "g"),
            listOf("a", "b", "p", "q", "r", "f", "g"),
        )
        val answers = listOf(
            "1d0\n< a\n4d2\n< d\n6d3\n< f\n".lines(),
            "0a1,3\n> a\n> b\n> c\n".lines(),
            "1,4c1,3\n< a\n< b\n< c\n< d\n---\n> e\n> f\n> g\n".lines(),
            "3,5c3,5\n< c\n< d\n< e\n---\n> p\n> q\n> r\n".lines(),
        )

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])

            comparisonOutputData.comparisonAndOutputTemplate()

            normalOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate)
            assertEquals(answers[i], stream.toString().lines())
            stream.reset()
        }
    }
}