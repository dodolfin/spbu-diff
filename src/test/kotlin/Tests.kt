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
            assertEquals(answers[i], compareTwoFiles(file1Values[i], file2Values[i])[0].count { elem -> elem })
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
            plainOutput(file1Values[i], file2Values[i], compareTwoFiles(file1Values[i], file2Values[i]))
            assertEquals(answers[i], stream.toString().lines())
            stream.reset()
        }
    }
}
