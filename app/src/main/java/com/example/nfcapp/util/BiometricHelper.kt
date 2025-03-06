package com.example.nfcapp.util

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.nfcapp.R
import java.util.concurrent.Executor

/**
 * Helper class for handling biometric authentication.
 *
 * This class provides methods to check biometric availability and prompt
 * the user for biometric authentication (fingerprint, face, etc.) to
 * authorize payment transactions.
 */
class BiometricHelper(private val context: Context) {

    private val TAG = "BiometricHelper"
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // Callback for authentication result
    private var authCallback: ((Boolean) -> Unit)? = null

    // Flag to indicate if authentication is required for payment
    private var requireAuthentication = true

    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)

        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d(TAG, "No biometric hardware")
                false
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d(TAG, "Biometric hardware unavailable")
                false
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(TAG, "No biometrics enrolled")
                false
            }

            else -> {
                Log.d(TAG, "Biometric status unknown")
                false
            }
        }
    }

    /**
     * Set whether authentication is required for payments
     */
    fun setAuthenticationRequired(required: Boolean) {
        requireAuthentication = required
    }

    /**
     * Check if authentication is required for payments
     */
    fun isAuthenticationRequired(): Boolean {
        return requireAuthentication && isBiometricAvailable()
    }

    /**
     * Show biometric prompt for payment authentication
     *
     * @param activity The current activity
     * @param callback Callback with authentication result (true=success, false=failure)
     */
    fun showBiometricPrompt(activity: FragmentActivity, callback: (Boolean) -> Unit) {
        // If authentication is not required, immediately succeed
        if (!requireAuthentication) {
            callback(true)
            return
        }

        // If biometrics are not available, fail
        if (!isBiometricAvailable()) {
            Log.d(TAG, "Biometrics not available, failing authentication")
            callback(false)
            return
        }

        authCallback = callback

        // Set up executor
        executor = ContextCompat.getMainExecutor(context)

        // Set up biometric prompt
        biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d(TAG, "Authentication error: $errString")
                    authCallback?.invoke(false)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Authentication succeeded")
                    authCallback?.invoke(true)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "Authentication failed")
                    // Don't invoke the callback for failure, only for error
                    // This allows multiple attempts
                }
            })

        // Configure the prompt
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_prompt_title))
            .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
            .setDescription(context.getString(R.string.biometric_prompt_description))
            .setNegativeButtonText(context.getString(android.R.string.cancel))
            .setConfirmationRequired(true)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        // Show the prompt
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Authenticate for direct payment (without UI prompt)
     *
     * This is used in the HCE service to quietly approve transactions
     * when biometric authentication has already been performed.
     */
    fun authenticateForPayment(): Boolean {
        // In a real app, this would check if recent biometric auth was performed
        // For this demo, we'll just check if auth is required
        return !requireAuthentication
    }
}