import kotlin.test.*

internal class Tests {
    @Test
    fun compareTwoFilesTests() {
        val file1Values = arrayOf(
            arrayOf("a", "b", "c", "d", "e", "f", "g", "h"),
            arrayOf("a", "b", "c", "d"),
            arrayOf(),
            arrayOf("b", "d", "p", "q", "v", "y", "z"),
            arrayOf("#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b;",
                "    cin >> a >> b;", "    cout << a + b << endl;", "    return 0;", "}")
        )
        val file2Values = arrayOf(
            arrayOf("b", "c", "e", "g", "h"),
            arrayOf("e", "f", "g"),
            arrayOf("a", "b"),
            arrayOf("a", "q", "v", "b", "y", "d", "z"),
            arrayOf("#include <iostream>", "using namespace std;", "", "int main() {", "    int a, b, c;",
                "    cin >> a >> b >> c;", "    cout << a + b + c << endl;", "    return 0;", "}")
        )
        val answers = arrayOf(5, 0, 0, 4, 6)

        for (i in file1Values.indices) {
            assertEquals(answers[i], compareTwoFiles(file1Values[i], file2Values[i])[0].count { elem -> elem })
        }
    }
}
