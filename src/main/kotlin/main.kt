import kotlin.system.exitProcess
import kotlin.math.*
import kotlin.text.Regex
import java.io.File
import java.io.InputStream
import java.time.*

const val SIZE_LIMIT = 500 * 1024 // Ограничение на размер файла в байтах
const val LINE_LIMIT = 10000 // Ограничение на размер файла в строчках

// Специальное значение, которое означает, что у аргумента не предусмотрено значения или оно не было введено
const val NO_VALUE = Int.MIN_VALUE

/*
 * Обозначает опцию программы, передающуюся в командной строке при запуске программы. [argumentType] — сам аргумент,
 * [argumentValue] — значение аргумента (если не предусмотрено, хранится NO_VALUE)
 */
data class Argument(val argumentType: ArgumentType, val argumentValue: Int = NO_VALUE)

/*
 * Все возможные аргументы командной строки. [shortForm] — короткая форма (одна буква), вызов предваряется одним дефисом,
 * числовое значение записывается сразу после, можно сочетать несколько (например, -a0b1), [fullForm] — полная форма,
 * вызов предваряется двумя дефисами, числовое значение записывается через знак равно (например, --unified=4)
 */
enum class ArgumentType(val shortForm: String, val fullForm: String, val defaultValue: Int = NO_VALUE) {
    UNIFIED("u", "unified", 3),
    NORMAL("n", "normal"),
    PLAIN("p", "plain"),
    HELP("", "help")
}

/*
 * Используется для восстановления ответа в решении задачи о LCS
 */
enum class ReconstructionMarker {
    NONE, REMOVE_FROM_LCS, LEFT, UP
}

/*
 * Данные, необходимые для сравнения и вывода. [stringsDictionary] — общий словарь строчек из двух файлов,
 * [outputTemplate] — заготовка для вывода, в которой строчки расположены в нужном порядке, и затем,
 * в зависимости от конкретного формата, убирает какие-то строчки, [comparisonData] описан далее.
 */
data class ComparisonOutputData(
    val stringsDictionary: List<String>,
    val outputTemplate: MutableList<Line>,
    val comparisonData: ComparisonData
)

/*
 * Данные, необходимые для сравнения файлов. [file1] и [file2] хранятся в виде индексов соответствующих строк в
 * stringsDictionary
 */
data class ComparisonData(val file1: List<Line>, val file2: List<Line>)

/*
 * Обозначает строчку файла, хранит [stringIndex] — индекс строчки в общем словаре строк stringsDictionary и
 * [lineMarker] — роль строчки в изменяющей последовательности строки
 */
data class Line(val stringIndex: Int, var lineMarker: LineMarker = LineMarker.NONE)

/*
 * Обозначает статус строки согласно алгоритму LCS (COMMON — входит в LCS, DELETED — в первом файле и в LCS не входит и
 * была удалена, ADDED — аналогично DELETED, была добавлена)
 */
enum class LineMarker {
    NONE, COMMON, DELETED, ADDED
}

/*
 * Почти все режимы ввода предусматривают вывод каждого изменения в отдельном блоке (с контекстом или без).
 * [templateStart] — индекс начала блока в заготовке вывода outputTemplate, [file1Start] и [file2Start] либо номер
 * первой строчки в соответствующем файле, если она входит в блок, либо номер последней строчки в соответствующем файле
 * до начала блока, [length] — длина блока в строчках, [blockType] — специфика «нормального» режима вывода
 */
data class OutputBlock(
    val templateStart: Int,
    var file1Start: Int,
    var file2Start: Int,
    var length: Int,
    val blockType: BlockType = BlockType.DOESNT_MATTER
)

/*
 * Для «нормального» режима вывода необходимо различать блоки, где производится удаление (DELETE) и добавление (ADD).
 * Для остальных режимов вывода тип блока не имеет значения (DOESNT_MATTER)
 */
enum class BlockType {
    DOESNT_MATTER, ADD, DELETE
}

