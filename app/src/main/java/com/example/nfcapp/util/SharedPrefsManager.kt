package com.example.nfcapp.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manager for secure storage of app preferences.
 *
 * This class provides methods for securely storing and retrieving
 * application settings such as the active card ID, payment settings,
 * and force approval mode.
 */
class SharedPrefsManager private constructor(context: Context) {

    private val prefs: SharedPreferences

    // Preference keys
    private val KEY_ACTIVE_CARD_ID = "active_card_id"
    private val KEY_FORCE_APPROVAL = "force_approval_enabled"
    private val KEY_BIOMETRIC_REQUIRED = "biometric_required"
    private val KEY_APP_INITIALIZED = "app_initialized"
    private val KEY_FIRST_RUN = "first_run"

    init {
        // Create or retrieve the master key for encryption
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Initialize encrypted shared preferences
        prefs = EncryptedSharedPreferences.create(
            context,
            "secure_payment_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get the active card ID
     */
    fun getActiveCardId(): Long {
        return prefs.getLong(KEY_ACTIVE_CARD_ID, -1)
    }

    /**
     * Set the active card ID
     */
    fun setActiveCardId(cardId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_CARD_ID, cardId).apply()
    }

    /**
     * Check if force approval mode is enabled
     */
    fun isForceApprovalEnabled(): Boolean {
        return prefs.getBoolean(KEY_FORCE_APPROVAL, false)
    }

    /**
     * Set force approval mode
     */
    fun setForceApprovalEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_APPROVAL, enabled).apply()
    }

    /**
     * Check if biometric authentication is required
     */
    fun isBiometricRequired(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_REQUIRED, true)
    }

    /**
     * Set whether biometric authentication is required
     */
    fun setBiometricRequired(required: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_REQUIRED, required).apply()
    }

    /**
     * Check if app has been initialized
     */
    fun isAppInitialized(): Boolean {
        return prefs.getBoolean(KEY_APP_INITIALIZED, false)
    }

    /**
     * Set app initialized flag
     */
    fun setAppInitialized(initialized: Boolean) {
        prefs.edit().putBoolean(KEY_APP_INITIALIZED, initialized).apply()
    }

    /**
     * Check if this is the first run of the app
     */
    fun isFirstRun(): Boolean {
        val firstRun = prefs.getBoolean(KEY_FIRST_RUN, true)
        if (firstRun) {
            prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
        }
        return firstRun
    }

    /**
     * Clear all preferences (for logout or reset)
     */
    fun clearAllPreferences() {
        prefs.edit().clear().apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: SharedPrefsManager? = null

        fun getInstance(context: Context): SharedPrefsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SharedPrefsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}