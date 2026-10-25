package com.syntaxa.khata.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.syntaxa.khata.data.local.entity.DailyCashClosingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCashClosingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveClosing(closing: DailyCashClosingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAllClosings(closings: List<DailyCashClosingEntity>)

    @Query("SELECT * FROM daily_cash_closings WHERE closingDateEpoch = :closingDateEpoch LIMIT 1")
    suspend fun getClosingForDate(closingDateEpoch: Long): DailyCashClosingEntity?

    @Query("SELECT * FROM daily_cash_closings ORDER BY closingDateEpoch DESC LIMIT :limit")
    fun observeRecentClosings(limit: Int = 30): Flow<List<DailyCashClosingEntity>>

    @Query("SELECT * FROM daily_cash_closings")
    suspend fun getAllClosingsRaw(): List<DailyCashClosingEntity>

    @Query("DELETE FROM daily_cash_closings")
    suspend fun deleteAllClosings()
}