/*
 * В разных режимах вывода используются разные символы, чтобы показать, что очередная строка была удалена ([deletedPrefix]),
 * добавлена ([addedPrefix]), есть в обоих файлах ([commonPrefix])
 */
data class OutputStyle(val commonPrefix: String, val deletedPrefix: String, val addedPrefix: String)

/*
 * Вспомогательная структура для «объединённого» режима вывода
 */
data class UnifiedInternalBlock(var leftBound: Int = -1, var rightBound: Int = -1)

/*
 * Стили вывода для разных режимов
 */
val plusMinusStyle = OutputStyle("  ", "- ", "+ ")
val unifiedStyle = OutputStyle(" ", "-", "+")
val normalStyle = OutputStyle("  ", "< ", "> ")

/*
 * В случае ошибки вывести сообщение [exitMessage] и завершить программу с ненулевым кодом возврата.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    println("Use README.md or --help option for more information.")
    exitProcess(1)
}

/*
 * Показать краткую справку по использованию и завершить программу с нулевым кодом возврата.
 */
fun showHelpAndTerminate() {
    println("HELP: TODO()")
    exitProcess(0)
}

/*
 * Вспомогательная функция для splitIntoArgument, которая преобразует строчку из цифр [value], переданную программе в
 * командной строке, в число. [flag] — флаг, значение которого задавалось — необходим для вывода ошибки.
 * [isShortForm] — true, если флаг был в коротком формате (-u, -n) и false, если в полном (--help, --verbose). Т. к.
 * в полной форме (--unified=3) регулярное выражение захватывает и знак равно, его надо отрезать.
 */
fun getValueFromString(flag: String, value: String, isShortForm: Boolean): Int {
    if (value.length > 9) {
        terminateOnError("$flag value $value is too big.")
    }

    if (value.isEmpty()) {
        return NO_VALUE
    }

    if (isShortForm) {
        return value.toInt()
    }
    return value.drop(1).toInt()
}

/*
 * Вспомогательная функция для splitIntoArgument, которая преобразует строку с флагом [flag] в тип аргумента. Если
 * строка не соответствует ни одному типу аргумента, программа завершается с ошибкой.
 */
fun getArgumentTypeFromFlag(flag: String): ArgumentType {
    return when (flag) {
        ArgumentType.HELP.fullForm -> ArgumentType.HELP
        ArgumentType.PLAIN.shortForm, ArgumentType.PLAIN.fullForm -> ArgumentType.PLAIN
        ArgumentType.NORMAL.shortForm, ArgumentType.NORMAL.fullForm -> ArgumentType.NORMAL
        ArgumentType.UNIFIED.shortForm, ArgumentType.UNIFIED.fullForm -> ArgumentType.UNIFIED
        else -> {
            terminateOnError("There is no $flag argument.")
            ArgumentType.HELP
        }
    }
}

/*
 * Функция, которая разбирает список аргументов, переданных программе, [args], выделяет оттуда существующие аргументы
 * (а если есть несуществующие — завершает программу с ошибкой) и присваивает им значения. Если какой-то аргумент
 * введён несколько раз, то берётся последнее его введённое значение.
 */
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
        searchRegex = if (isShortForm) {
            Regex("([a-zA-Z])([0-9]*)")
        } else {
            Regex("([a-z]+)(=[0-9]+)?")
        }

        searchRegex.findAll(arg).forEach {
            val (flag, value) = it.destructured

            val argumentType = getArgumentTypeFromFlag(flag)
            val valueToInt = getValueFromString(flag, value, isShortForm)

            parsedArgs[argumentType] = if (valueToInt != NO_VALUE) valueToInt else argumentType.defaultValue
        }
    }

    return parsedArgs.toList().map { Argument(it.first, it.second) }
}

/*
 * Функция, которая берёт разобранные аргументы из splitIntoArguments и делает несколько проверок на совместимость
 * разных аргументов и на количество аргументов. Если всё в порядке, возвращает список аргументов.
 */
