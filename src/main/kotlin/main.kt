import kotlin.system.exitProcess
import java.io.File
import java.io.InputStream

const val SIZE_LIMIT = 1024 * 1024 * 1024

enum class ReconstructionMarker {
    NONE, REMOVE_FROM_LCS, LEFT, UP
}

/*
 * В случае ошибки вывести сообщение [exitMessage] и завершить программу с ненулевым кодом возврата.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    exitProcess(1)
}

/*
 * Проверяет, что в программу передано хотя бы 2 аргумента [args] (два файла для сравнения).
 */
fun checkArguments(args: Array<String>) {
    if (args.size < 2) {
        terminateOnError("Not enough arguments (required 2 paths to files; got ${ args.size }).")
    }
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
    val LCSReconstruction = Array(file1.size + 1) { Array(file2.size + 1) { ReconstructionMarker.NONE } }

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
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.REMOVE_FROM_LCS
            } else if (LCSMemoization[prefix1][prefix2 + 1] > LCSMemoization[prefix1 + 1][prefix2]) {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1][prefix2 + 1]
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.LEFT
            } else {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1 + 1][prefix2]
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.UP
            }
        }
    }

    // Восстановление ответа
    val solution = arrayOf(Array(file1.size) { false }, Array(file2.size) { false })
    var prefix1 = file1.size; var prefix2 = file2.size
    while (prefix1 != 0 && prefix2 != 0) {
        when (LCSReconstruction[prefix1][prefix2]) {
            ReconstructionMarker.REMOVE_FROM_LCS -> {
                solution[0][prefix1 - 1] = true
                solution[1][prefix2 - 1] = true
                prefix1--; prefix2--
            }
            ReconstructionMarker.LEFT -> prefix1--
            ReconstructionMarker.UP -> prefix2--
        }
    }

    return solution
}

/*
 * Выводит объединение двух файлов [file1] и [file2], показывающее удаление строки минусом в начале, а добавление — плюсом.
 */
fun plainOutput(file1: Array<String>, file2: Array<String>, linesMarkers: Array<Array<Boolean>>) {
    var pointer1 = 0; var pointer2 = 0

    while (pointer1 < file1.size || pointer2 < file2.size) {
        if (pointer2 >= file2.size || (pointer1 < file1.size && !linesMarkers[0][pointer1])) {
            println("- ${file1[pointer1]}")
            pointer1++
        } else if (pointer1 >= file1.size || !linesMarkers[1][pointer2]) {
            println("+ ${file2[pointer2]}")
            pointer2++
        } else {
            println("  ${file1[pointer1]}")
            pointer1++; pointer2++
        }
    }
}

fun main(args: Array<String>) {
    checkArguments(args)

    val file1 = readFromFile(args[args.size - 2])
    val file2 = readFromFile(args[args.size - 1])

    val linesMarkers = compareTwoFiles(file1, file2)
    plainOutput(file1, file2, linesMarkers)
}