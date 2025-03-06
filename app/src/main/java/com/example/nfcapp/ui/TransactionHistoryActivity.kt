package com.example.nfcapp.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nfcapp.adapters.TransactionAdapter
import com.example.nfcapp.data.repository.TransactionRepository
import com.example.nfcapp.databinding.ActivityTransactionHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for displaying transaction history.
 *
 * This activity shows a list of all payment transactions made with the app,
 * including details like amount, date, merchant, and approval status.
 */
class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize repository
        transactionRepository = TransactionRepository.getInstance(this)

        // Set up toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Set up transaction RecyclerView
        setupTransactionList()

        // Load transaction data
        loadTransactions()
    }

    /**
     * Set up transaction RecyclerView and adapter
     */
    private fun setupTransactionList() {
        transactionAdapter = TransactionAdapter(
            onTransactionClick = { transaction ->
                // Show transaction details
                // This would typically navigate to a transaction details screen
                // For now, we'll just show the merchant name and amount
                val message = "${transaction.merchantName}: ${transaction.getFormattedAmount()}"
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        )

        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TransactionHistoryActivity)
            adapter = transactionAdapter
        }
    }

    /**
     * Load transaction data from repository
     */
    private fun loadTransactions() {
        lifecycleScope.launch {
            transactionRepository.getAllTransactions().collectLatest { transactions ->
                if (transactions.isEmpty()) {
                    binding.transactionsRecyclerView.visibility = View.GONE
                    binding.noTransactionsText.visibility = View.VISIBLE
                } else {
                    binding.transactionsRecyclerView.visibility = View.VISIBLE
                    binding.noTransactionsText.visibility = View.GONE
                    transactionAdapter.submitList(transactions)
                }
            }
        }
    }
}