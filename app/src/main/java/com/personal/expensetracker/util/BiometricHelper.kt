package com.personal.expensetracker.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wraps AndroidX Biometric for app lock.
 * Supports fingerprint, face, and device PIN/pattern/password.
 */
object BiometricHelper {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Check if any biometric or device credential is available. */
    fun canAuthenticate(context: Context): Boolean {
        val result = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Show biometric / PIN / pattern prompt.
     * Calls [onSuccess] when authenticated, [onError] on failure.
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User cancelled or too many attempts
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onError("Authentication cancelled")
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                // Called on each failed attempt, prompt stays open
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Expense Tracker")
            .setSubtitle("Authenticate to access your finances")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
