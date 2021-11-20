package com.dodolfin.diff.output.unified

import com.dodolfin.diff.compare.Line
import com.dodolfin.diff.compare.LineMarker
import com.dodolfin.diff.output.OutputBlock
import com.dodolfin.diff.output.printBlock
import com.dodolfin.diff.output.unifiedStyle
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Supporting data class for the „unified“ output format.
 */
data class UnifiedInternalBlock(var leftBound: Int = -1, var rightBound: Int = -1)

/**
 * Transforms [outputTemplate] to the list of blocks suitable for „unified“ output format. As much as possible, but
 * no more than [contextLines] lines should be around each addition/deletion/change block.
 */
fun getUnifiedBlocks(outputTemplate: List<Line>, contextLines: Int): List<OutputBlock> {
    val blocksAllBounds = mutableListOf<Int>()

    // First we calculate indexes of all changed blocks and add context lines around them.
    for (i in outputTemplate.indices) {
        when {
            ((i == 0 && outputTemplate[i].lineMarker != LineMarker.COMMON) ||
                    (i != 0 && outputTemplate[i - 1].lineMarker == LineMarker.COMMON && outputTemplate[i].lineMarker != LineMarker.COMMON)) -> {
                blocksAllBounds.add(max(0, i - contextLines))
            }
            i != 0 && outputTemplate[i - 1].lineMarker != LineMarker.COMMON && outputTemplate[i].lineMarker == LineMarker.COMMON -> {
                blocksAllBounds.add(min(outputTemplate.lastIndex, i - 1 + contextLines))
            }
        }
    }
    if (outputTemplate.last().lineMarker != LineMarker.COMMON) {
        blocksAllBounds.add(outputTemplate.lastIndex)
    }

    // Then we need to remove duplicate lines in order to properly merge overlapping blocks.
    val blocksUnifiedBounds = mutableListOf<UnifiedInternalBlock>()
    blocksUnifiedBounds.add(UnifiedInternalBlock(blocksAllBounds[0]))
    for (i in 1 until blocksAllBounds.lastIndex step 2) {
        if (blocksAllBounds[i + 1] - blocksAllBounds[i] > 1) {
            blocksUnifiedBounds.last().rightBound = blocksAllBounds[i]
            blocksUnifiedBounds.add(UnifiedInternalBlock(blocksAllBounds[i + 1]))
        }
    }
    blocksUnifiedBounds.last().rightBound = blocksAllBounds.last()

    // After that we need to add some information to transform UnifiedInternalBlock into OutputBlock.
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

/**
 * „unified“ output service function. Transforms UNIX epoch time [epochValue] (the number of milliseconds passed since
 * the 1st January of 1970) into readable format. Uses this computer time zone.
 */
fun convertEpochToReadableTime(epochValue: Long): OffsetDateTime {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochValue), ZoneId.systemDefault())
}

/**
 * „unified“ output service function. Counts a number of lines in [block] which are present in one of the files.
 * E. g. for first file it's the number of common lines in [block] plus the number of deleted lines.
 * [outputTemplate] is needed to distinguish lines (common/deleted/added). [ignoredLineMarker] tells which
 * type of lines is to be ignored (e. g. added lines for the first file)
 */
fun getRelativeBlockLength(block: OutputBlock, outputTemplate: List<Line>, ignoredLineMarker: LineMarker): Int {
    val blockRange = block.templateStart until block.templateStart + block.length
    return block.length - outputTemplate.slice(blockRange).count { it.lineMarker == ignoredLineMarker }
}

/**
 * „Unified“ output format (used in GitHub)
 * First two lines are the names of compared files [file1Object], [file2Object] and the time of last modification of each
 * file. Then added/deleted/changed blocks are printed. Each block contains as much as possible, but
 * no more than [contextLines] context lines around it (those are common lines, they are not printed in „normal“ mode).
 * If blocks are overlapping, they are printed merged. Each block is preceded by line describing changes in the following
 * format: "@@ -s1,l1 +s2,l2 @@" (actual output is without double quotes), where s1 is the beginning of the block in first
 * file relative terms, l1 is the number of lines in the block which are present in the first file. s2 and l2 are defined
 * similar.
 * Information about lines status (common, added or deleted) is stored [outputTemplate].
 * Since [outputTemplate] only stores indexes of lines, we need [stringsDictionary] to print strings themselves.
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