package com.example.nfcapp.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nfcapp.data.repository.TransactionRepository
import com.example.nfcapp.databinding.ActivityPaymentSuccessBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity displayed after a successful payment.
 *
 * This activity shows payment confirmation details and automatically
 * returns to the main screen after a short delay.
 */
class PaymentSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentSuccessBinding
    private lateinit var transactionRepository: TransactionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize repository
        transactionRepository = TransactionRepository.getInstance(this)

        // Get transaction ID from intent
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)

        // Set up done button
        binding.doneButton.setOnClickListener {
            finish()
        }

        // Load and display transaction details
        loadTransactionDetails(transactionId)

        // Auto-dismiss after delay
        startAutoDismissTimer()
    }

    /**
     * Load transaction details from the repository
     */
    private fun loadTransactionDetails(transactionId: Long) {
        if (transactionId == -1L) {
            // No transaction ID provided, show generic success
            binding.transactionDetailsLayout.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)

            if (transaction != null) {
                // Show transaction details
                binding.transactionDetailsLayout.visibility = View.VISIBLE

                // Set merchant name
                binding.merchantName.text = transaction.merchantName.ifEmpty {
                    "Payment Completed"
                }

                // Set amount
                binding.paymentAmount.text = transaction.getFormattedAmount()

                // Set date
                binding.paymentDate.text = transaction.getFormattedDate()

                // Set card info (last 4 digits)
                binding.cardInfo.text = if (transaction.aid.length >= 4) {
                    "•••• " + transaction.aid.takeLast(4)
                } else {
                    "•••• ****"
                }
            } else {
                // Transaction not found, hide details
                binding.transactionDetailsLayout.visibility = View.GONE
            }
        }
    }

    /**
     * Start a timer to automatically dismiss this activity
     */
    private fun startAutoDismissTimer() {
        lifecycleScope.launch {
            delay(AUTO_DISMISS_DELAY)

            // Only auto-finish if activity is still running
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        private const val AUTO_DISMISS_DELAY = 5000L // 5 seconds
    }
}