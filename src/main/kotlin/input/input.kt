package com.dodolfin.diff.input

import java.io.File
import java.io.InputStream
import kotlin.system.exitProcess

/**
 * Maximum size of file in bytes
 */
const val SIZE_LIMIT = 10 * 1024 * 1024

/**
 * Maximum size of file in lines
 */
const val LINE_LIMIT = 10000

/**
 * Special value used for handling command line arguments. Means that option doesn't have an argument value or didn't
 * receive it
 */
const val NO_VALUE = Int.MIN_VALUE

/**
 * Stores command-line option (option is like "-s 1200" in some command-line linux utility).
 */
data class Argument(val argumentType: ArgumentType, val argumentValue: Int = NO_VALUE)

/**
 * All possible command-line options. [shortForm] is a short name of an option (one character) preceded by hyphen sign,
 * followed (without a space delimiter) by a numeric value, allows multiple option form (e.g. -a0b1)
 * [fullForm] is a long name of an option preceded by two hyphen signs, followed by a numeric value with equals sign as
 * a delimiter (e.g. --unified=4)
 */
enum class ArgumentType(val shortForm: String, val fullForm: String, val defaultValue: Int = NO_VALUE) {
    UNIFIED("u", "unified", 3),
    NORMAL("n", "normal"),
    PLAIN("p", "plain"),
    HELP("", "help")
}

/**
 * In case of some error print [exitMessage], terminate the program and return non-zero value.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    println("Use README.md or --help option for more information.")
    exitProcess(1)
}

/**
 * Show usage and options description and terminate the program with zero return value.
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
 * splitIntoArgument service function. Transforms numeric string [value], received as a command-line argument, to
 * a number. [flag] is the name of the option which value was ser. We use it to display error message.
 * [isShortForm] is true if flag is in shortForm (-u, -n) and false otherwise (--help, --verbose). My regex catches
 * = sign with the flag, so [isShortForm] is used to decide if we should drop the last character.
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
 * splitIntoArgument service function. Transforms string with option by the name of [flag] into option type. If there
 * is no such option, the program displays the error message and terminates.
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
 * Transforms raw [args] into list of parsed Arguments with values. If [args] contain an argument which doesn't exist,
 * the program terminates with an error message. If an option appears multiple times in [args], only the last value (or default
 * value, if last value is empty) is used.
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
 * Checks [args] size and arguments received from [splitIntoArguments] for compatibility. If something is wrong,
 * terminates the program with an error message. Otherwise, returns the list of arguments.
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
 * Creates file object for file at [pathToFile], checks if it exists, is normal file, is readable and its size is in
 * limits. If something is wrong, terminates the program with an error message. Otherwise, returns the file object.
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
 * Returns contents of file at [fileObject] in the form of lines list.
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