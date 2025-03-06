package com.example.nfcapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nfcapp.data.model.Card
import com.example.nfcapp.data.repository.CardRepository
import com.example.nfcapp.databinding.ActivityCardDetailsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for displaying and managing detailed information about a payment card.
 *
 * This activity shows comprehensive card information and provides actions for
 * managing the card (setting as default, enabling/disabling, deleting).
 */
class CardDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCardDetailsBinding
    private lateinit var cardRepository: CardRepository
    private var cardId: Long = -1
    private var card: Card? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize repository
        cardRepository = CardRepository.getInstance(this)

        // Set up toolbar and back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Get card ID from intent
        cardId = intent.getLongExtra(EXTRA_CARD_ID, -1L)
        if (cardId == -1L) {
            Toast.makeText(this, "Error: Card not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load card data
        loadCardData()

        // Set up button actions
        setupButtonActions()
    }

    /**
     * Load card data from repository
     */
    private fun loadCardData() {
        lifecycleScope.launch {
            card = cardRepository.getCardById(cardId)

            if (card != null) {
                displayCardData(card!!)
            } else {
                Toast.makeText(
                    this@CardDetailsActivity,
                    "Error: Card not found",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /**
     * Display card data in the UI
     */
    private fun displayCardData(card: Card) {
        // Card preview
        binding.cardBackground.setBackgroundColor(card.getCardColor())
        binding.cardNumber.text = card.getMaskedCardNumber()
        binding.cardholderName.text = card.cardholderName.ifEmpty { "CARDHOLDER NAME" }
        binding.expiryDate.text = card.getFormattedExpiryDate()

        // Show default indicator if default card
        binding.defaultCardIndicator.visibility = if (card.isDefault) View.VISIBLE else View.GONE

        // Card details
        binding.cardNumberValue.text = card.getMaskedCardNumber()
        binding.expiryDateValue.text = card.getFormattedExpiryDate()
        binding.cardholderNameValue.text = card.cardholderName
        binding.cardTypeValue.text = card.cardType
        binding.cardNetworkValue.text = card.cardNetwork
        binding.cardStatusValue.text = if (card.isEnabled) "Active" else "Disabled"

        // Format dates
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.dateAddedValue.text = dateFormat.format(Date(card.dateAdded))

        // Show last used date or "Never used" if not used
        binding.lastUsedValue.text = if (card.lastUsed > 0) {
            dateFormat.format(Date(card.lastUsed))
        } else {
            "Never used"
        }

        // Update button states
        updateButtonStates(card)
    }

    /**
     * Update button states based on card status
     */
    private fun updateButtonStates(card: Card) {
        // Set default button (disable if already default)
        binding.setDefaultButton.isEnabled = !card.isDefault
        binding.setDefaultButton.text = if (card.isDefault) {
            "Default Card"
        } else {
            "Set as Default Card"
        }

        // Toggle enabled button
        binding.toggleEnabledButton.text = if (card.isEnabled) {
            "Disable Card"
        } else {
            "Enable Card"
        }
    }

    /**
     * Set up button click listeners
     */
    private fun setupButtonActions() {
        // Set as default button
        binding.setDefaultButton.setOnClickListener {
            setCardAsDefault()
        }

        // Toggle enabled button
        binding.toggleEnabledButton.setOnClickListener {
            toggleCardEnabled()
        }

        // Delete card button
        binding.deleteCardButton.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    /**
     * Set card as default
     */
    private fun setCardAsDefault() {
        card?.let { card ->
            lifecycleScope.launch {
                try {
                    cardRepository.setCardAsDefault(card.id)

                    // Reload card data to reflect changes
                    loadCardData()

                    Toast.makeText(
                        this@CardDetailsActivity,
                        "Card set as default",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@CardDetailsActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Toggle card enabled/disabled state
     */
    private fun toggleCardEnabled() {
        card?.let { card ->
            lifecycleScope.launch {
                try {
                    val newState = !card.isEnabled
                    cardRepository.setCardEnabled(card.id, newState)

                    // Reload card data to reflect changes
                    loadCardData()

                    val statusMessage = if (newState) "Card enabled" else "Card disabled"
                    Toast.makeText(
                        this@CardDetailsActivity,
                        statusMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@CardDetailsActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Show delete confirmation dialog
     */
    private fun showDeleteConfirmation() {
        card?.let { card ->
            AlertDialog.Builder(this)
                .setTitle("Delete Card")
                .setMessage("Are you sure you want to delete this card? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteCard(card)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /**
     * Delete card from repository
     */
    private fun deleteCard(card: Card) {
        lifecycleScope.launch {
            try {
                cardRepository.deleteCard(card)

                Toast.makeText(
                    this@CardDetailsActivity,
                    "Card deleted",
                    Toast.LENGTH_SHORT
                ).show()

                // Close activity
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@CardDetailsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        const val EXTRA_CARD_ID = "card_id"
    }
}