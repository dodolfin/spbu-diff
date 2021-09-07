import kotlin.system.exitProcess

/*
 * В случае ошибки вывести сообщение [exitMessage] и завершить программу с ненулевым кодом возврата.
 */
fun terminateOnError(exitMessage: String) {
    println(exitMessage)
    exitProcess(1)
}

fun main(args: Array<String>) {
    TODO()
}
