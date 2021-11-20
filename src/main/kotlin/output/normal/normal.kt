package com.dodolfin.diff.output.normal

import com.dodolfin.diff.compare.Line
import com.dodolfin.diff.compare.LineMarker
import com.dodolfin.diff.output.BlockType
import com.dodolfin.diff.output.OutputBlock
import com.dodolfin.diff.output.normalStyle
import com.dodolfin.diff.output.printBlock

/**
 * Returns „normal“ output format blocks generated from [outputTemplate].
 */
fun getNormalBlocks(outputTemplate: List<Line>): List<OutputBlock> {
    val blocks = mutableListOf<OutputBlock>()
    var commonLinesCnt = 0
    var deletedLinesCnt = 0
    var addedLinesCnt = 0

    for (i in outputTemplate.indices) {
        if (outputTemplate[i].lineMarker == LineMarker.COMMON) {
            commonLinesCnt++
            continue
        }

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

    return blocks
}

/**
 * «Normal» output format (default output format in diff utility for Linux)
 * Changed blocks are displayed without context around them; deleted and added lines are preceded with < and > signs correspondingly.
 * Each block is preceded by line which describes changes in the following format:
 * f1r|op|f2r (without | in actual output), where op is the type of operation (a for addition, d for deletion, c for changing),
 * f1r describes a range in the first file from where lines were deleted or where would lines from second file appear
 * if we would insert them in the first file. f2r describes the same rang for second file.
 * Information about lines status (common, added or deleted) is stored [outputTemplate].
 * Since [outputTemplate] only stores indexes of lines, we need [stringsDictionary] to print strings themselves.
 */
fun normalOutput(stringsDictionary: List<String>, outputTemplate: List<Line>) {
    val blocks = getNormalBlocks(outputTemplate)
    var skipThisBlock = false

    for (i in blocks.indices) {
        if (skipThisBlock) {
            skipThisBlock = false
            continue
        }

        when {
            (i != blocks.lastIndex && blocks[i].blockType == BlockType.DELETE && blocks[i + 1].blockType == BlockType.ADD &&
                    blocks[i].templateStart + blocks[i].length == blocks[i + 1].templateStart) -> {
                println(
                    "${blocks[i].file1Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file1Start + blocks[i].length}"}" +
                            "c" +
                            "${blocks[i + 1].file2Start + 1}${if (blocks[i + 1].length == 1) "" else ",${blocks[i + 1].file2Start + blocks[i + 1].length}"}"
                )
                printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
                println("---")
                printBlock(stringsDictionary, outputTemplate, blocks[i + 1], normalStyle)
                skipThisBlock = true
            }
            blocks[i].blockType == BlockType.DELETE -> {
                println(
                    "${blocks[i].file1Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file1Start + blocks[i].length}"}" +
                            "d" +
                            "${blocks[i].file2Start}"
                )
                printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
            }
            else -> {
                println(
                    "${blocks[i].file1Start}" +
                            "a" +
                            "${blocks[i].file2Start + 1}${if (blocks[i].length == 1) "" else ",${blocks[i].file2Start + blocks[i].length}"}"
                )
                printBlock(stringsDictionary, outputTemplate, blocks[i], normalStyle)
            }
        }
    }
}