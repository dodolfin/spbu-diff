package com.dodolfin.diff

import com.dodolfin.diff.compare.stringsToLines
import com.dodolfin.diff.input.openFile
import com.dodolfin.diff.input.parseArguments
import com.dodolfin.diff.input.readFromFile
import com.dodolfin.diff.output.output

/**
 * Main function.
 */
fun main(args: Array<String>) {
    val parsedArgs = parseArguments(args)

    val file1Object = openFile(args[args.size - 2])
    val file2Object = openFile(args[args.size - 1])

    val file1Strings = readFromFile(file1Object)
    val file2Strings = readFromFile(file2Object)
    val comparisonOutputData = stringsToLines(file1Strings, file2Strings)
    comparisonOutputData.comparisonAndOutputTemplate()

    output(comparisonOutputData, file1Object, file2Object, parsedArgs)
}