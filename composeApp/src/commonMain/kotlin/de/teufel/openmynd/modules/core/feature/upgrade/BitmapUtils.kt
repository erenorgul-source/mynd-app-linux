package de.teufel.openmynd.modules.core.feature.upgrade

internal object BitmapUtils {

    fun getZeroBitIndexMap(bitmap: ByteArray): IntArray {
        val num = countZeroBit(bitmap)
        val array = IntArray(num)
        var count = 0
        var index = 0
        for (b in bitmap) {
            for (i in 0..7) {
                val offset = index % 8
                if (b.toInt() and (0x1 shl offset) == 0x0) {
                    array[count] = index
                    count++
                }
                index++
            }
        }
        return array
    }

    fun countZeroBit(bitmap: ByteArray): Int {
        var count = 0
        for (x in bitmap) {
            var temp = x.toInt()
            while (temp + 1 != 0) {
                temp = temp or temp + 1
                count++
            }
        }
        return count
    }
}
