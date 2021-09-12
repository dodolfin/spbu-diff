import kotlin.system.exitProcess
import java.io.File
import java.io.InputStream

const val SIZE_LIMIT = 500 * 1024
const val LINE_LIMIT = 10000

enum class ReconstructionMarker {
    NONE, REMOVE_FROM_LCS, LEFT, UP
}

/*
 * Тип данных, определяющий измененный блок в файле. [absoluteStartIndex] — индекс строки в файле (с 0), с которой
 * начинается измененный блок. [relativeStartIndex] — количество общих для двух файлов строк, встретившихся
 * до начала изменённого блока. [length] — длина изменённого блока в строчках.
 */
data class EditedBlock(val absoluteStartIndex: Int, val relativeStartIndex: Int, var length: Int)

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

    inputStream.bufferedReader().forEachLine {
        lineList.add(it)
        if (lineList.size > LINE_LIMIT) {
            terminateOnError("$pathToFile exceeds line limit ($LINE_LIMIT lines).")
        }
    }

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
 * Общие для двух файлов линии отмечены true в [linesMarkers].
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

/*
 * По отметкам для каждой строки общая она для двух файлов или нет [linesMarkers] формирует список изменённых блоков
 * с «координатами» (в строках, относительно начала файла) изменений.
 */
fun getEditedBlocks(linesMarkers: Array<Boolean>): List<EditedBlock> {
    val blocks = mutableListOf<EditedBlock>()
    var commonLinesCounter = 0

    for (i in linesMarkers.indices) {
        if (linesMarkers[i]) {
            ++commonLinesCounter
        } else if (i == 0 || linesMarkers[i - 1]) {
            blocks.add(blocks.size, EditedBlock(i, commonLinesCounter, 1))
        } else {
            blocks[blocks.lastIndex].length++
        }
    }

    return blocks
}

/*
 * «Нормальный» формат вывода (применяется по умолчанию в версии diff для Linux)
 * Изменённые блоки выводятся без контекста вокруг; удаленные строки отмечаются знаком
 * < в начале строки, добавленные — знаком >. Начало каждого блока предваряет описание изменений в формате f1r|op|f2r (в
 * выводе без |), где op — характер операции (a — добавление, d — удаление, c — изменение), f1r описывает область в
 * первом файле, откуда были удалены или куда были бы вставлены строчки из второго файла, если бы их вставили в первый.
 * Аналогично, f2r описывает область во втором файле, куда были добавлены или где были бы строчки из первого файла,
 * если бы их не удалили.
 * Общие для двух файлов линии отмечены true в [linesMarkers].
 */
fun normalOutput(file1: Array<String>, file2: Array<String>, linesMarkers: Array<Array<Boolean>>) {
    val blocks1 = getEditedBlocks(linesMarkers[0])
    val blocks2 = getEditedBlocks(linesMarkers[1])

    var pointer1 = 0; var pointer2 = 0
    var deletedLinesCnt = 0; var addedLinesCnt = 0
    while (pointer1 <= blocks1.lastIndex || pointer2 <= blocks2.lastIndex) {
        if (pointer2 > blocks2.lastIndex || (pointer1 <= blocks1.lastIndex && blocks1[pointer1].relativeStartIndex < blocks2[pointer2].relativeStartIndex)) {
            val deletedBlock = blocks1[pointer1]
            println("${ deletedBlock.absoluteStartIndex + 1 }${ if (deletedBlock.length > 1) ",${ deletedBlock.absoluteStartIndex + deletedBlock.length }" else "" }" +
                    "d" +
                    "${ deletedBlock.relativeStartIndex + addedLinesCnt }")
            for (i in deletedBlock.absoluteStartIndex until (deletedBlock.absoluteStartIndex + deletedBlock.length)) {
                println("< ${file1[i]}")
            }
            deletedLinesCnt += blocks1[pointer1].length

            pointer1++
        } else if (pointer1 > blocks1.lastIndex || blocks2[pointer2].relativeStartIndex < blocks1[pointer1].relativeStartIndex) {
            val addedBlock = blocks2[pointer2]
            println("${ addedBlock.relativeStartIndex + deletedLinesCnt }" +
                    "a" +
                    "${ addedBlock.absoluteStartIndex + 1 }${ if (addedBlock.length > 1) ",${ addedBlock.absoluteStartIndex + addedBlock.length }" else "" }")
            for (i in addedBlock.absoluteStartIndex until (addedBlock.absoluteStartIndex + addedBlock.length)) {
                println("> ${file2[i]}")
            }
            addedLinesCnt += blocks2[pointer2].length

            pointer2++
        } else {
            val deletedBlock = blocks1[pointer1]
            val addedBlock = blocks2[pointer2]
            println("${ deletedBlock.absoluteStartIndex + 1 }${ if (deletedBlock.length > 1) ",${ deletedBlock.absoluteStartIndex + deletedBlock.length }" else "" }" +
                    "c" +
                    "${ addedBlock.absoluteStartIndex + 1 }${ if (addedBlock.length > 1) ",${ addedBlock.absoluteStartIndex + addedBlock.length }" else "" }")
            for (i in deletedBlock.absoluteStartIndex until (deletedBlock.absoluteStartIndex + deletedBlock.length)) {
                println("< ${file1[i]}")
            }
            println("---")
            for (i in addedBlock.absoluteStartIndex until (addedBlock.absoluteStartIndex + addedBlock.length)) {
                println("> ${file2[i]}")
            }
            deletedLinesCnt += deletedBlock.length
            addedLinesCnt += addedBlock.length

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