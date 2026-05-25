package com.example.data

import kotlinx.coroutines.flow.Flow

class AttendanceRepository(private val attendanceDao: AttendanceDao) {
    val allEvents: Flow<List<Event>> = attendanceDao.getAllEvents()

    suspend fun getEventById(id: Long): Event? = attendanceDao.getEventById(id)

    suspend fun insertEvent(event: Event): Long = attendanceDao.insertEvent(event)

    suspend fun deleteEvent(event: Event) = attendanceDao.deleteEvent(event)

    fun getAttendeesForEvent(eventId: Long): Flow<List<Attendee>> = 
        attendanceDao.getAttendeesForEvent(eventId)

    suspend fun getAttendeeByBarcode(eventId: Long, barcode: String): Attendee? =
        attendanceDao.getAttendeeByBarcode(eventId, barcode)

    suspend fun getAttendeeById(id: Long): Attendee? =
        attendanceDao.getAttendeeById(id)

    suspend fun insertAttendee(attendee: Attendee): Long = attendanceDao.insertAttendee(attendee)

    suspend fun insertAttendees(attendees: List<Attendee>) = attendanceDao.insertAttendees(attendees)

    suspend fun updateAttendee(attendee: Attendee) = attendanceDao.updateAttendee(attendee)

    suspend fun deleteAttendee(attendee: Attendee) = attendanceDao.deleteAttendee(attendee)

    suspend fun resetAttendance(eventId: Long) {
        attendanceDao.resetAttendanceForEvent(eventId)
        attendanceDao.deleteWalkInsForEvent(eventId)
    }
}
