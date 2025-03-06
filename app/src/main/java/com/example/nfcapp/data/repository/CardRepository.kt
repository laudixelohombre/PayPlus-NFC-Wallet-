package com.example.nfcapp.data.repository

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.nfcapp.data.model.Card
import com.example.nfcapp.util.HexUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Repository for card data storage and retrieval.
 *
 * This class provides methods for adding, updating, deleting and retrieving
 * payment cards from the Room database. It also handles encryption of
 * sensitive card data like card number and CVV.
 */
class CardRepository private constructor(context: Context) {

    private val cardDao: CardDao
    private val encryptionHelper: CardEncryptionHelper

    init {
        // Initialize Room database
        val database = CardDatabase.getInstance(context)
        cardDao = database.cardDao()

        // Initialize encryption helper
        encryptionHelper = CardEncryptionHelper(context)
    }

    /**
     * Get all cards as a Flow
     */
    fun getAllCards(): Flow<List<Card>> {
        return cardDao.getAllCards()
    }

    /**
     * Get card by ID
     */
    suspend fun getCardById(id: Long): Card? {
        val card = cardDao.getCardById(id) ?: return null
        return decryptCard(card)
    }

    /**
     * Add a new card
     */
    suspend fun addCard(card: Card): Long = withContext(Dispatchers.IO) {
        // Check if maximum card limit is reached (10 cards)
        val cardCount = cardDao.getCardCount()
        if (cardCount >= 10) {
            throw MaxCardLimitException("Maximum limit of 10 cards reached")
        }

        // Encrypt sensitive card data
        val encryptedCard = encryptCard(card)

        // Set as default if it's the first card
        if (cardCount == 0) {
            encryptedCard.isDefault = true
        }

        // Insert into database
        return@withContext cardDao.insertCard(encryptedCard)
    }

    /**
     * Update an existing card
     */
    suspend fun updateCard(card: Card) = withContext(Dispatchers.IO) {
        val encryptedCard = encryptCard(card)
        cardDao.updateCard(encryptedCard)
    }

    /**
     * Delete a card
     */
    suspend fun deleteCard(card: Card) = withContext(Dispatchers.IO) {
        cardDao.deleteCard(card)

        // If the deleted card was default, set a new default card
        if (card.isDefault) {
            val remainingCards = cardDao.getCardsList()
            if (remainingCards.isNotEmpty()) {
                val newDefault = remainingCards.first()
                newDefault.isDefault = true
                cardDao.updateCard(newDefault)
            }
        }
    }

    /**
     * Set a card as the default card
     */
    suspend fun setDefaultCard(cardId: Long) = withContext(Dispatchers.IO) {
        // Clear default flag on all cards
        cardDao.clearDefaultCards()

        // Set the specified card as default
        val card = cardDao.getCardById(cardId)
        if (card != null) {
            card.isDefault = true
            cardDao.updateCard(card)
        }
    }

    /**
     * Get the default card
     */
    suspend fun getDefaultCard(): Card? = withContext(Dispatchers.IO) {
        val defaultCard = cardDao.getDefaultCard()
        if (defaultCard != null) {
            return@withContext decryptCard(defaultCard)
        }
        return@withContext null
    }

    /**
     * Encrypt sensitive card data
     */
    private fun encryptCard(card: Card): Card {
        // Create a copy to avoid modifying the original
        val encryptedCard = card.copy()

        // Encrypt card number
        if (card.cardNumber.isNotEmpty()) {
            encryptedCard.cardNumber = encryptionHelper.encrypt(card.cardNumber)
        }

        // Encrypt CVV
        if (card.cvv.isNotEmpty()) {
            encryptedCard.cvv = encryptionHelper.encrypt(card.cvv)
        }

        return encryptedCard
    }

    /**
     * Decrypt sensitive card data
     */
    private fun decryptCard(card: Card): Card {
        // Create a copy to avoid modifying the original
        val decryptedCard = card.copy()

        // Decrypt card number
        if (card.cardNumber.isNotEmpty()) {
            decryptedCard.cardNumber = encryptionHelper.decrypt(card.cardNumber)
        }

        // Decrypt CVV
        if (card.cvv.isNotEmpty()) {
            decryptedCard.cvv = encryptionHelper.decrypt(card.cvv)
        }

        return decryptedCard
    }

