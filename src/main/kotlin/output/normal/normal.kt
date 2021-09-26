package com.dodolfin.diff.output.normal

import com.dodolfin.diff.compare.Line
import com.dodolfin.diff.compare.LineMarker
import com.dodolfin.diff.output.BlockType
import com.dodolfin.diff.output.OutputBlock
import com.dodolfin.diff.output.normalStyle
import com.dodolfin.diff.output.printBlock

/**
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