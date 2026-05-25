package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    // Event actions
    @Query("SELECT * FROM events ORDER BY id DESC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Long): Event?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Delete
    suspend fun deleteEvent(event: Event)

    // Attendee actions
    @Query("SELECT * FROM attendees WHERE eventId = :eventId ORDER BY name ASC")
    fun getAttendeesForEvent(eventId: Long): Flow<List<Attendee>>

    @Query("SELECT * FROM attendees WHERE eventId = :eventId AND barcode = :barcode LIMIT 1")
    suspend fun getAttendeeByBarcode(eventId: Long, barcode: String): Attendee?

    @Query("SELECT * FROM attendees WHERE id = :id")
    suspend fun getAttendeeById(id: Long): Attendee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendee(attendee: Attendee): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendees(attendees: List<Attendee>)

    @Update
    suspend fun updateAttendee(attendee: Attendee)

    @Delete
    suspend fun deleteAttendee(attendee: Attendee)

    @Query("UPDATE attendees SET checkInTime = NULL, isWalkIn = 0 WHERE eventId = :eventId")
    suspend fun resetAttendanceForEvent(eventId: Long)

    @Query("DELETE FROM attendees WHERE eventId = :eventId AND isWalkIn = 1")
    suspend fun deleteWalkInsForEvent(eventId: Long)
}