fun parseArguments(args: Array<String>): List<Argument> {
    val parsedArgs = splitIntoArguments(args)

    if (Argument(ArgumentType.HELP) in parsedArgs) {
        showHelpAndTerminate()
    }

    if (args.size < 2) {
        terminateOnError("Not enough arguments (required 2 paths to files; got ${args.size}).")
    }

    if (parsedArgs.size > 1) {
        terminateOnError("Conflicting output modes.")
    }

    return parsedArgs
}

/*
 * Создаёт и возвращает объект файла по адресу [pathToFile] и вместе с этим делает несколько проверок на
 * существование файла, на его тип, на право чтения и на длину файла.
 */
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
 * Считать содержимое файла из объекта [fileObject] и вернуть список строк файла в формате String.
 */
fun readFromFile(fileObject: File): List<String> {
    val inputStream: InputStream = fileObject.inputStream()
    val lineList = mutableListOf<String>()

    inputStream.bufferedReader().forEachLine {
        lineList.add(it)
        if (lineList.size > LINE_LIMIT) {
            terminateOnError("${fileObject.name} exceeds line limit ($LINE_LIMIT lines).")
        }
    }

    return lineList
}

/*
 * Преобразовать содержимое файлов в объект типа ComparisonOutputData, в котором содержится общий словарь строк,
 * содержимое файлов в формате Line (индекс строки в словаре и её положение в LCS) и заготовка для вывода, позже
 * заполняемая в функции produceOutputTemplate.
 */
fun stringsToLines(file1Strings: List<String>, file2Strings: List<String>): ComparisonOutputData {
    val stringsDictionary = mutableSetOf<String>()
    stringsDictionary.addAll(file1Strings)
    stringsDictionary.addAll(file2Strings)

    val file1Indices = file1Strings.map { Line(stringsDictionary.indexOf(it)) }
    val file2Indices = file2Strings.map { Line(stringsDictionary.indexOf(it)) }

    return ComparisonOutputData(
        stringsDictionary.toList(), MutableList(0) { Line(0, LineMarker.NONE) },
        ComparisonData(file1Indices, file2Indices)
    )
}

/*
 * Небольшая оптимизация алгоритма поиска LCS — заранее отмечает строчки, которых нет в другом файле, удалёнными или
 * добавленными. Ничего не возвращает, так как изменяет исходный объект.
 */
fun markNotCommonLines(comparisonOutputData: ComparisonOutputData) {
    val indicesCount = MutableList(2) { Array(comparisonOutputData.stringsDictionary.size) { 0 } }
    val fromCollections = arrayOf(comparisonOutputData.comparisonData.file1, comparisonOutputData.comparisonData.file2)

    fromCollections.forEachIndexed { index, it ->
        it.forEach { line ->
            indicesCount[index][line.stringIndex]++
        }
    }

    fromCollections.forEachIndexed { index, it ->
        it.forEach { line ->
            if (indicesCount[1 - index][line.stringIndex] == 0) {
                line.lineMarker = if (index == 0) LineMarker.DELETED else LineMarker.ADDED
            }
        }
    }
}

/*
 * Сравнивает два файла, содержащихся в [comparisonData]. Ничего не возвращает, так как изменяет исходный объект.
 */
