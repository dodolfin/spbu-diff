package com.dodolfin.diff.compare

import com.dodolfin.diff.output.ComparisonOutputData

/**
 * Used for answer reconstruction in LCS finding algorithm
 */
enum class ReconstructionMarker {
    NONE, REMOVE_FROM_LCS, LEFT, UP
}

/**
 * Stores data needed to compare files. [file1] and [file2] are stored as a list of sequence of corresponding strings in
 * stringsDictionary
 */
data class ComparisonData(val file1: List<Line>, val file2: List<Line>) {
    /**
     * Speeds up LCS finding algorithm a little by marking lines present only in one file as deleted or added.
     */
    fun markNotCommonLines() {
        val indicesCount = MutableList(2) { MutableList(this.file1.size + this.file2.size) { 0 } }
        val fromCollections = arrayOf(this.file1, this.file2)

        fromCollections.forEachIndexed { index, it ->
            it.forEach { line ->
                indicesCount[index][line.stringIndex]++
            }
        }

        fromCollections.forEachIndexed { index, it ->
            it.forEach { line ->
                if (indicesCount[index][line.stringIndex] != 0 && indicesCount[1 - index][line.stringIndex] == 0) {
                    line.lineMarker = if (index == 0) LineMarker.DELETED else LineMarker.ADDED
                }
            }
        }
    }


    /**
     * Compares two files that are stored in this object.
     */
    fun compareTwoFiles() {
        // Before comparing we filter out lines which are already marked as common, added or deleted
        val file1 = this.file1.filter { it.lineMarker == LineMarker.NONE }
        val file2 = this.file2.filter { it.lineMarker == LineMarker.NONE }

        /*
         * LCS stands for Longest Common Subsequence
         * LCSMemoization stores LCS values for every possible combination of two files prefixes,
         * and LCSReconstruction stores data for reconstructing the subsequence itself.
         */
        val LCSMemoization = MutableList(file1.size + 1) { MutableList(file2.size + 1) { 0 } }
        val LCSReconstruction =
            MutableList(file1.size + 1) { MutableList(file2.size + 1) { ReconstructionMarker.NONE } }

        /*
         * LCSReconstruction values explanation:
         * REMOVE_FROM_LCS means that last character is same in the point [prefix1 + 1][prefix2 + 1] and we should
         *                 add it to our LCS
         * LEFT            means that the length of LCS was greater in the point [prefix1][prefix2 + 1]
         * UP              means that the length of LCS was greater in the point [prefix1 + 1][prefix2]
         */
        for (prefix1 in file1.indices) {
            for (prefix2 in file2.indices) {
                when {
                    file1[prefix1] == file2[prefix2] -> {
                        LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1][prefix2] + 1
                        LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.REMOVE_FROM_LCS
                    }
                    LCSMemoization[prefix1][prefix2 + 1] > LCSMemoization[prefix1 + 1][prefix2] -> {
                        LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1][prefix2 + 1]
                        LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.LEFT
                    }
                    else -> {
                        LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1 + 1][prefix2]
                        LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.UP
                    }
                }
            }
        }

        // Answer reconstruction
        // First we assume that LCS is empty by marking all lines as either deleted or added
        file1.forEach { it.lineMarker = LineMarker.DELETED }
        file2.forEach { it.lineMarker = LineMarker.ADDED }

        // In the following cycle we actually mark LCS lines as common
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
}

/**
 * Stores line of the file. [stringIndex] is a number of line in stringsDictionary.
 * [lineMarker] shows if that line was added, deleted or is common
 */
data class Line(val stringIndex: Int, var lineMarker: LineMarker = LineMarker.NONE)

/**
 * Stores line status (COMMON: line is in LCS, DELETED: line is in the first file and not in LCS, was delete,
 * ADDED: DELETED, but vice versa)
 */
enum class LineMarker {
    NONE, COMMON, DELETED, ADDED
}

/**
 * Transform file 1 and file 2 contents to the ComparisonOutputData instance.
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