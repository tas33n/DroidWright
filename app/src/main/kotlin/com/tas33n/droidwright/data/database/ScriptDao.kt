/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun getAllScripts(): Flow<List<ScriptEntity>>
    
    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getScriptById(id: String): ScriptEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity)
    
    @Update
    suspend fun updateScript(script: ScriptEntity)
    
    @Delete
    suspend fun deleteScript(script: ScriptEntity)
    
    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteScriptById(id: String)
    
    @Query("UPDATE scripts SET isRunning = :isRunning, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateRunningState(id: String, isRunning: Boolean, timestamp: Long)

    @Query("UPDATE scripts SET lastExecuted = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateLastExecuted(id: String, timestamp: Long)
}