fun compareTwoFiles(comparisonData: ComparisonData) {
    // Перед тем как сравнивать строчки, оставим только те, для которых ещё неизвестно, входят они в LCS или нет
    val file1 = comparisonData.file1.filter { it.lineMarker == LineMarker.NONE }
    val file2 = comparisonData.file2.filter { it.lineMarker == LineMarker.NONE }

    /*
     * LCS означает Longest Common Subsequence — наибольшую общую подпоследовательность
     * В массиве LCSMemoization хранятся значения НОП для всех возможных префиксов двух файлов,
     * а в массиве LCSReconstruction хранятся данные для восстановления самой LCS.
     */
    val LCSMemoization = MutableList(file1.size + 1) { MutableList(file2.size + 1) { 0 } }
    val LCSReconstruction = MutableList(file1.size + 1) { MutableList(file2.size + 1) { ReconstructionMarker.NONE } }

    /*
     * В массиве LCSReconstruction значение
     * REMOVE_FROM_LCS означает, что в точке [prefix1 + 1][prefix2 + 1] последние строки совпали и их выгодно было включить в НОП
     * LEFT означает, что значение НОП было лучше в точке [prefix1][prefix2 + 1]
     * UP означает, что значение НОП было лучше в точке [prefix1 + 1][prefix2]
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
    // Сначала отметим все строки из первого и второго файла как не входящие в LCS
    file1.forEach { it.lineMarker = LineMarker.DELETED }
    file2.forEach { it.lineMarker = LineMarker.ADDED }

    // В цикле отметим строки, на самом деле входящие в LCS
    var prefix1 = file1.size
    var prefix2 = file2.size
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

/*
 * Алгоритм, похожий на сортировку объединением, объединяет строки двух файлов нужном для вывода порядке (общие строки идут
 * по порядку, если есть удаление и добавление в одной точке LCS, то сначала выводится удаление, а затем добавление).
 * Ничего не возвращает, так как изменяет исходный объект.
 */
fun produceOutputTemplate(comparisonOutputData: ComparisonOutputData) {
    val file1 = comparisonOutputData.comparisonData.file1
    val file2 = comparisonOutputData.comparisonData.file2
    var pointer1 = 0
    var pointer2 = 0

    while (pointer1 < file1.size || pointer2 < file2.size) {
        if (pointer2 >= file2.size || (pointer1 < file1.size && file1[pointer1].lineMarker != LineMarker.COMMON)) {
            comparisonOutputData.outputTemplate.add(file1[pointer1])
            pointer1++
        } else if (pointer1 >= file1.size || file2[pointer2].lineMarker != LineMarker.COMMON) {
            comparisonOutputData.outputTemplate.add(file2[pointer2])
            pointer2++
        } else {
            comparisonOutputData.outputTemplate.add(file1[pointer1])
            pointer1++; pointer2++
        }
    }
}

/*
 * Вывести на печать какой-то блок [block] из заготовки для вывода [outputTemplate]. Так как в outputTemplate хранятся
 * лишь индексы строк, нужен общий словарь [stringsDictionary], где хранятся сами строки файлов. Для разных
 * режимов вывода нужны разные символы, показывающие статус строки — эта информация хранится в [style].
 */
fun printBlock(stringsDictionary: List<String>, outputTemplate: List<Line>, block: OutputBlock, style: OutputStyle) {
    outputTemplate.slice(block.templateStart until block.templateStart + block.length).forEach { line ->
        when (line.lineMarker) {
            LineMarker.COMMON -> println("${style.commonPrefix}${stringsDictionary[line.stringIndex]}")
            LineMarker.DELETED -> println("${style.deletedPrefix}${stringsDictionary[line.stringIndex]}")
            LineMarker.ADDED -> println("${style.addedPrefix}${stringsDictionary[line.stringIndex]}")
        }
    }
}

/*
 * Выводит объединение двух файлов [outputTemplate]. Так как в outputTemplate хранятся лишь индексы строк,
 * нужен общий словарь [stringsDictionary], где хранятся сами строки файлов.
 */
fun plainOutput(stringsDictionary: List<String>, outputTemplate: List<Line>) {
    printBlock(stringsDictionary, outputTemplate, OutputBlock(0, 0, 0, outputTemplate.size), plusMinusStyle)
}

/*
 * Генерирует и возвращает список блоков, пригодных для «нормального» формата вывода. Блоки формируются на основе
 * [outputTemplate].
 */
fun getNormalBlocks(outputTemplate: List<Line>): List<OutputBlock> {
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
                    LineMarker.DELETED -> blocks.add(
                        blocks.size, OutputBlock(
                            i, commonLinesCnt + deletedLinesCnt,
                            commonLinesCnt + addedLinesCnt, 0, BlockType.DELETE
                        )
                    )
                    LineMarker.ADDED -> blocks.add(
                        blocks.size, OutputBlock(
                            i, commonLinesCnt + deletedLinesCnt,
                            commonLinesCnt + addedLinesCnt, 0, BlockType.ADD
                        )
                    )
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
 * «Нормальный» формат вывода (используется по умолчанию в версии diff для Linux)
 * Изменённые блоки выводятся без контекста вокруг; удаленные строки отмечаются знаком
 * < в начале строки, добавленные — знаком >. Начало каждого блока предваряет описание изменений в формате f1r|op|f2r (в
 * выводе без |), где op — характер операции (a — добавление, d — удаление, c — изменение), f1r описывает область в
 * первом файле, откуда были удалены или куда были бы вставлены строчки из второго файла, если бы их вставили в первый.
 * Аналогично, f2r описывает область во втором файле, куда были добавлены или где были бы строчки из первого файла,
 * если бы их не удалили.
 * Статус каждой строчки хранится в [outputTemplate]. Так как в outputTemplate хранятся лишь индексы строк, нужен общий
 * словарь [stringsDictionary], где хранятся сами строки файлов.
 */
fun normalOutput(stringsDictionary: List<String>, outputTemplate: List<Line>) {
    val blocks = getNormalBlocks(outputTemplate)
    var skipThisBlock = false

    for (i in blocks.indices) {
        if (skipThisBlock) {
            skipThisBlock = false
            continue
        }

        if (i != blocks.lastIndex && blocks[i].blockType == BlockType.DELETE && blocks[i + 1].blockType == BlockType.ADD &&
            blocks[i].templateStart + blocks[i].length == blocks[i + 1].templateStart
        ) {
            println(
                "${blocks[i].file1Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file1Start + blocks[i].length}"}" +
                        "c" +
                        "${blocks[i + 1].file2Start + 1}${if (blocks[i + 1].length == 1) "" else ",${blocks[i + 1].file2Start + blocks[i + 1].length}"}"
            )
            printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
            println("---")
            printBlock(stringsDictionary, outputTemplate, blocks[i + 1], normalStyle)
            skipThisBlock = true
        } else if (blocks[i].blockType == BlockType.DELETE) {
            println(
                "${blocks[i].file1Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file1Start + blocks[i].length}"}" +
                        "d" +
                        "${blocks[i].file2Start}"
            )
            printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
        } else {
            println(
                "${blocks[i].file1Start}" +
                        "a" +
                        "${blocks[i].file2Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file2Start + blocks[i].length}"}"
            )
            printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
        }
    }
}

