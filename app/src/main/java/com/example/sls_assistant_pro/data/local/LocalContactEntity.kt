package com.example.sls_assistant_pro.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_contact")
data class LocalContactEntity(
    @PrimaryKey val taskKey: String, // referenceNumber or id
    val status: String, // "answered", "no_answer", "none"
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Dao
interface LocalContactDao {
    @Query("SELECT * FROM local_contact")
    fun getAllContacts(): Flow<List<LocalContactEntity>>

    @Query("SELECT * FROM local_contact WHERE taskKey = :taskKey LIMIT 1")
    suspend fun getContact(taskKey: String): LocalContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setContact(contact: LocalContactEntity)

    @Query("DELETE FROM local_contact WHERE taskKey = :taskKey")
    suspend fun deleteContact(taskKey: String)
}
