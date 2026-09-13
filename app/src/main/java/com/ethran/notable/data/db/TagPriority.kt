package com.ethran.notable.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Priority/sort order for a topic (tag) on the Home screen.
 * sortOrder 0 = hidden, 1+ = display order.
 */
@Entity(tableName = "tag_priority")
data class TagPriority(
    @PrimaryKey val tagName: String,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0
)