/*
 * Генерирует и возвращает список блоков, пригодных для «объединённого» формата вывода. Блоки формируются на основе
 * [outputTemplate]. Около каждого блока изменений должно быть не более [contextLines] строк контекста.
 */
fun getUnifiedBlocks(outputTemplate: List<Line>, contextLines: Int): List<OutputBlock> {
    val blocksAllBounds = mutableListOf<Int>()

    // Сначала мы находим индексы всех изменённых блоков и добавляем к ним строки контекста.
    for (i in outputTemplate.indices) {
        if ((i == 0 && outputTemplate[i].lineMarker != LineMarker.COMMON) ||
            (i != 0 && outputTemplate[i - 1].lineMarker == LineMarker.COMMON && outputTemplate[i].lineMarker != LineMarker.COMMON)
        ) {
            blocksAllBounds.add(max(0, i - contextLines))
        } else if (i != 0 && outputTemplate[i - 1].lineMarker != LineMarker.COMMON && outputTemplate[i].lineMarker == LineMarker.COMMON) {
            blocksAllBounds.add(min(outputTemplate.lastIndex, i - 1 + contextLines))
        }
    }
    if (outputTemplate.last().lineMarker != LineMarker.COMMON) {
        blocksAllBounds.add(outputTemplate.lastIndex)
    }

    // Затем необходимо объединить пересекающиеся блоки.
    val blocksUnifiedBounds = mutableListOf<UnifiedInternalBlock>()
    blocksUnifiedBounds.add(UnifiedInternalBlock(blocksAllBounds[0]))
    for (i in 1 until blocksAllBounds.lastIndex step 2) {
        if (blocksAllBounds[i + 1] - blocksAllBounds[i] > 1) {
            blocksUnifiedBounds.last().rightBound = blocksAllBounds[i]
            blocksUnifiedBounds.add(UnifiedInternalBlock(blocksAllBounds[i + 1]))
        }
    }
    blocksUnifiedBounds.last().rightBound = blocksAllBounds.last()

    // После этого надо преобразовать блоки в формате UnifiedInternalBlock в формат OutputBlock, добавив определённую
    // информацию.
    val blocksResult = mutableListOf<OutputBlock>()
    var commonLinesCnt = 0
    var deletedLinesCnt = 0
    var addedLinesCnt = 0
    var blockPointer = 0

    for (i in outputTemplate.indices) {
        if (blockPointer <= blocksUnifiedBounds.lastIndex && i in blocksUnifiedBounds[blockPointer].leftBound..blocksUnifiedBounds[blockPointer].rightBound) {
            if (i == blocksUnifiedBounds[blockPointer].leftBound) {
                val file1Start =
                    if (i == 0 && outputTemplate[i].lineMarker == LineMarker.ADDED) -1 else commonLinesCnt + deletedLinesCnt
                val file2Start =
                    if (i == 0 && outputTemplate[i].lineMarker == LineMarker.DELETED) -1 else commonLinesCnt + addedLinesCnt
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

/*
 * Вспомогательная функция для «объединённого» формата вывода. Преобразует время в формате UNIX [epochValue] (количество
 * миллисекунд, прошедших с 1 января 1970 года) в читаемый вид. Использует установленный на компьютере часовой пояс.
 */
fun convertEpochToReadableTime(epochValue: Long): OffsetDateTime {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochValue), ZoneId.systemDefault())
}

/*
 * Вспомогательная функция для «объединённого» формата вывода. В блоке [block] считает количество строк, которые находятся
 * в одном из файлов. Ясно, что, например, для первого файла это будет количество общих строк плюс количество
 * удалённых строк и аналогично для второго файла.
 * [outputTemplate] нужен так как блоки хранят лишь индексы, но не сами строки. [ignoredLineMarker] позволяет определить
 * какой тип строк надо игнорировать (например, для первого файла это будут добавленные строки)
 */
fun getRelativeBlockLength(block: OutputBlock, outputTemplate: List<Line>, ignoredLineMarker: LineMarker): Int {
    val blockRange = block.templateStart until block.templateStart + block.length
    return block.length - outputTemplate.slice(blockRange).count { it.lineMarker == ignoredLineMarker }
}

/*
 * «Объединённый» формат вывода (используется, например, в Github)
 * Сначала выводятся имена сравниваемых файлов и время последнего изменения этих файлов (для этого нужны объекты файлов
 * [file1Object] и [file2Object]). Затем выводятся блоки изменений с контекстом [contextLines] строк около каждого
 * блока (по умолчанию три строки). Если блоки с контекстом пересекаются, то они объединяются в один блок. Каждый блок
 * предваряется заголовком в формате "@@ -s1,l1 +s2,l2 @@" (на выводе без кавычек), где s1 — начало блока относительно
 * первого файла, l1 — количество строк в блоке, содержащихся в первом файле. Аналогично s2 и l2 определяются для второго
 * файла.
 * Статус каждой строчки хранится в [outputTemplate]. Так как в outputTemplate хранятся лишь индексы строк, нужен общий
 * словарь [stringsDictionary], где хранятся сами строки файлов.
 */
fun unifiedOutput(
    stringsDictionary: List<String>,
    outputTemplate: List<Line>,
    file1Object: File,
    file2Object: File,
    contextLines: Int = 3
) {
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

/*
 * На основе аргументов [parsedArgs], переданных программе, осуществляет вывод в нужном формате. [comparisonOutputData]
 * нужен для вывода, объекты файлов [file1Object] и [file2Object] нужны для «объединённого» формата вывода.
 */
fun output(
    comparisonOutputData: ComparisonOutputData,
    file1Object: File,
    file2Object: File,
    parsedArgs: List<Argument>
) {
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

/*
 * Главная функция программы. Объединяет все функции в один алгоритм.
 */
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