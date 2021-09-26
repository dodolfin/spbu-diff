package output.unified

import kotlin.test.*

import com.dodolfin.diff.compare.stringsToLines
import com.dodolfin.diff.output.unified.unifiedOutput
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

internal class UnifiedOutputTests {
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
    fun unifiedOutputTests() {
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
            "@@ -1,7 +1,4 @@\n-a\n b\n c\n-d\n e\n-f\n g\n".lines(),
            "@@ -1 +1,4 @@\n+a\n+b\n+c\n d\n".lines(),
            "@@ -1,4 +1,3 @@\n-a\n-b\n-c\n-d\n+e\n+f\n+g\n".lines(),
            "@@ -2,5 +2,5 @@\n b\n-c\n-d\n-e\n+p\n+q\n+r\n f\n".lines(),
        )

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])

            val fakeFile1 = File("")
            val fakeFile2 = File("")

            comparisonOutputData.comparisonAndOutputTemplate()

            unifiedOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate, fakeFile1, fakeFile2, 1)
            assertEquals(answers[i], stream.toString().lines().drop(2))
            stream.reset()
        }
    }
}