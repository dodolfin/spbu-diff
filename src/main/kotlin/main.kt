import kotlin.system.exitProcess
import java.io.File
import java.io.InputStream

const val SIZE_LIMIT = 1024 * 1024 * 1024

/*
 * В случае ошибки вывести сообщение [exitMessage] и завершить программу с ненулевым кодом возврата.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    exitProcess(1)
}

/*
 * Считать содержимое файла, находящегося по адресу [pathToFile]. Пока что эта функция производит и проверки,
 * связанные с файлом (существование, право доступа к нему и т. д.)
 */
fun readFromFile(pathToFile: String): Array<String> {
    val fileObject = File(pathToFile)

    if (!fileObject.exists()) {
        terminateOnError("$pathToFile does not exist.")
    }
    if (!fileObject.isFile()) {
        terminateOnError("$pathToFile is not a normal file.")
    }
    if (!fileObject.canRead() ) {
        terminateOnError("$pathToFile is not readable.")
    }
    if (fileObject.length() > SIZE_LIMIT) {
        terminateOnError("$pathToFile exceeds size limit ($SIZE_LIMIT bytes).")
    }

    val inputStream: InputStream = fileObject.inputStream()
    val lineList = mutableListOf<String>()

    inputStream.bufferedReader().forEachLine { lineList.add(it) }

    return lineList.toTypedArray()
}

fun main(args: Array<String>) {
    TODO()
}