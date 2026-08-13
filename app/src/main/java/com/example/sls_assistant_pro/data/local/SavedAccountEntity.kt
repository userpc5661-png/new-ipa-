package com.example.sls_assistant_pro.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_accounts")
data class SavedAccountEntity(
    @PrimaryKey val email: String,
    val name: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

@Dao
interface SavedAccountDao {
    @Query("SELECT * FROM saved_accounts ORDER BY savedAt DESC")
    fun getAllAccounts(): Flow<List<SavedAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: SavedAccountEntity)

    @Query("DELETE FROM saved_accounts WHERE email = :email")
    suspend fun deleteAccount(email: String)
}
