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
import com.example.nfcapp.emv.TransactionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for transaction data storage and retrieval.
 *
 * This class provides methods for storing and retrieving transaction history
 * in the Room database. Each transaction includes details like amount, date,
 * merchant information, and approval status.
 */
class TransactionRepository private constructor(context: Context) {

    private val transactionDao: TransactionDao

    init {
        // Initialize Room database
        val database = TransactionDatabase.getInstance(context)
        transactionDao = database.transactionDao()
    }

    /**
     * Get all transactions as a Flow
     */
    fun getAllTransactions(): Flow<List<TransactionData>> {
        return transactionDao.getAllTransactions()
    }

    /**
     * Get transactions for a specific card
     */
    fun getTransactionsForCard(cardId: Long): Flow<List<TransactionData>> {
        return transactionDao.getTransactionsForCard(cardId)
    }

    /**
     * Get transaction by ID
     */
    suspend fun getTransactionById(id: Long): TransactionData? {
        return transactionDao.getTransactionById(id)
    }

    /**
     * Insert a new transaction
     */
    suspend fun insertTransaction(transaction: TransactionData): Long =
        withContext(Dispatchers.IO) {
            // Update the card's last transaction time
            if (transaction.cardId > 0) {
                val cardRepository = CardRepository.getInstance(context)
                val card = cardRepository.getCardById(transaction.cardId)

                card?.let {
                    it.lastTransaction = transaction.timestamp
                    it.lastUsed = transaction.timestamp
                    cardRepository.updateCard(it)
                }
            }

            // Insert the transaction
            return@withContext transactionDao.insertTransaction(transaction)
        }

    /**
     * Update an existing transaction
     */
    suspend fun updateTransaction(transaction: TransactionData) = withContext(Dispatchers.IO) {
        transactionDao.updateTransaction(transaction)
    }

    /**
     * Delete a transaction
     */
    suspend fun deleteTransaction(transaction: TransactionData) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransaction(transaction)
    }

    /**
     * Get recent transactions (limit by count)
     */
    fun getRecentTransactions(limit: Int): Flow<List<TransactionData>> {
        return transactionDao.getRecentTransactions(limit)
    }

    /**
     * Clear all transactions
     */
    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        transactionDao.clearAllTransactions()
    }

    companion object {
        @Volatile
        private var INSTANCE: TransactionRepository? = null

        fun getInstance(context: Context): TransactionRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TransactionRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * DAO (Data Access Object) for Transaction entity
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionData>>

    @Query("SELECT * FROM transactions WHERE cardId = :cardId ORDER BY timestamp DESC")
    fun getTransactionsForCard(cardId: Long): Flow<List<TransactionData>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionData?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<TransactionData>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionData): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionData)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionData)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()
}

/**
 * Room database for storing transactions
 */
@Database(entities = [TransactionData::class], version = 1, exportSchema = false)
abstract class TransactionDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: TransactionDatabase? = null

        fun getInstance(context: Context): TransactionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransactionDatabase::class.java,
                    "transaction_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}