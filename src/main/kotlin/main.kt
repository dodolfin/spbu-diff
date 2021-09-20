package com.dodolfin.diff

import com.dodolfin.diff.input.*
import com.dodolfin.diff.compare.*
import com.dodolfin.diff.output.*
import kotlin.system.exitProcess
import kotlin.math.*
import kotlin.text.Regex
import java.io.File
import java.io.InputStream
import java.time.*

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