import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.File
import kotlin.test.*
import com.dodolfin.diff.input.*
import com.dodolfin.diff.compare.*
import com.dodolfin.diff.output.*
import com.dodolfin.diff.output.normal.*
import com.dodolfin.diff.output.unified.*

internal class Tests {
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

    // К сожалению, протестировать getValueFromString (и другие такие функции) на случаи, когда diff должен
    // завершать свою работу, кажется невозможным.
    @Test
    fun getValueFromStringTests() {
        assertEquals(100, getValueFromString("flag", "=100", false))
        assertEquals(2, getValueFromString("flag", "2", true))
        assertEquals(976976976, getValueFromString("flag", "976976976", true))
        assertEquals(555666777, getValueFromString("flag", "=555666777", false))
    }

    @Test
    fun getArgumentTypeFromFlagTests() {
        assertEquals(ArgumentType.HELP, getArgumentTypeFromFlag("help"))
        assertEquals(ArgumentType.PLAIN, getArgumentTypeFromFlag("p"))
        assertEquals(ArgumentType.UNIFIED, getArgumentTypeFromFlag("unified"))
        assertEquals(ArgumentType.NORMAL, getArgumentTypeFromFlag("n"))
        assertEquals(ArgumentType.NORMAL, getArgumentTypeFromFlag("normal"))
    }

    @Test
    fun splitIntoArgumentTests() {
        val args = listOf(
            arrayOf("-u", "--unified", "file1", "file2"),
            arrayOf("-nu2ppu3nnu4p", "--help", "--normal", "--unified=2", "file1", "file2"),
            arrayOf("-p19999", "file1", "file2")
        )
        val parsedArgs = listOf(
            setOf(Argument(ArgumentType.UNIFIED, 3)),
            setOf(Argument(ArgumentType.HELP), Argument(ArgumentType.NORMAL), Argument(ArgumentType.PLAIN), Argument(ArgumentType.UNIFIED, 2)),
            setOf(Argument(ArgumentType.PLAIN, 19999))
        )

        args.forEachIndexed { index, it ->
            assertEquals(parsedArgs[index], splitIntoArguments(it).toSet())
        }
    }

    @Test
    fun readFromFileTests() {
        val answers = listOf(
            listOf("few lines formatted", "", "in windows style", "(CRLF)"),
            listOf(),
            listOf("another few lines", "now in Unix style", "(LF)")
        )

        answers.forEachIndexed { index, it ->
            val fileObject = File("src/test/kotlin/files/readFromFileTests/readFromFileTest${ index + 1 }.txt")
            assertEquals(it, readFromFile(fileObject))
        }
    }

