package compare

import kotlin.test.*

import com.dodolfin.diff.compare.ComparisonData
import com.dodolfin.diff.compare.Line
import com.dodolfin.diff.compare.LineMarker
import com.dodolfin.diff.compare.stringsToLines

internal class CompareTests {
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
            it.markNotCommonLines()
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

            comparisonOutputData.comparisonAndOutputTemplate()

            val file1 = comparisonOutputData.comparisonData.file1
            assertEquals(answers[i], file1.count { it.lineMarker == LineMarker.COMMON })
        }
    }
}