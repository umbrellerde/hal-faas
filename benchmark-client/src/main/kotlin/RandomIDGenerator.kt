import java.lang.StringBuilder
import java.util.Random

class RandomIDGenerator {
    companion object {
        private var chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        fun next(): String {
            val sb = StringBuilder()
            repeat(10) {
                sb.append(chars.random())
            }
            return sb.toString()
        }
    }
}