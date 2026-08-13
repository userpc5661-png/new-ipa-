package com.example.sls_assistant_pro.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "delivery_history")
data class DeliveryHistoryEntity(
    @PrimaryKey val awb: String,
    val customerName: String,
    val codAmount: Double,
    val collected: Boolean = true,
    val dateString: String, // YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface DeliveryHistoryDao {
    @Query("SELECT * FROM delivery_history WHERE dateString = :dateString ORDER BY timestamp DESC")
    fun getHistoryForDate(dateString: String): Flow<List<DeliveryHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDelivery(record: DeliveryHistoryEntity)
}
