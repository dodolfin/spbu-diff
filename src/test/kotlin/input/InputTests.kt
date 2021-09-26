package input

import kotlin.test.*

import com.dodolfin.diff.compare.stringsToLines
import com.dodolfin.diff.input.*
import java.io.File
import kotlin.test.assertEquals

internal class InputTests {
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
            setOf(
                Argument(ArgumentType.HELP), Argument(ArgumentType.NORMAL), Argument(ArgumentType.PLAIN), Argument(
                    ArgumentType.UNIFIED, 2)
            ),
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
            val fileObject = File("test_files/readFromFileTests/readFromFileTest${ index + 1 }.txt")
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
}