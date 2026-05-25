package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,
    val location: String = "",
    val description: String = ""
)

@Entity(
    tableName = "attendees",
    indices = [
        Index(value = ["eventId", "barcode"], unique = true)
    ]
)
data class Attendee(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val barcode: String,
    val name: String,
    val email: String = "",
    val department: String = "",
    val checkInTime: Long? = null,
    val isWalkIn: Boolean = false
)
