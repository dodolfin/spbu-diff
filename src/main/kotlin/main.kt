import kotlin.system.exitProcess
import java.io.File
import java.io.InputStream

const val SIZE_LIMIT = 500 * 1024
const val LINE_LIMIT = 10000

enum class ReconstructionMarker {
    NONE, REMOVE_FROM_LCS, LEFT, UP
}

enum class LineMarker {
    NONE, COMMON, DELETED, ADDED
}

enum class BlockType {
    DOESNT_MATTER, ADD, DELETE
}

data class ComparisonOutputData(val stringsDictionary: Array<String>, val comparisonData: ComparisonData)
data class ComparisonData(val file1: Array<Line>, val file2: Array<Line>)
data class OutputStyle(val commonPrefix: String, val deletedPrefix: String, val addedPrefix: String)

val plusMinusStyle = OutputStyle("  ", "- ", "+ ")
val normalStyle = OutputStyle("  ", "< ", "> ")

data class Line(val stringIndex: Int, var lineMarker: LineMarker)

data class OutputBlock(val templateStart: Int, val file1Start: Int, val file2Start: Int, var length: Int, val blockType: BlockType = BlockType.DOESNT_MATTER)

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

fun stringsToLines(file1Strings: Array<String>, file2Strings: Array<String>): ComparisonOutputData {
    val stringsToIndex = mutableMapOf<String, Int>()
    var freeIndex = 0
    val stringsDictionary = mutableListOf<String>()

    val fromCollections = arrayOf(file1Strings, file2Strings)
    val file1Indices = mutableListOf<Line>(); val file2Indices = mutableListOf<Line>()
    val toCollections = arrayOf(file1Indices, file2Indices)

    for (i in fromCollections.indices) {
        for (string in fromCollections[i]) {
            if (!stringsToIndex.containsKey(string)) {
                stringsToIndex[string] = freeIndex
                stringsDictionary.add(string)
                freeIndex++
            }
            toCollections[i].add(toCollections[i].size, Line(stringsToIndex.getOrDefault(string, 0), LineMarker.NONE))
        }
    }

    return ComparisonOutputData(stringsDictionary.toTypedArray(),
        ComparisonData(file1Indices.toTypedArray(), file2Indices.toTypedArray()))
}

fun markNotCommonLines(comparisonOutputData: ComparisonOutputData) {
    val indicesCount = Array(2) { Array(comparisonOutputData.stringsDictionary.size) {0} }
    val fromCollections = arrayOf(comparisonOutputData.comparisonData.file1, comparisonOutputData.comparisonData.file2)

    for (i in fromCollections.indices) {
        fromCollections[i].forEach {
            indicesCount[i][it.stringIndex]++
        }
    }

    for (i in fromCollections.indices) {
        fromCollections[i].forEach {
            if (indicesCount[1 - i][it.stringIndex] == 0) {
                it.lineMarker = if (i == 0) LineMarker.DELETED else LineMarker.ADDED
            }
        }
    }
}

/*
 * Сравнивает два файла [file1] и [file2], представленные в виде массива строк,
 * и возвращает ответ в следующем формате: solution с двумя массивами со значениями true или false,
 * которые показывают, входит ли данная строка в НОП или нет.
 */
fun compareTwoFiles(comparisonData: ComparisonData) {
    // Перед тем как сравнивать строчки, оставим только те, для которых ещё неизвестно их отношение к LCS
    val file1 = comparisonData.file1.filter { it.lineMarker == LineMarker.NONE }
    val file2 = comparisonData.file2.filter { it.lineMarker == LineMarker.NONE }

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
    file1.forEach { it.lineMarker = LineMarker.DELETED }
    file2.forEach { it.lineMarker = LineMarker.ADDED }
    var prefix1 = file1.size; var prefix2 = file2.size
    while (prefix1 != 0 && prefix2 != 0) {
        when (LCSReconstruction[prefix1][prefix2]) {
            ReconstructionMarker.REMOVE_FROM_LCS -> {
                file1[prefix1 - 1].lineMarker = LineMarker.COMMON
                file2[prefix2 - 1].lineMarker = LineMarker.COMMON
                prefix1--; prefix2--
            }
            ReconstructionMarker.LEFT -> prefix1--
            ReconstructionMarker.UP -> prefix2--
        }
    }
}

fun produceOutputTemplate(comparisonData: ComparisonData): Array<Line> {
    val file1 = comparisonData.file1
    val file2 = comparisonData.file2
    var pointer1 = 0; var pointer2 = 0
    val outputTemplate = mutableListOf<Line>()

    while (pointer1 < file1.size || pointer2 < file2.size) {
        if (pointer2 >= file2.size || (pointer1 < file1.size && file1[pointer1].lineMarker != LineMarker.COMMON)) {
            outputTemplate.add(file1[pointer1])
            pointer1++
        } else if (pointer1 >= file1.size || file2[pointer2].lineMarker != LineMarker.COMMON) {
            outputTemplate.add(file2[pointer2])
            pointer2++
        } else {
            outputTemplate.add(file1[pointer1])
            pointer1++; pointer2++
        }
    }

    return outputTemplate.toTypedArray()
}

