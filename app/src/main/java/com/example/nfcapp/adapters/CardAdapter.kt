package com.example.nfcapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.nfcapp.data.model.Card
import com.example.nfcapp.databinding.ItemCardBinding

/**
 * Adapter for displaying payment cards in a ViewPager.
 *
 * This adapter handles displaying card information in a carousel format,
 * with support for card selection and click actions.
 */
class CardAdapter(
    private val onCardClick: (Card) -> Unit,
    private val onCardSelected: (Card) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private var cards: List<Card> = listOf()

    /**
     * Update the list of cards
     */
    fun setCards(newCards: List<Card>) {
        cards = newCards
        notifyDataSetChanged()
    }

    /**
     * Get card at specified position
     */
    fun getCardAt(position: Int): Card? {
        return if (position in cards.indices) cards[position] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCardBinding.inflate(inflater, parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        holder.bind(card)
    }

    override fun getItemCount(): Int = cards.size

    inner class CardViewHolder(private val binding: ItemCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val card = cards[position]
                    onCardClick(card)
                }
            }
        }

        fun bind(card: Card) {
            // Set card background color based on network
            binding.cardBackground.setBackgroundColor(card.getCardColor())

            // Set masked card number
            binding.cardNumber.text = card.getMaskedCardNumber()

            // Set cardholder name and expiry date
            binding.cardholderName.text = card.cardholderName.ifEmpty { "CARDHOLDER NAME" }
            binding.expiryDate.text = card.getFormattedExpiryDate()

            // Set card logo based on network
            // This would normally use real card network logos
            // For example:
            // when {
            //     card.cardNumber.startsWith("4") -> binding.cardLogo.setImageResource(R.drawable.visa_logo)
            //     ...
            // }

            // Show default indicator if this is the default card
            binding.defaultCardIndicator.visibility =
                if (card.isDefault) View.VISIBLE else View.GONE

            // Notify that this card is now selected
            onCardSelected(card)
        }
    }
}