package com.example.sls_assistant_pro.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "location_corrections")
data class LocationCorrectionEntity(
    @PrimaryKey val referenceNumber: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface LocationCorrectionDao {
    @Query("SELECT * FROM location_corrections")
    fun getAllCorrections(): Flow<List<LocationCorrectionEntity>>

    @Query("SELECT * FROM location_corrections WHERE referenceNumber = :ref LIMIT 1")
    suspend fun getCorrection(ref: String): LocationCorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCorrection(correction: LocationCorrectionEntity)
}
