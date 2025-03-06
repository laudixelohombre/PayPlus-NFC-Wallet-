package com.example.nfcapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nfcapp.R
import com.example.nfcapp.databinding.ItemTransactionBinding
import com.example.nfcapp.emv.TransactionData

/**
 * Adapter for displaying transaction history in a RecyclerView.
 *
 * This adapter uses ListAdapter with DiffUtil for efficient updates and
 * handles displaying transaction details like amount, merchant info, and status.
 */
class TransactionAdapter(
    private val onTransactionClick: (TransactionData) -> Unit
) : ListAdapter<TransactionData, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTransactionBinding.inflate(inflater, parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = getItem(position)
        holder.bind(transaction)
    }

    inner class TransactionViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val transaction = getItem(position)
                    onTransactionClick(transaction)
                }
            }
        }

        fun bind(transaction: TransactionData) {
            val context = binding.root.context

            // Set merchant name or fallback to "Unknown Merchant"
            binding.merchantName.text = transaction.merchantName.ifEmpty {
                "Unknown Merchant"
            }

            // Set transaction date
            binding.transactionDate.text = transaction.getFormattedDate()

            // Set card info (last 4 digits)
            binding.cardInfo.text = "•••• " + transaction.aid.takeLast(4)

            // Set transaction amount
            binding.transactionAmount.text = transaction.getFormattedAmount()

            // Set transaction status and color
            val statusText = if (transaction.approved) "Approved" else "Declined"
            binding.transactionStatus.text = statusText

            val statusColor = if (transaction.approved) {
                ContextCompat.getColor(context, R.color.transaction_approved)
            } else {
                ContextCompat.getColor(context, R.color.transaction_declined)
            }
            binding.transactionStatus.setTextColor(statusColor)

            // Set transaction icon (would normally use merchant category icons)
            // For demo purposes, we'll use a placeholder
            // binding.transactionIcon.setImageResource(R.drawable.ic_transaction)
        }
    }
}

/**
 * DiffUtil callback for transaction items
 */
class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionData>() {
    override fun areItemsTheSame(oldItem: TransactionData, newItem: TransactionData): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: TransactionData, newItem: TransactionData): Boolean {
        return oldItem.id == newItem.id &&
                oldItem.amount == newItem.amount &&
                oldItem.approved == newItem.approved &&
                oldItem.timestamp == newItem.timestamp
    }
}