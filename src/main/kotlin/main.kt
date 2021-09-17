import kotlin.system.exitProcess
import kotlin.math.*
import kotlin.text.Regex
import java.io.File
import java.io.InputStream
import java.time.*

const val SIZE_LIMIT = 500 * 1024
const val LINE_LIMIT = 10000

enum class ArgumentType {
    UNIFIED, NORMAL, PLAIN, HELP
}

enum class ReconstructionMarker {
    NONE, REMOVE_FROM_LCS, LEFT, UP
}

enum class LineMarker {
    NONE, COMMON, DELETED, ADDED
}

enum class BlockType {
    DOESNT_MATTER, ADD, DELETE
}

data class Argument(val argumentType: ArgumentType, val argumentValue: Int = 0)
data class OutputStyle(val commonPrefix: String, val deletedPrefix: String, val addedPrefix: String)

data class ComparisonOutputData(val stringsDictionary: Array<String>, var outputTemplate: Array<Line>, val comparisonData: ComparisonData)
data class ComparisonData(val file1: Array<Line>, val file2: Array<Line>)

val plusMinusStyle = OutputStyle("  ", "- ", "+ ")
val unifiedStyle = OutputStyle(" ", "-", "+")
val normalStyle = OutputStyle("  ", "< ", "> ")

data class Line(val stringIndex: Int, var lineMarker: LineMarker)

data class OutputBlock(val templateStart: Int, var file1Start: Int, var file2Start: Int, var length: Int, val blockType: BlockType = BlockType.DOESNT_MATTER)
data class UnifiedInternalBlock(var leftBound: Int = -1, var rightBound: Int = -1)

/*
 * В случае ошибки вывести сообщение [exitMessage] и завершить программу с ненулевым кодом возврата.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    println("Use README.md or --help option for more information.")
    exitProcess(1)
}

fun showHelpAndTerminate() {
    println("HELP: TODO()")
    exitProcess(0)
}

fun splitIntoArguments(args: Array<String>): List<Argument> {
    val parsedArgs = mutableMapOf<ArgumentType, Int>()

    args.take(if (args.size >= 2) args.size - 2 else args.size).forEach { arg ->
        val shortFormArgRegex = Regex("-([a-zA-z][0-9]*)+")
        val fullFormArgRegex = Regex("--[a-z]+(=[0-9]+)?")

        if (!shortFormArgRegex.matches(arg) && !fullFormArgRegex.matches(arg)) {
            terminateOnError("${arg} is invalid argument.")
        }

        val isShortForm = shortFormArgRegex.matches(arg)
        val searchRegex: Regex
        if (isShortForm) {
            searchRegex = Regex("([a-zA-Z])([0-9]*)")
        } else {
            searchRegex = Regex("([a-z]+)(=[0-9]+)?")
        }

        searchRegex.findAll(arg).forEach {
            var (flag, value) = it.destructured

            if (value.length > 9) {
                terminateOnError("$flag value $value is too big.")
            }
            if (value.isNotEmpty() && !isShortForm) {
                value = value.drop(1)
            }
            var valueToInt = if (value.isNotEmpty()) value.toInt() else 0

            if (isShortForm) {
                parsedArgs[when (flag) {
                    "p" -> ArgumentType.PLAIN
                    "n" -> ArgumentType.NORMAL
                    "u" -> {
                        valueToInt = if (value.isNotEmpty()) valueToInt else 3
                        ArgumentType.UNIFIED
                    }
                    else -> {
                        terminateOnError("There is no $flag argument.")
                        ArgumentType.HELP
                    }
                }] = valueToInt
            } else {
                parsedArgs[when (flag) {
                    "help" -> ArgumentType.HELP
                    "plain" -> ArgumentType.PLAIN
                    "normal" -> ArgumentType.NORMAL
                    "unified" -> {
                        valueToInt = if (value.isNotEmpty()) valueToInt else 3
                        ArgumentType.UNIFIED
                    }
                    else -> {
                        terminateOnError("There is no $flag argument.")
                        ArgumentType.HELP
                    }
                }] = valueToInt
            }
        }
    }

    return parsedArgs.toList().map { Argument(it.first, it.second) }
}

fun parseArguments(args: Array<String>): List<Argument> {
    val parsedArgs = splitIntoArguments(args)

    if (Argument(ArgumentType.HELP) in parsedArgs) {
        showHelpAndTerminate()
    }

    if (args.size < 2) {
        terminateOnError("Not enough arguments (required 2 paths to files; got ${ args.size }).")
    }

    if (parsedArgs.size > 1) {
        terminateOnError("Conflicting output modes.")
    }

    return parsedArgs
}

fun openFile(pathToFile: String): File {
    val fileObject = File(pathToFile)

    if (!fileObject.exists()) {
        terminateOnError("${fileObject.name} does not exist.")
    }
    if (!fileObject.isFile) {
        terminateOnError("${fileObject.name} is not a normal file.")
    }
    if (!fileObject.canRead()) {
        terminateOnError("${fileObject.name} is not readable.")
    }
    if (fileObject.length() > SIZE_LIMIT) {
        terminateOnError("${fileObject.name} exceeds size limit ($SIZE_LIMIT bytes).")
    }

    return fileObject
}

/*
 * Считать содержимое файла, находящегося по адресу [pathToFile]. Пока что эта функция производит и проверки,
 * связанные с файлом (существование, право доступа к нему и т. д.)
 */
