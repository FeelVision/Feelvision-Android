package com.feelvision.data.people

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PeopleDao {
    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllFlow(): Flow<List<PersonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity): Long

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteById(id: Long)
}