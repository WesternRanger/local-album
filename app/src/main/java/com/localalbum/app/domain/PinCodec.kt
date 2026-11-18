package com.localalbum.app.domain

import java.security.MessageDigest
import java.security.SecureRandom

object PinCodec {
    private val shape = Regex("\\d{4}")

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): String? {
        if (!shape.matches(pin)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun matches(pin: String, salt: ByteArray, expected: String): Boolean {
        val actual = hash(pin, salt) ?: return false
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }
}
