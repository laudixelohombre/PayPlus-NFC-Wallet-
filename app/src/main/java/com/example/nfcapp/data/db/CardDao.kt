package com.example.nfcapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nfcapp.data.model.Card
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Card entity.
 * Handles all database operations for cards.
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