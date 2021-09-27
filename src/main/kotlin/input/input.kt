package com.dodolfin.diff.input

import java.io.File
import java.io.InputStream
import kotlin.system.exitProcess

/**
 * [SIZE_LIMIT] — ограничение на размер файла в байтах
 */
const val SIZE_LIMIT = 10 * 1024 * 1024

/**
 * [LINE_LIMIT] — ограничение на размер файла в строчках
 */
const val LINE_LIMIT = 10000

/**
 * [NO_VALUE] — cпециальное значение, которое означает, что у аргумента не предусмотрено значения или оно не было введено
 */
const val NO_VALUE = Int.MIN_VALUE

/**
 * Обозначает опцию программы, передающуюся в командной строке при запуске программы. [argumentType] — сам аргумент,
 * [argumentValue] — значение аргумента (если не предусмотрено, хранится NO_VALUE)
 */
data class Argument(val argumentType: ArgumentType, val argumentValue: Int = NO_VALUE)

/**
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

/**
 * В случае ошибки вывести сообщение [exitMessage] и завершить программу с ненулевым кодом возврата.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    println("Use README.md or --help option for more information.")
    exitProcess(1)
}

/**
 * Показать краткую справку по использованию и завершить программу с нулевым кодом возврата.
 */
fun showHelpAndTerminate() {
    println(
        """Usage: java -jar diff.jar [OPTION] FILE1 FILE2
        | 
        |Options:""".trimMargin("|")
    )
    println("  -u[CONTEXT_LINES], --unified=[CONTEXT_LINES]\tUnified output format with [CONTEXT_LINES] context lines (default 3)")
    println("  -n, --normal\tNormal output format")
    println("  -p, --plain\tPlain output format (print both files)")
    println("  --help\tShow this message and exit")
    exitProcess(0)
}

/**
 * Вспомогательная функция для splitIntoArgument, которая преобразует строчку из цифр [value], переданную программе в
 * командной строке, в число. [flag] — флаг, значение которого задавалось — необходим для вывода ошибки.
 * [isShortForm] — true, если флаг был в коротком формате (-u, -n) и false, если в полном (--help, --verbose). Т. к.
 * в полной форме (--unified=3) регулярное выражение захватывает и знак равно, его надо отрезать.
 */
fun getValueFromString(flag: String, value: String, isShortForm: Boolean): Int {
    if (value.length - (if (!isShortForm) 1 else 0) > 9) {
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

/**
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

/**
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

/**
 * Функция, которая берёт разобранные аргументы из splitIntoArguments и делает несколько проверок на совместимость
 * разных аргументов и на количество аргументов. Если всё в порядке, возвращает список аргументов.
 */
fun parseArguments(args: Array<String>): List<Argument> {
    val parsedArgs = splitIntoArguments(args)

    if ("--help" in args) {
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

/**
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

/**
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