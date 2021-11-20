package com.dodolfin.diff.output

import com.dodolfin.diff.compare.ComparisonData
import com.dodolfin.diff.compare.Line
import com.dodolfin.diff.compare.LineMarker
import com.dodolfin.diff.input.Argument
import com.dodolfin.diff.input.ArgumentType
import com.dodolfin.diff.output.normal.normalOutput
import com.dodolfin.diff.output.unified.unifiedOutput
import java.io.File

/**
 * Data required for comparing files and pretty-printing the results of comparing. [stringsDictionary] stores all
 * lines from both files. [outputTemplate] stores a „merge“ of two files (described below), [comparisonData] is described in
 * compare.kt.
 */
data class ComparisonOutputData(
    val stringsDictionary: List<String>,
    val outputTemplate: MutableList<Line>,
    val comparisonData: ComparisonData
) {
    /**
     * Merges two files in order suitable for printing the results of comparing. Common lines order is same as in original files,
     * in changed (with both addition and deletion) blocks deleted lines go before added.
     */
    fun produceOutputTemplate() {
        val file1 = this.comparisonData.file1
        val file2 = this.comparisonData.file2
        var pointer1 = 0
        var pointer2 = 0

        while (pointer1 < file1.size || pointer2 < file2.size) {
            when {
                pointer2 >= file2.size || (pointer1 < file1.size && file1[pointer1].lineMarker != LineMarker.COMMON) -> {
                    this.outputTemplate.add(file1[pointer1])
                    pointer1++
                }
                pointer1 >= file1.size || file2[pointer2].lineMarker != LineMarker.COMMON -> {
                    this.outputTemplate.add(file2[pointer2])
                    pointer2++
                }
                else -> {
                    this.outputTemplate.add(file1[pointer1])
                    pointer1++; pointer2++
                }
            }
        }
    }

    /**
     * Compares files and produces outputTemplate. Made for convenient work with comparisonOutputData.
     */
    fun comparisonAndOutputTemplate() {
        this.comparisonData.markNotCommonLines()
        this.comparisonData.compareTwoFiles()
        this.produceOutputTemplate()
    }
}

/**
 * Almost all output formats imply printing of changes in separate blocks (with or without context lines), hence
 * this data class was created.
 * [templateStart] is the index of the block start in outputTemplate, [file1Start] and [file2Start] are either
 * indexes of the first line in the corresponding file, if it's in the block, either the index of the most bottom line
 * in the corresponding file before the beginning of the block, [length] is size of block in lines,
 * [blockType] is used only for „normal“ output format
 */
data class OutputBlock(
    val templateStart: Int,
    var file1Start: Int,
    var file2Start: Int,
    var length: Int,
    val blockType: BlockType = BlockType.DOESNT_MATTER
)

/**
 * We should distinguish deletion and addition blocks when in „normal“ output format. For all other output
 * formats the type of block doesn't matter (DOESNT_MATTER).
 */
enum class BlockType {
    DOESNT_MATTER, ADD, DELETE
}

/**
 * In different output formats we use different characters to precede deleted ([deletedPrefix]), added ([addedPrefix])
 * or common ([commonPrefix]) line.
 */
data class OutputStyle(val commonPrefix: String, val deletedPrefix: String, val addedPrefix: String)

/**
 * Output styles for different output formats
 */
val plusMinusStyle = OutputStyle("  ", "- ", "+ ")
val unifiedStyle = OutputStyle(" ", "-", "+")
val normalStyle = OutputStyle("  ", "< ", "> ")

/**
 * Print a block [block] from [outputTemplate]. Since [outputTemplate] only stores indexes of lines,
 * we need [stringsDictionary] to print strings themselves. [style] stores characters needed to display status of string
 * in different output formats.
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

/**
 * Simply prints whole [outputTemplate]. Since [outputTemplate] only stores indexes of lines,
 * we need [stringsDictionary] to print strings themselves.
 */
fun plainOutput(stringsDictionary: List<String>, outputTemplate: List<Line>) {
    printBlock(stringsDictionary, outputTemplate, OutputBlock(0, 0, 0, outputTemplate.size), plusMinusStyle)
}

/**
 * Chooses appropriate (using [parsedArgs]) output format and performs output. We need [comparisonOutputData]
 * for the output itself, [file1Object] and [file2Object] are used in „unified“ output format.
 */
fun output(
    comparisonOutputData: ComparisonOutputData,
    file1Object: File,
    file2Object: File,
    parsedArgs: List<Argument>
) {
    if (comparisonOutputData.outputTemplate.all { it.lineMarker == LineMarker.COMMON }) {
        return
    }

    if (parsedArgs.isEmpty()) {
        normalOutput(comparisonOutputData.stringsDictionary, comparisonOutputData.outputTemplate)
        return
    }

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
