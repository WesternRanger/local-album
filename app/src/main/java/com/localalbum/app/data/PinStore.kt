package com.localalbum.app.data

import android.content.Context
import android.util.Base64
import com.localalbum.app.domain.PinCodec

class PinStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPin(): Boolean {
        return prefs.getString(HASH, null) != null && prefs.getString(SALT, null) != null
    }

    fun setPin(pin: String) {
        val salt = PinCodec.newSalt()
        val hash = PinCodec.hash(pin, salt) ?: return
        prefs.edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, hash)
            .apply()
    }

    fun check(pin: String): Boolean {
        val saltText = prefs.getString(SALT, null) ?: return false
        val hash = prefs.getString(HASH, null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        return PinCodec.matches(pin, salt, hash)
    }

    private companion object {
        const val PREFS = "local_album_pin"
        const val SALT = "salt"
        const val HASH = "hash"
    }
}
