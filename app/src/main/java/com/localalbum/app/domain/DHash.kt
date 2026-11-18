package com.localalbum.app.domain

/**
 * 64-bit difference hash. The input is a row-major luma plane at least 9 pixels
 * wide and 8 pixels tall. Each bit compares one pixel with the neighbor on its right.
 */
object DHash {
    fun fromLuma(width: Int, height: Int, luma: IntArray): Long {
        require(width >= 9 && height >= 8 && luma.size >= width * height)
        var bits = 0L
        for (y in 0 until 8) {
            val row = y * width
            for (x in 0 until 8) {
                bits = bits shl 1
                if (luma[row + x] > luma[row + x + 1]) bits = bits or 1L
            }
        }
        return bits
    }

    fun hamming(left: Long, right: Long): Int = java.lang.Long.bitCount(left xor right)
}
