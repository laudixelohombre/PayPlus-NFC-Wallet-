package com.example.nfcapp.ui

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.nfcapp.R
import com.example.nfcapp.data.model.Card
import com.example.nfcapp.data.repository.CardRepository
import com.example.nfcapp.databinding.ActivityAddCardBinding
import kotlinx.coroutines.launch

/**
 * Activity for adding new payment cards.
 *
 * This activity provides a form for users to enter card details (number,
 * expiry date, CVV, cardholder name) and validates the input before
 * storing the card in the database.
 */
class AddCardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCardBinding
    private lateinit var cardRepository: CardRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCardBinding.inflate(layoutInflater)
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

        // Set up input formatters
        setupInputFormatters()

        // Set up save button
        binding.saveCardButton.setOnClickListener {
            saveCard()
        }

        // Set up cancel button
        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    /**
     * Set up input formatters for card number and expiry date
     */
    private fun setupInputFormatters() {
        // Card number formatter (adds spaces)
        binding.cardNumberInput.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            private val divider = ' '

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return

                isFormatting = true

                // Remove all dividers first
                val str = s.toString().replace(divider.toString(), "")

                // Add dividers after every 4 characters
                val formatted = StringBuilder()
                for (i in str.indices) {
                    formatted.append(str[i])
                    if ((i + 1) % 4 == 0 && i != str.length - 1) {
                        formatted.append(divider)
                    }
                }

                // Replace the text
                s.replace(0, s.length, formatted.toString())

                // Update the card preview
                updateCardPreview()

                isFormatting = false
            }
        })

        // Expiry date formatter (adds slash)
        binding.expiryDateInput.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return

                isFormatting = true

                val str = s.toString().replace("/", "")

                if (str.length > 2) {
                    s.replace(0, s.length, str.substring(0, 2) + "/" + str.substring(2))
                }

                // Update the card preview
                updateCardPreview()

                isFormatting = false
            }
        })

        // Cardholder name formatter (converts to uppercase)
        binding.cardholderNameInput.addTextChangedListener {
            // Update the card preview
            updateCardPreview()
        }

        // CVV formatter
        binding.cvvInput.addTextChangedListener {
            // Update card preview (not directly visible in preview)
            updateCardPreview()
        }
    }

    /**
     * Update the card preview with current input values
     */
    private fun updateCardPreview() {
        val cardNumber = binding.cardNumberInput.text.toString()
        val expiryDate = binding.expiryDateInput.text.toString()
        val holderName = binding.cardholderNameInput.text.toString().uppercase()

        // Set card background color based on first digit
        val cardBackground = binding.cardPreview.root.findViewById<View>(R.id.cardBackground)
        cardBackground?.setBackgroundColor(getCardColor(cardNumber))

        // Update card number display
        val cardNumberPreview =
            binding.cardPreview.root.findViewById<android.widget.TextView>(R.id.cardNumber)
        cardNumberPreview?.text = if (cardNumber.isNotEmpty()) {
            cardNumber
        } else {
            "**** **** **** ****"
        }

        // Update expiry date display
        val expiryDatePreview =
            binding.cardPreview.root.findViewById<android.widget.TextView>(R.id.expiryDate)
        expiryDatePreview?.text = if (expiryDate.isNotEmpty()) {
            expiryDate
        } else {
            "MM/YY"
        }

        // Update cardholder name display
        val cardholderNamePreview =
            binding.cardPreview.root.findViewById<android.widget.TextView>(R.id.cardholderName)
        cardholderNamePreview?.text = if (holderName.isNotEmpty()) {
            holderName
        } else {
            "CARDHOLDER NAME"
        }
    }

    /**
     * Get card background color based on card number
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getCardColor(cardNumber: String): Int {
        val cleanNumber = cardNumber.replace(" ", "")

        return when {
            cleanNumber.startsWith("4") -> getColor(R.color.card_visa)
            cleanNumber.startsWith("5") -> getColor(R.color.card_mastercard)
            cleanNumber.startsWith("3") -> getColor(R.color.card_amex)
            cleanNumber.startsWith("6") -> getColor(R.color.card_discover)
            else -> getColor(R.color.card_default)
        }
    }

    /**
     * Save the card to the database
     */
    private fun saveCard() {
        // Get input values
        val cardNumber = binding.cardNumberInput.text.toString().replace(" ", "")
        val expiryDate = binding.expiryDateInput.text.toString()
        val cvv = binding.cvvInput.text.toString()
        val holderName = binding.cardholderNameInput.text.toString()

        // Validate inputs
        if (!validateInputs(cardNumber, expiryDate, cvv)) {
            return
        }

        // Create card object
        val card = Card(
            cardNumber = formatCardNumber(cardNumber),
            expiryDate = expiryDate,
            cvv = cvv,
            cardholderName = holderName,
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            pan = cardNumber,
            panSequence = "01",
            issuerName = getIssuerName(cardNumber),
            cardNetwork = getCardNetwork(cardNumber),
            cardType = "CREDIT" // Would normally detect debit vs credit
        )

        // Save card to database
        lifecycleScope.launch {
            try {
                val cardId = cardRepository.addCard(card)

                // Show success message
                Toast.makeText(
                    this@AddCardActivity,
                    getString(R.string.card_added_success),
                    Toast.LENGTH_SHORT
                ).show()

                // Close activity
                finish()
            } catch (e: CardRepository.MaxCardLimitException) {
                // Show error - maximum 10 cards
                Toast.makeText(
                    this@AddCardActivity,
                    "Maximum limit of 10 cards reached",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                // Show generic error
                Toast.makeText(
                    this@AddCardActivity,
                    getString(R.string.card_error) + ": " + e.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Validate card input fields
     */
    private fun validateInputs(cardNumber: String, expiryDate: String, cvv: String): Boolean {
        var isValid = true

        // Validate card number (Luhn algorithm check would be here in production)
        if (cardNumber.length < 15 || cardNumber.length > 19) {
            binding.cardNumberLayout.error = "Invalid card number"
            isValid = false
        } else {
            binding.cardNumberLayout.error = null
        }

        // Validate expiry date
        if (!expiryDate.matches(Regex("^(0[1-9]|1[0-2])/[0-9]{2}$"))) {
            binding.expiryDateLayout.error = "Invalid format (MM/YY)"
            isValid = false
        } else {
            // Check if card has expired
            val parts = expiryDate.split("/")
            val expiryMonth = parts[0].toInt()
            val expiryYear = 2000 + parts[1].toInt()

            val calendar = java.util.Calendar.getInstance()
            val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
            val currentYear = calendar.get(java.util.Calendar.YEAR)

            if ((currentYear > expiryYear) ||
                (currentYear == expiryYear && currentMonth > expiryMonth)
            ) {
                binding.expiryDateLayout.error = "Card has expired"
                isValid = false
            } else {
                binding.expiryDateLayout.error = null
            }
        }

        // Validate CVV
        if (cvv.length < 3 || cvv.length > 4) {
            binding.cvvLayout.error = "Invalid CVV"
            isValid = false
        } else {
            binding.cvvLayout.error = null
        }

        return isValid
    }

    /**
     * Format card number with spaces
     */
    private fun formatCardNumber(cardNumber: String): String {
        val result = StringBuilder()

        for (i in cardNumber.indices) {
            if (i > 0 && i % 4 == 0) {
                result.append(" ")
            }
            result.append(cardNumber[i])
        }

        return result.toString()
    }

    /**
     * Get issuer name based on card number
     */
    private fun getIssuerName(cardNumber: String): String {
        return when {
            cardNumber.startsWith("4") -> "Visa"
            cardNumber.startsWith("5") -> "Mastercard"
            cardNumber.startsWith("34") || cardNumber.startsWith("37") -> "American Express"
            cardNumber.startsWith("6") -> "Discover"
            else -> "Unknown"
        }
    }

    /**
     * Get card network based on card number
     */
    private fun getCardNetwork(cardNumber: String): String {
        return when {
            cardNumber.startsWith("4") -> "VISA"
            cardNumber.startsWith("5") -> "MASTERCARD"
            cardNumber.startsWith("34") || cardNumber.startsWith("37") -> "AMEX"
            cardNumber.startsWith("6") -> "DISCOVER"
            else -> "UNKNOWN"
        }
    }
}