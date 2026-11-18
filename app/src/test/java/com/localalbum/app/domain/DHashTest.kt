package com.localalbum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DHashTest {
    @Test
    fun flatImageHashesToZero() {
        val luma = IntArray(9 * 8) { 40 }
        assertEquals(0L, DHash.fromLuma(9, 8, luma))
    }

    @Test
    fun decreasingRowsSetEveryBit() {
        val luma = IntArray(9 * 8) { index -> 200 - index }
        assertEquals(-1L, DHash.fromLuma(9, 8, luma))
        assertEquals(64, DHash.hamming(0L, -1L))
    }
}