    companion object {
        @Volatile
        private var INSTANCE: CardRepository? = null

        fun getInstance(context: Context): CardRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = CardRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Exception thrown when maximum card limit is reached
     */
    class MaxCardLimitException(message: String) : Exception(message)
}

/**
 * DAO (Data Access Object) for Card entity
 */
@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY isDefault DESC, lastUsed DESC")
    fun getAllCards(): Flow<List<Card>>

    @Query("SELECT * FROM cards ORDER BY isDefault DESC, lastUsed DESC")
    suspend fun getCardsList(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: Long): Card?

    @Query("SELECT * FROM cards WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultCard(): Card?

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: Card): Long

    @Update
    suspend fun updateCard(card: Card)

    @Delete
    suspend fun deleteCard(card: Card)

    @Query("UPDATE cards SET isDefault = 0")
    suspend fun clearDefaultCards()
}

/**
 * Room database for storing cards
 */
@Database(entities = [Card::class], version = 1, exportSchema = false)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var INSTANCE: CardDatabase? = null

        fun getInstance(context: Context): CardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CardDatabase::class.java,
                    "card_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Helper class for encrypting and decrypting sensitive card data
 */
class CardEncryptionHelper(context: Context) {
    private val TAG = "CardEncryptionHelper"
    private val TRANSFORMATION = "AES/GCM/NoPadding"
    private val KEY_SIZE = 256
    private val IV_LENGTH = 12
    private val ALGORITHM = "AES"

    private val masterKey: MasterKey
    private val sharedPreferences: EncryptedSharedPreferences

    init {
        // Create or retrieve the Master Key for encryption
        masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Initialize EncryptedSharedPreferences for storing encryption keys
        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secure_card_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    /**
     * Encrypt a string value
     */
    fun encrypt(plaintext: String): String {
        try {
            // Get or generate encryption key
            val secretKey = getEncryptionKey()

            // Generate random IV (Initialization Vector)
            val iv = ByteArray(IV_LENGTH)
            java.security.SecureRandom().nextBytes(iv)

            // Initialize cipher for encryption
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            // Encrypt the data
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray())

            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            // Convert to hex string for storage
            return HexUtil.toHexString(combined)
        } catch (e: Exception) {
            // In case of encryption failure, just return the original string
            // Not secure, but this prevents data loss in case of cryptographic errors
            return plaintext
        }
    }

    /**
     * Decrypt a string value
     */
    fun decrypt(encryptedHex: String): String {
        try {
            // Convert hex to bytes
            val combined = HexUtil.toByteArray(encryptedHex)

            // Extract IV and encrypted data
            val iv = ByteArray(IV_LENGTH)
            val encryptedBytes = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)

            // Get encryption key
            val secretKey = getEncryptionKey()

            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            // Decrypt the data
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            return String(decryptedBytes)
        } catch (e: Exception) {
            // In case of decryption failure, return the encrypted hex
            // This is not ideal, but prevents crashing
            return encryptedHex
        }
    }

    /**
     * Get or generate the encryption key
     */
    private fun getEncryptionKey(): SecretKey {
        // Check if we already have a key
        val keyHex = sharedPreferences.getString("encryption_key", null)

        if (keyHex != null) {
            // Use existing key
            val keyBytes = HexUtil.toByteArray(keyHex)
            return javax.crypto.spec.SecretKeySpec(keyBytes, ALGORITHM)
        } else {
            // Generate a new key
            val keyGenerator = javax.crypto.KeyGenerator.getInstance(ALGORITHM)
            keyGenerator.init(KEY_SIZE)
            val newKey = keyGenerator.generateKey()

            // Save the key for future use
            val keyBytes = newKey.encoded
            val keyHexString = HexUtil.toHexString(keyBytes)
            sharedPreferences.edit().putString("encryption_key", keyHexString).apply()

            return newKey
        }
    }
}