fun readFromFile(fileObject: File): Array<String> {
    val inputStream: InputStream = fileObject.inputStream()
    val lineList = mutableListOf<String>()

    inputStream.bufferedReader().forEachLine {
        lineList.add(it)
        if (lineList.size > LINE_LIMIT) {
            terminateOnError("${fileObject.name} exceeds line limit ($LINE_LIMIT lines).")
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

    return ComparisonOutputData(stringsDictionary.toTypedArray(), Array(0) {Line(0, LineMarker.NONE)},
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

fun produceOutputTemplate(comparisonOutputData: ComparisonOutputData) {
    val file1 = comparisonOutputData.comparisonData.file1
    val file2 = comparisonOutputData.comparisonData.file2
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

    comparisonOutputData.outputTemplate = outputTemplate.toTypedArray()
}

fun printBlock(stringsDictionary: Array<String>, outputTemplate: Array<Line>, block: OutputBlock, style: OutputStyle) {
    outputTemplate.slice(block.templateStart until block.templateStart + block.length).forEach { line ->
        when (line.lineMarker) {
            LineMarker.COMMON -> println("${style.commonPrefix}${stringsDictionary[line.stringIndex]}")
            LineMarker.DELETED -> println("${style.deletedPrefix}${stringsDictionary[line.stringIndex]}")
            LineMarker.ADDED -> println("${style.addedPrefix}${stringsDictionary[line.stringIndex]}")
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

fun getUnifiedBlocks(outputTemplate: Array<Line>, contextLines: Int): List<OutputBlock> {
    val blocksAllBounds = mutableListOf<Int>()

    for (i in outputTemplate.indices) {
        if ((i == 0 && outputTemplate[i].lineMarker != LineMarker.COMMON) ||
            (i != 0 && outputTemplate[i - 1].lineMarker == LineMarker.COMMON && outputTemplate[i].lineMarker != LineMarker.COMMON)) {
            blocksAllBounds.add(max(0, i - contextLines))
        } else if (i != 0 && outputTemplate[i - 1].lineMarker != LineMarker.COMMON && outputTemplate[i].lineMarker == LineMarker.COMMON) {
            blocksAllBounds.add(min(outputTemplate.lastIndex, i - 1 + contextLines))
        }
    }
    if (outputTemplate.last().lineMarker != LineMarker.COMMON) {
        blocksAllBounds.add(outputTemplate.lastIndex)
    }

    val blocksUnifiedBounds = mutableListOf<UnifiedInternalBlock>()
    blocksUnifiedBounds.add(UnifiedInternalBlock(blocksAllBounds[0]))
    for (i in 1 until blocksAllBounds.lastIndex step 2) {
        if (blocksAllBounds[i + 1] - blocksAllBounds[i] > 1) {
            blocksUnifiedBounds.last().rightBound = blocksAllBounds[i]
            blocksUnifiedBounds.add(UnifiedInternalBlock(blocksAllBounds[i + 1]))
        }
    }
    blocksUnifiedBounds.last().rightBound = blocksAllBounds.last()

    val blocksResult = mutableListOf<OutputBlock>()
    var commonLinesCnt = 0
    var deletedLinesCnt = 0
    var addedLinesCnt = 0
    var blockPointer = 0

    for (i in outputTemplate.indices) {
        if (blockPointer <= blocksUnifiedBounds.lastIndex && i in blocksUnifiedBounds[blockPointer].leftBound..blocksUnifiedBounds[blockPointer].rightBound) {
            if (i == blocksUnifiedBounds[blockPointer].leftBound) {
                val file1Start = if (i == 0 && outputTemplate[i].lineMarker == LineMarker.ADDED) -1 else commonLinesCnt + deletedLinesCnt
                val file2Start = if (i == 0 && outputTemplate[i].lineMarker == LineMarker.DELETED) -1 else commonLinesCnt + addedLinesCnt
                blocksResult.add(OutputBlock(i, file1Start, file2Start, 0))
            }

            blocksResult.last().length++
            if (blocksResult.last().file1Start == -1 && outputTemplate[i].lineMarker != LineMarker.ADDED) {
                blocksResult.last().file1Start = 0
            }
            if (blocksResult.last().file2Start == -1 && outputTemplate[i].lineMarker != LineMarker.DELETED) {
                blocksResult.last().file2Start = 0
            }

            if (i == blocksUnifiedBounds[blockPointer].rightBound) {
                blockPointer++
            }
        }

        when (outputTemplate[i].lineMarker) {
            LineMarker.COMMON -> commonLinesCnt++
            LineMarker.ADDED -> addedLinesCnt++
            LineMarker.DELETED -> deletedLinesCnt++
        }
    }

    return blocksResult
}

fun convertEpochToReadableTime(epochValue: Long): OffsetDateTime {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochValue), ZoneId.systemDefault())
}

fun getRelativeBlockLength(block: OutputBlock, outputTemplate: Array<Line>, ignoredLineMarker: LineMarker): Int {
    val blockRange = block.templateStart until block.templateStart + block.length
    return block.length - outputTemplate.slice(blockRange).count {it.lineMarker == ignoredLineMarker}
}

fun unifiedOutput(stringsDictionary: Array<String>, outputTemplate: Array<Line>, file1Object: File, file2Object: File, contextLines: Int = 3) {
    val blocks = getUnifiedBlocks(outputTemplate, contextLines)

    println("--- ${file1Object.name} ${convertEpochToReadableTime(file1Object.lastModified())}")
    println("+++ ${file2Object.name} ${convertEpochToReadableTime(file2Object.lastModified())}")
    blocks.forEach { block ->
        val file1LinesCnt = getRelativeBlockLength(block, outputTemplate, LineMarker.ADDED)
        val file2LinesCnt = getRelativeBlockLength(block, outputTemplate, LineMarker.DELETED)
        println(
            "@@ -${block.file1Start + 1}${if (file1LinesCnt == 1) "" else ",$file1LinesCnt"} " +
                    "+${block.file2Start + 1}${if (file2LinesCnt == 1) "" else ",$file2LinesCnt"} @@"
        )
        printBlock(stringsDictionary, outputTemplate, block, unifiedStyle)
    }
}

fun output(comparisonOutputData: ComparisonOutputData, file1Object: File, file2Object: File, parsedArgs: List<Argument>) {
    when (parsedArgs[0].argumentType) {
        ArgumentType.PLAIN -> plainOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate)
        ArgumentType.NORMAL -> normalOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate)
        ArgumentType.UNIFIED -> unifiedOutput(
            comparisonOutputData.stringsDictionary,
            comparisonOutputData.outputTemplate,
            file1Object,
            file2Object,
            parsedArgs[0].argumentValue
        )
    }
}

fun main(args: Array<String>) {
    val parsedArgs = parseArguments(args)

    val file1Object = openFile(args[args.size - 2])
    val file2Object = openFile(args[args.size - 1])

    val file1Strings = readFromFile(file1Object)
    val file2Strings = readFromFile(file2Object)
    val comparisonOutputData = stringsToLines(file1Strings, file2Strings)
    markNotCommonLines(comparisonOutputData)
    compareTwoFiles(comparisonOutputData.comparisonData)

    produceOutputTemplate(comparisonOutputData)
    output(comparisonOutputData, file1Object, file2Object, parsedArgs)
}