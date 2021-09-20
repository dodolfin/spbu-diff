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

/*
 * Вспомогательная структура для «объединённого» режима вывода
 */
data class UnifiedInternalBlock(var leftBound: Int = -1, var rightBound: Int = -1)

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