fun printBlock(stringsDictionary: Array<String>, outputTemplate: Array<Line>, block: OutputBlock, style: OutputStyle) {
    for (i in block.templateStart until block.templateStart + block.length) {
        when (outputTemplate[i].lineMarker) {
            LineMarker.COMMON -> println("${style.commonPrefix}${stringsDictionary[outputTemplate[i].stringIndex]}")
            LineMarker.DELETED -> println("${style.deletedPrefix}${stringsDictionary[outputTemplate[i].stringIndex]}")
            LineMarker.ADDED -> println("${style.addedPrefix}${stringsDictionary[outputTemplate[i].stringIndex]}")
        }
    }
}

/*
 * Выводит объединение двух файлов [file1] и [file2], показывающее удаление строки минусом в начале, а добавление — плюсом.
 * Общие для двух файлов линии отмечены true в [linesMarkers].
 */
fun plainOutput(stringsDictionary: Array<String>, outputTemplate: Array<Line>) {
    printBlock(stringsDictionary, outputTemplate, OutputBlock(0, 0, 0, outputTemplate.size), plusMinusStyle)
}

/*
 * По отметкам для каждой строки общая она для двух файлов или нет [linesMarkers] формирует список изменённых блоков
 * с «координатами» (в строках, относительно начала файла) изменений.
 */
fun getNormalBlocks(outputTemplate: Array<Line>): List<OutputBlock> {
    val blocks = mutableListOf<OutputBlock>()
    var commonLinesCnt = 0
    var deletedLinesCnt = 0
    var addedLinesCnt = 0

    for (i in outputTemplate.indices) {
        if (outputTemplate[i].lineMarker == LineMarker.COMMON) {
            commonLinesCnt++
        } else {
            if (i == 0 || outputTemplate[i - 1].lineMarker != outputTemplate[i].lineMarker) {
                when (outputTemplate[i].lineMarker) {
                    LineMarker.DELETED -> blocks.add(blocks.size, OutputBlock(i, commonLinesCnt + deletedLinesCnt,
                        commonLinesCnt + addedLinesCnt, 0, BlockType.DELETE))
                    LineMarker.ADDED -> blocks.add(blocks.size, OutputBlock(i, commonLinesCnt + deletedLinesCnt,
                        commonLinesCnt + addedLinesCnt, 0, BlockType.ADD))
                }
            }

            blocks.last().length++
            when (outputTemplate[i].lineMarker) {
                LineMarker.DELETED -> deletedLinesCnt++
                LineMarker.ADDED -> addedLinesCnt++
            }
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
fun normalOutput(stringsDictionary: Array<String>, outputTemplate: Array<Line>) {
    val blocks = getNormalBlocks(outputTemplate)
    var skipThisBlock = false

    for (i in blocks.indices) {
        if (skipThisBlock) {
            skipThisBlock = false
            continue
        }

        if (i != blocks.lastIndex && blocks[i].blockType == BlockType.DELETE && blocks[i + 1].blockType == BlockType.ADD &&
                blocks[i].templateStart + blocks[i].length == blocks[i + 1].templateStart) {
            println("${blocks[i].file1Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file1Start + blocks[i].length}"}" +
                    "c" +
                    "${blocks[i + 1].file2Start + 1}${if (blocks[i + 1].length == 1) "" else ",${blocks[i + 1].file2Start + blocks[i + 1].length}"}")
            printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
            println("---")
            printBlock(stringsDictionary, outputTemplate, blocks[i + 1], normalStyle)
            skipThisBlock = true
        } else if (blocks[i].blockType == BlockType.DELETE) {
            println("${blocks[i].file1Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file1Start + blocks[i].length}"}" +
                    "d" +
                    "${blocks[i].file2Start}")
            printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
        } else {
            println("${blocks[i].file1Start}" +
                    "a" +
                    "${blocks[i].file2Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file2Start + blocks[i].length}"}")
            printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
        }
    }
}

fun main(args: Array<String>) {
    checkArguments(args)

    val file1Strings = readFromFile(args[args.size - 2])
    val file2Strings = readFromFile(args[args.size - 1])
    val comparisonOutputData = stringsToLines(file1Strings, file2Strings)
    markNotCommonLines(comparisonOutputData)
    compareTwoFiles(comparisonOutputData.comparisonData)

    val outputTemplate = produceOutputTemplate(comparisonOutputData.comparisonData)
    normalOutput(comparisonOutputData.stringsDictionary, outputTemplate)
}