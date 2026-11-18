package com.localalbum.app.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

sealed class DirectDelete {
    data object Deleted : DirectDelete()
    data object Failed : DirectDelete()
    data class NeedsUser(val intentSender: IntentSender) : DirectDelete()
}

/**
 * Removes stills the user already confirmed in the app.
 * Android 11 and newer always show the system delete confirmation.
 */
class MediaRemoval(
    private val context: Context,
) {
    fun batchRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }
        return null
    }

    fun deleteDirect(uri: Uri): DirectDelete {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) DirectDelete.Deleted else DirectDelete.Failed
        } catch (error: SecurityException) {
            recoverable(error)?.let { DirectDelete.NeedsUser(it) } ?: DirectDelete.Failed
        }
    }

    private fun recoverable(error: SecurityException): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val prompt = error as? RecoverableSecurityException ?: return null
        return prompt.userAction.actionIntent.intentSender
    }
}
