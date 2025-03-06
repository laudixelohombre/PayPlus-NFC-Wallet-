package com.example.nfcapp

import android.app.Application
import android.util.Log
import com.example.nfcapp.data.repository.CardRepository
import com.example.nfcapp.data.repository.TransactionRepository
import com.example.nfcapp.util.BiometricHelper
import com.example.nfcapp.util.SharedPrefsManager

/**
 * Application class for the PayPlus Wallet app.
 *
 * This class handles global initialization of components,
 * provides access to repositories, and maintains application state.
 */
class PayPlusApp : Application() {

    private val TAG = "PayPlusApp"

    // Repositories and utilities
    private lateinit var cardRepository: CardRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var biometricHelper: BiometricHelper

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Initializing PayPlus Wallet App")

        // Initialize repositories and utilities
        initializeComponents()

        // Configure default settings
        setupDefaultSettings()

        // Log app initialization
        Log.d(TAG, "App initialization complete")
    }

    /**
     * Initialize core application components
     */
    private fun initializeComponents() {
        // Initialize repositories
        cardRepository = CardRepository.getInstance(this)
        transactionRepository = TransactionRepository.getInstance(this)

        // Initialize utilities
        sharedPrefsManager = SharedPrefsManager.getInstance(this)
        biometricHelper = BiometricHelper(this)
    }

    /**
     * Set up default app settings
     */
    private fun setupDefaultSettings() {
        // Check if first run
        if (sharedPrefsManager.isFirstRun()) {
            Log.d(TAG, "First run detected, applying default settings")

            // Set default settings
            sharedPrefsManager.setBiometricRequired(biometricHelper.isBiometricAvailable())
            sharedPrefsManager.setForceApprovalEnabled(false)
            sharedPrefsManager.setAppInitialized(true)
        }
    }

    companion object {
        // Version info
        const val APP_VERSION = "1.0.0"

        // Max card limit
        const val MAX_CARDS = 10

        // Feature flags
        const val ENABLE_FORCE_APPROVAL = true
        const val ENABLE_BIOMETRIC_AUTH = true
    }
}