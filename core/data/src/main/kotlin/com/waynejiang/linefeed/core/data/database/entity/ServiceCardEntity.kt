package com.waynejiang.linefeed.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_cards")
data class ServiceCardEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val ctaLabel: String,
    val actionUrl: String,
    val position: Int,
)
