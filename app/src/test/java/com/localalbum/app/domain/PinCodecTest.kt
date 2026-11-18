package com.localalbum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCodecTest {
    @Test
    fun samePinMatchesAndWrongPinDoesNot() {
        val salt = PinCodec.newSalt()
        val hash = PinCodec.hash("2468", salt)
        assertTrue(hash != null)
        assertTrue(PinCodec.matches("2468", salt, hash!!))
        assertFalse(PinCodec.matches("2469", salt, hash))
    }

    @Test
    fun rejectsPinsThatAreNotFourDigits() {
        val salt = PinCodec.newSalt()
        assertEquals(null, PinCodec.hash("12", salt))
        assertEquals(null, PinCodec.hash("12ab", salt))
        assertFalse(PinCodec.matches("12345", salt, "00"))
    }

    @Test
    fun differentSaltsChangeTheHash() {
        val pin = "1357"
        val first = PinCodec.hash(pin, PinCodec.newSalt())
        val second = PinCodec.hash(pin, PinCodec.newSalt())
        assertNotEquals(first, second)
    }
}
