package com.ethran.notable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TagPriorityDao {
    @Query("SELECT * FROM tag_priority ORDER BY sortOrder ASC")
    suspend fun getAllSorted(): List<TagPriority>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tagPriority: TagPriority)

    @Query("DELETE FROM tag_priority WHERE tagName = :tagName")
    suspend fun delete(tagName: String)
}