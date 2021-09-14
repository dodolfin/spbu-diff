import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.*

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

    @Test
    fun stringsToLinesTests() {
        val strings1 = arrayOf(
            arrayOf("a", "b", "c", "d", "e", "f", "g", "h"),
            arrayOf("a", "b", "c", "d"),
            arrayOf(),
            arrayOf("b", "d", "p", "q", "v", "y", "z"),
            arrayOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b;",
                "    cin >> a >> b;", "    cout << a + b << endl;", "    return 0;", "}"
            )
        )
        val strings2 = arrayOf(
            arrayOf("b", "c", "e", "g", "h"),
            arrayOf("e", "f", "g"),
            arrayOf("a", "b"),
            arrayOf("a", "q", "v", "b", "y", "d", "z"),
            arrayOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b, c;",
                "    cin >> a >> b >> c;", "    cout << a + b + c << endl;", "    return 0;", "}"
            )
        )
        val stringsDictionary = arrayOf(
            arrayOf("a", "b", "c", "d", "e", "f", "g", "h"),
            arrayOf("a", "b", "c", "d", "e", "f", "g"),
            arrayOf("a", "b"),
            arrayOf("b", "d", "p", "q", "v", "y", "z", "a"),
            arrayOf("#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b;",
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
            assertTrue(stringsDictionary[i].contentEquals(comparisonOutputData.stringsDictionary))
            assertEquals(file1[i], comparisonOutputData.comparisonData.file1.map {it.stringIndex})
            assertEquals(file2[i], comparisonOutputData.comparisonData.file2.map {it.stringIndex})
        }
    }

    @Test
    fun compareTwoFilesTests() {
        val file1Values = arrayOf(
            arrayOf("a", "b", "c", "d", "e", "f", "g", "h"),
            arrayOf("a", "b", "c", "d"),
            arrayOf(),
            arrayOf("b", "d", "p", "q", "v", "y", "z"),
            arrayOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b;",
                "    cin >> a >> b;", "    cout << a + b << endl;", "    return 0;", "}"
            )
        )
        val file2Values = arrayOf(
            arrayOf("b", "c", "e", "g", "h"),
            arrayOf("e", "f", "g"),
            arrayOf("a", "b"),
            arrayOf("a", "q", "v", "b", "y", "d", "z"),
            arrayOf(
                "#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b, c;",
                "    cin >> a >> b >> c;", "    cout << a + b + c << endl;", "    return 0;", "}"
            )
        )
        val answers = arrayOf(5, 0, 0, 4, 6)

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])
            markNotCommonLines(comparisonOutputData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            val file1 = comparisonOutputData.comparisonData.file1
            assertEquals(answers[i], file1.count { it.lineMarker == LineMarker.COMMON })
        }
    }

    @Test
    fun plainOutputTests() {
        val file1Values = arrayOf(
            arrayOf("a", "b", "c", "d", "e", "f", "g", "h"),
            arrayOf("a", "b", "c", "d"),
            arrayOf("a", "b", "c", "d", "e", "f", "g"),
        )
        val file2Values = arrayOf(
            arrayOf("b", "c", "e", "g", "h"),
            arrayOf("e", "f", "g"),
            arrayOf("a", "b", "p", "q", "r", "f", "g"),
        )
        val answers = arrayOf(
            "- a\n  b\n  c\n- d\n  e\n- f\n  g\n  h\n".lines(),
            "- a\n- b\n- c\n- d\n+ e\n+ f\n+ g\n".lines(),
            "  a\n  b\n- c\n- d\n- e\n+ p\n+ q\n+ r\n  f\n  g\n".lines(),
        )

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])
            markNotCommonLines(comparisonOutputData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            plainOutput(comparisonOutputData)
            assertEquals(answers[i], stream.toString().lines())
            stream.reset()
        }
    }

    @Test
    fun normalOutputTests() {
        val file1Values = arrayOf(
            arrayOf("a", "b", "c", "d", "e", "f", "g", "h"),
            arrayOf("d", "e", "f"),
            arrayOf("a", "b", "c", "d"),
            arrayOf("a", "b", "c", "d", "e", "f", "g"),
        )
        val file2Values = arrayOf(
            arrayOf("b", "c", "e", "g", "h"),
            arrayOf("a", "b", "c", "d", "e", "f"),
            arrayOf("e", "f", "g"),
            arrayOf("a", "b", "p", "q", "r", "f", "g"),
        )
        val answers = arrayOf(
            "1d0\n< a\n4d2\n< d\n6d3\n< f\n".lines(),
            "0a1,3\n> a\n> b\n> c\n".lines(),
            "1,4c1,3\n< a\n< b\n< c\n< d\n---\n> e\n> f\n> g\n".lines(),
            "3,5c3,5\n< c\n< d\n< e\n---\n> p\n> q\n> r\n".lines(),
        )

        for (i in file1Values.indices) {
            val comparisonOutputData = stringsToLines(file1Values[i], file2Values[i])
            markNotCommonLines(comparisonOutputData)
            compareTwoFiles(comparisonOutputData.comparisonData)

            normalOutput(comparisonOutputData)
            assertEquals(answers[i], stream.toString().lines())
            stream.reset()
        }
    }
}
