package co.igloohome.igloohome_sdk_demo

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*
import java.util.Base64

private val HEX_CHARS = "0123456789ABCDEF"
internal fun ByteArray.toPlainHex() : String{
    val result = StringBuffer()
    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append("${HEX_CHARS[firstIndex]}")
        result.append("${HEX_CHARS[secondIndex]}")
    }
    return result.toString()
}

class PrettyPrintingMap<K, V>(private val map: Map<K, V>) {
    override fun toString(): String {
        val sb = StringBuilder()
        val iter =
            map.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            sb.append(entry.key)
            sb.append('=').append('"')
            sb.append(entry.value)
            sb.append('"')
            if (iter.hasNext()) {
                sb.append(',').append(' ')
            }
        }
        return sb.toString()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun arrayInt2Base64String(ints: IntArray) : String? {
    val bytes =
        ints.foldIndexed(ByteArray(ints.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
    val base64String: String? = Base64.getEncoder().encodeToString(bytes)
    return base64String
}