package com.waynejiang.linefeed.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per [com.waynejiang.linefeed.core.domain.model.ContentSource]; updated in the same transaction as its data. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val source: String,
    val lastSuccessAtMillis: Long?,
    val lastAttemptAtMillis: Long?,
    val lastError: String?,
)
