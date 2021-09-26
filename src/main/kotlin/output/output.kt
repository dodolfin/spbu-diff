package com.dodolfin.diff.output

import com.dodolfin.diff.compare.ComparisonData
import com.dodolfin.diff.compare.Line
import com.dodolfin.diff.compare.LineMarker
import com.dodolfin.diff.input.Argument
import com.dodolfin.diff.input.ArgumentType
import com.dodolfin.diff.output.unified.*
import com.dodolfin.diff.output.normal.*
import java.io.File

/**
 * Данные, необходимые для сравнения и вывода. [stringsDictionary] — общий словарь строчек из двух файлов,
 * [outputTemplate] — заготовка для вывода, в которой строчки расположены в нужном порядке, и затем,
 * в зависимости от конкретного формата, убирает какие-то строчки, [comparisonData] описан далее.
 */
data class ComparisonOutputData(
    val stringsDictionary: List<String>,
    val outputTemplate: MutableList<Line>,
    val comparisonData: ComparisonData
) {
    /**
     * Алгоритм, похожий на сортировку объединением, объединяет строки двух файлов нужном для вывода порядке (общие строки идут
     * по порядку, если есть удаление и добавление в одной точке LCS, то сначала выводится удаление, а затем добавление).
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
     * Сравнивает файлы и делает заготовку для вывода. Сделано для удобства работы с comparisonOutputData.
     */
    fun comparisonAndOutputTemplate() {
        this.comparisonData.markNotCommonLines()
        this.comparisonData.compareTwoFiles()
        this.produceOutputTemplate()
    }
}

/**
 * Почти все режимы ввода предусматривают вывод каждого изменения в отдельном блоке (с контекстом или без).
 * [templateStart] — индекс начала блока в заготовке вывода outputTemplate, [file1Start] и [file2Start] либо номер
 * первой строчки в соответствующем файле, если она входит в блок, либо номер последней строчки в соответствующем файле
 * до начала блока, [length] — длина блока в строчках, [blockType] — специфика «нормального» режима вывода
 */
data class OutputBlock(
    val templateStart: Int,
    var file1Start: Int,
    var file2Start: Int,
    var length: Int,
    val blockType: BlockType = BlockType.DOESNT_MATTER
)

/**
 * Для «нормального» режима вывода необходимо различать блоки, где производится удаление (DELETE) и добавление (ADD).
 * Для остальных режимов вывода тип блока не имеет значения (DOESNT_MATTER)
 */
enum class BlockType {
    DOESNT_MATTER, ADD, DELETE
}

/**
 * В разных режимах вывода используются разные символы, чтобы показать, что очередная строка была удалена ([deletedPrefix]),
 * добавлена ([addedPrefix]), есть в обоих файлах ([commonPrefix])
 */
data class OutputStyle(val commonPrefix: String, val deletedPrefix: String, val addedPrefix: String)

/**
 * Стили вывода для разных режимов
 */
val plusMinusStyle = OutputStyle("  ", "- ", "+ ")
val unifiedStyle = OutputStyle(" ", "-", "+")
val normalStyle = OutputStyle("  ", "< ", "> ")

/**
 * Вывести на печать какой-то блок [block] из заготовки для вывода [outputTemplate]. Так как в outputTemplate хранятся
 * лишь индексы строк, нужен общий словарь [stringsDictionary], где хранятся сами строки файлов. Для разных
 * режимов вывода нужны разные символы, показывающие статус строки — эта информация хранится в [style].
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
 * Выводит объединение двух файлов [outputTemplate]. Так как в outputTemplate хранятся лишь индексы строк,
 * нужен общий словарь [stringsDictionary], где хранятся сами строки файлов.
 */
fun plainOutput(stringsDictionary: List<String>, outputTemplate: List<Line>) {
    printBlock(stringsDictionary, outputTemplate, OutputBlock(0, 0, 0, outputTemplate.size), plusMinusStyle)
}

/**
 * На основе аргументов [parsedArgs], переданных программе, осуществляет вывод в нужном формате. [comparisonOutputData]
 * нужен для вывода, объекты файлов [file1Object] и [file2Object] нужны для «объединённого» формата вывода.
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
