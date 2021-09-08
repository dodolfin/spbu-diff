import kotlin.system.exitProcess
import java.io.File
import java.io.InputStream

const val SIZE_LIMIT = 1024 * 1024 * 1024

/*
 * В случае ошибки вывести сообщение [exitMessage] и завершить программу с ненулевым кодом возврата.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    exitProcess(1)
}

/*
 * Считать содержимое файла, находящегося по адресу [pathToFile]. Пока что эта функция производит и проверки,
 * связанные с файлом (существование, право доступа к нему и т. д.)
 */
fun readFromFile(pathToFile: String): Array<String> {
    val fileObject = File(pathToFile)

    if (!fileObject.exists()) {
        terminateOnError("$pathToFile does not exist.")
    }
    if (!fileObject.isFile()) {
        terminateOnError("$pathToFile is not a normal file.")
    }
    if (!fileObject.canRead()) {
        terminateOnError("$pathToFile is not readable.")
    }
    if (fileObject.length() > SIZE_LIMIT) {
        terminateOnError("$pathToFile exceeds size limit ($SIZE_LIMIT bytes).")
    }

    val inputStream: InputStream = fileObject.inputStream()
    val lineList = mutableListOf<String>()

    inputStream.bufferedReader().forEachLine { lineList.add(it) }

    return lineList.toTypedArray()
}

/*
 * Сравнивает два файла [file1] и [file2], представленные в виде массива строк,
 * и возвращает ответ в следующем формате: solution с двумя массивами со значениями true или false,
 * которые показывают, входит ли данная строка в НОП или нет.
 */
fun compareTwoFiles(file1: Array<String>, file2: Array<String>): Array<Array<Boolean>> {
    /* LCS означает Longest Common Subsequence — наибольшую общую подпоследовательность
     * В массиве LCSMemoization хранятся значения НОП для всех возможных префиксов двух файлов,
     * а в массиве LCSReconstruction хранятся данные для восстановления.
     */
    val LCSMemoization = Array(file1.size + 1) { Array(file2.size + 1) { 0 } }
    val LCSReconstruction = Array(file1.size + 1) { Array(file2.size + 1) { 0 } }

    /*
     * В массиве LCSReconstruction значение
     *  1 означает, что в точке [prefix1 + 1][prefix2 + 1] последние строки совпали и их выгодно было включить в НОП
     * -1 означает, что значение НОП было лучше в точке [prefix1][prefix2 + 1]
     * -2 означает, что значение НОП было лучше в точке [prefix1 + 1][prefix2]
     */
    for (prefix1 in file1.indices) {
        for (prefix2 in file2.indices) {
            if (file1[prefix1] == file2[prefix2]) {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1][prefix2] + 1
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = 1
            } else if (LCSMemoization[prefix1][prefix2 + 1] > LCSMemoization[prefix1 + 1][prefix2]) {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1][prefix2 + 1]
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = -1
            } else {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1 + 1][prefix2]
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = -2
            }
        }
    }

    // Восстановление ответа
    val solution = arrayOf(Array(file1.size) { false }, Array(file2.size) { false })
    var prefix1 = file1.size; var prefix2 = file2.size
    while (prefix1 != 0 && prefix2 != 0) {
        when (LCSReconstruction[prefix1][prefix2]) {
            1 -> {
                solution[0][prefix1 - 1] = true
                solution[1][prefix2 - 1] = true
                prefix1--; prefix2--
            }
            -1 -> prefix1--
            -2 -> prefix2--
        }
    }

    return solution
}

fun main(args: Array<String>) {
    TODO()
}