    @Test
    fun stringsToLinesTests() {
        val strings1 = listOf(
            listOf("a", "b", "c", "d", "e", "f", "g", "h"),
            listOf("a", "b", "c", "d"),
            listOf(),
            listOf("b", "d", "p", "q", "v", "y", "z"),
            listOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b;",
                "    cin >> a >> b;", "    cout << a + b << endl;", "    return 0;", "}"
            )
        )
        val strings2 = listOf(
            listOf("b", "c", "e", "g", "h"),
            listOf("e", "f", "g"),
            listOf("a", "b"),
            listOf("a", "q", "v", "b", "y", "d", "z"),
            listOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b, c;",
                "    cin >> a >> b >> c;", "    cout << a + b + c << endl;", "    return 0;", "}"
            )
        )
        val stringsDictionary = listOf(
            listOf("a", "b", "c", "d", "e", "f", "g", "h"),
            listOf("a", "b", "c", "d", "e", "f", "g"),
            listOf("a", "b"),
            listOf("b", "d", "p", "q", "v", "y", "z", "a"),
            listOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b;",
                "    cin >> a >> b;", "    cout << a + b << endl;", "    return 0;", "}", "    int a, b, c;",
                "    cin >> a >> b >> c;", "    cout << a + b + c << endl;"
            )
        )
        val file1 = listOf(
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            listOf(0, 1, 2, 3),
            listOf(),
            listOf(0, 1, 2, 3, 4, 5, 6),
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        )
        val file2 = listOf(
            listOf(1, 2, 4, 6, 7),
            listOf(4, 5, 6),
            listOf(0, 1),
            listOf(7, 3, 4, 0, 5, 1, 6),
            listOf(0, 1, 2, 3, 9, 10, 11, 7, 8)
        )

        for (i in strings1.indices) {
            val comparisonOutputData = stringsToLines(strings1[i], strings2[i])
            stringsDictionary[i].forEach { print("$it ") }
            println()
            comparisonOutputData.stringsDictionary.forEach { print("$it ") }
            assertEquals(stringsDictionary[i], comparisonOutputData.stringsDictionary)
            assertEquals(file1[i], comparisonOutputData.comparisonData.file1.map {it.stringIndex})
            assertEquals(file2[i], comparisonOutputData.comparisonData.file2.map {it.stringIndex})
        }
    }

    @Test
    fun markNotCommonLinesTests() {
        val comparisonData = listOf(
            ComparisonData(
                listOf(Line(0), Line(1), Line(2), Line(3), Line(4), Line(5), Line(6)),
                listOf(Line(1), Line(2), Line(3), Line(7))
            ),
            ComparisonData(
                listOf(Line(0), Line(1), Line(2)),
                listOf(Line(2), Line(0), Line(0), Line(1))
            ),
            ComparisonData(
                listOf(Line(0), Line(3), Line(2), Line(1)),
                listOf()
            )
        )
        val answers = listOf(
            ComparisonData(
                listOf(Line(0, LineMarker.DELETED), Line(1), Line(2), Line(3), Line(4, LineMarker.DELETED), Line(5, LineMarker.DELETED), Line(6, LineMarker.DELETED)),
                listOf(Line(1), Line(2), Line(3), Line(7, LineMarker.ADDED))
            ),
            comparisonData[1],
            ComparisonData(
                listOf(Line(0, LineMarker.DELETED), Line(3, LineMarker.DELETED), Line(2, LineMarker.DELETED), Line(1, LineMarker.DELETED)),
                listOf()
            )
        )

        comparisonData.forEachIndexed { index, it ->
            markNotCommonLines(it)
            assertEquals(answers[index], it)
        }
    }

    @Test
    fun compareTwoFilesTests() {
        val file1Values = listOf(
            listOf("a", "b", "c", "d", "e", "f", "g", "h"),
            listOf("a", "b", "c", "d"),
            listOf(),
            listOf("b", "d", "p", "q", "v", "y", "z"),
            listOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b;",
                "    cin >> a >> b;", "    cout << a + b << endl;", "    return 0;", "}"
            )
        )
        val file2Values = listOf(
            listOf("b", "c", "e", "g", "h"),
            listOf("e", "f", "g"),
            listOf("a", "b"),
            listOf("a", "q", "v", "b", "y", "d", "z"),
            listOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b, c;",
                "    cin >> a >> b >> c;", "    cout << a + b + c << endl;", "    return 0;", "}"
            )
        )
        val answers = listOf(5, 0, 0, 4, 6)

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])
            markNotCommonLines(comparisonOutputData.comparisonData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            val file1 = comparisonOutputData.comparisonData.file1
            assertEquals(answers[i], file1.count { it.lineMarker == LineMarker.COMMON })
        }
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
            markNotCommonLines(comparisonOutputData.comparisonData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            produceOutputTemplate(comparisonOutputData)
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
            markNotCommonLines(comparisonOutputData.comparisonData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            produceOutputTemplate(comparisonOutputData)
            plainOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate)
            assertEquals(answers[i], stream.toString().lines())
            stream.reset()
        }
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
            markNotCommonLines(comparisonOutputData.comparisonData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            produceOutputTemplate(comparisonOutputData)
            normalOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate)
            assertEquals(answers[i], stream.toString().lines())
            stream.reset()
        }
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
            markNotCommonLines(comparisonOutputData.comparisonData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            val fakeFile1 = File("")
            val fakeFile2 = File("")

            produceOutputTemplate(comparisonOutputData)
            unifiedOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate, fakeFile1, fakeFile2, 1)
            assertEquals(answers[i], stream.toString().lines().drop(2))
            stream.reset()
        }
    }
}
