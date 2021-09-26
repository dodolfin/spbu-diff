package com.dodolfin.diff.compare

/**
 * Используется для восстановления ответа в решении задачи о LCS
 */
enum class ReconstructionMarker {
    NONE, REMOVE_FROM_LCS, LEFT, UP
}

/**
 * Данные, необходимые для сравнения и вывода. [stringsDictionary] — общий словарь строчек из двух файлов,
 * [outputTemplate] — заготовка для вывода, в которой строчки расположены в нужном порядке, и затем,
 * в зависимости от конкретного формата, убирает какие-то строчки, [comparisonData] описан далее.
 */
data class ComparisonOutputData(
    val stringsDictionary: List<String>,
    val outputTemplate: MutableList<Line>,
    val comparisonData: ComparisonData
)

/**
 * Данные, необходимые для сравнения файлов. [file1] и [file2] хранятся в виде индексов соответствующих строк в
 * stringsDictionary
 */
data class ComparisonData(val file1: List<Line>, val file2: List<Line>)

/**
 * Обозначает строчку файла, хранит [stringIndex] — индекс строчки в общем словаре строк stringsDictionary и
 * [lineMarker] — роль строчки в изменяющей последовательности строки
 */
data class Line(val stringIndex: Int, var lineMarker: LineMarker = LineMarker.NONE)

/**
 * Обозначает статус строки согласно алгоритму LCS (COMMON — входит в LCS, DELETED — в первом файле и в LCS не входит и
 * была удалена, ADDED — аналогично DELETED, была добавлена)
 */
enum class LineMarker {
    NONE, COMMON, DELETED, ADDED
}

/**
 * Преобразовать содержимое файлов в объект типа ComparisonOutputData, в котором содержится общий словарь строк,
 * содержимое файлов в формате Line (индекс строки в словаре и её положение в LCS) и заготовка для вывода, позже
 * заполняемая в функции produceOutputTemplate.
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

/**
 * Небольшая оптимизация алгоритма поиска LCS — заранее отмечает строчки, которых нет в другом файле, удалёнными или
 * добавленными. Ничего не возвращает, так как изменяет исходный объект.
 */
fun markNotCommonLines(comparisonData: ComparisonData) {
    val indicesCount = MutableList(2) { MutableList(comparisonData.file1.size + comparisonData.file2.size) { 0 } }
    val fromCollections = arrayOf(comparisonData.file1, comparisonData.file2)

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
 * Сравнивает два файла, содержащихся в [comparisonData]. Ничего не возвращает, так как изменяет исходный объект.
 */
fun compareTwoFiles(comparisonData: ComparisonData) {
    // Перед тем как сравнивать строчки, оставим только те, для которых ещё неизвестно, входят они в LCS или нет
    val file1 = comparisonData.file1.filter { it.lineMarker == LineMarker.NONE }
    val file2 = comparisonData.file2.filter { it.lineMarker == LineMarker.NONE }

    /*
     * LCS означает Longest Common Subsequence — наибольшую общую подпоследовательность
     * В массиве LCSMemoization хранятся значения НОП для всех возможных префиксов двух файлов,
     * а в массиве LCSReconstruction хранятся данные для восстановления самой LCS.
     */
    val LCSMemoization = MutableList(file1.size + 1) { MutableList(file2.size + 1) { 0 } }
    val LCSReconstruction = MutableList(file1.size + 1) { MutableList(file2.size + 1) { ReconstructionMarker.NONE } }

    /*
     * В массиве LCSReconstruction значение
     * REMOVE_FROM_LCS означает, что в точке [prefix1 + 1][prefix2 + 1] последние строки совпали и их выгодно было включить в НОП
     * LEFT означает, что значение НОП было лучше в точке [prefix1][prefix2 + 1]
     * UP означает, что значение НОП было лучше в точке [prefix1 + 1][prefix2]
     */
    for (prefix1 in file1.indices) {
        for (prefix2 in file2.indices) {
            if (file1[prefix1] == file2[prefix2]) {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1][prefix2] + 1
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.REMOVE_FROM_LCS
            } else if (LCSMemoization[prefix1][prefix2 + 1] > LCSMemoization[prefix1 + 1][prefix2]) {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1][prefix2 + 1]
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.LEFT
            } else {
                LCSMemoization[prefix1 + 1][prefix2 + 1] = LCSMemoization[prefix1 + 1][prefix2]
                LCSReconstruction[prefix1 + 1][prefix2 + 1] = ReconstructionMarker.UP
            }
        }
    }

    // Восстановление ответа
    // Сначала отметим все строки из первого и второго файла как не входящие в LCS
    file1.forEach { it.lineMarker = LineMarker.DELETED }
    file2.forEach { it.lineMarker = LineMarker.ADDED }

    // В цикле отметим строки, на самом деле входящие в LCS
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
