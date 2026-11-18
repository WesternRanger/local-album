package com.localalbum.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun deviceBiometricAvailable(context: Context): Boolean {
    return try {
        val manager = BiometricManager.from(context)
        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Exception) {
        false
    }
}

@Composable
fun rememberBiometricPrompt(
    onSuccess: () -> Unit,
    onFailed: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val success = rememberUpdatedState(onSuccess)
    val failed = rememberUpdatedState(onFailed)
    return remember(context) {
        {
            val activity = context.findFragmentActivity()
            if (activity == null) {
                failed.value("这台手机暂时不能用指纹或面容")
            } else {
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            success.value()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_CANCELED
                            ) {
                                failed.value(errString.toString().ifBlank { "没能通过验证" })
                            }
                        }
                    },
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("验证身份")
                    .setSubtitle("打开私密空间")
                    .setNegativeButtonText("改用密码")
                    .build()
                prompt.authenticate(info)
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
