package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Attendee
import com.example.data.Event
import com.example.data.AttendanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AttendanceRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AttendanceRepository(database.attendanceDao())
    }

    val events = repository.allEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent = _selectedEvent.asStateFlow()

    // Collect attendees for selected event
    val attendees = _selectedEvent.flatMapLatest { event ->
        if (event != null) {
            repository.getAttendeesForEvent(event.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _scanFeedback = MutableStateFlow<ScanFeedback?>(null)
    val scanFeedback = _scanFeedback.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectEvent(event: Event?) {
        _selectedEvent.value = event
        _scanFeedback.value = null
    }

    fun clearFeedback() {
        _scanFeedback.value = null
    }

    fun createEvent(name: String, date: String, location: String, description: String) {
        viewModelScope.launch {
            val eventId = repository.insertEvent(Event(name = name, date = date, location = location, description = description))
            // Retrieve created event and set as active
            val created = repository.getEventById(eventId)
            if (created != null) {
                _selectedEvent.value = created
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            if (_selectedEvent.value?.id == event.id) {
                _selectedEvent.value = null
            }
            repository.deleteEvent(event)
        }
    }

    fun registerAttendee(name: String, email: String, department: String, barcode: String) {
        val currentEvent = _selectedEvent.value ?: return
        viewModelScope.launch {
            repository.insertAttendee(
                Attendee(
                    eventId = currentEvent.id,
                    barcode = barcode.trim(),
                    name = name,
                    email = email,
                    department = department
                )
            )
        }
    }

    fun deleteAttendee(attendee: Attendee) {
        viewModelScope.launch {
            repository.deleteAttendee(attendee)
        }
    }

    fun toggleCheckIn(attendee: Attendee) {
        viewModelScope.launch {
            val updated = if (attendee.checkInTime == null) {
                attendee.copy(checkInTime = System.currentTimeMillis())
            } else {
                attendee.copy(checkInTime = null)
            }
            repository.updateAttendee(updated)
        }
    }

    fun resetAttendance() {
        val currentEvent = _selectedEvent.value ?: return
        viewModelScope.launch {
            repository.resetAttendance(currentEvent.id)
        }
    }

    // Main scanning / manual check-in process
    fun processBarcode(barcode: String) {
        val currentEvent = _selectedEvent.value
        if (currentEvent == null) {
            _scanFeedback.value = ScanFeedback("Please select or create an event first", false)
            return
        }
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isEmpty()) {
            _scanFeedback.value = ScanFeedback("Barcode cannot be empty", false)
            return
        }

        viewModelScope.launch {
            val attendee = repository.getAttendeeByBarcode(currentEvent.id, cleanBarcode)
            if (attendee != null) {
                if (attendee.checkInTime != null) {
                    val timeStr = formatTimeShort(attendee.checkInTime)
                    _scanFeedback.value = ScanFeedback("${attendee.name} was already checked in at $timeStr", true)
                } else {
                    val now = System.currentTimeMillis()
                    repository.updateAttendee(attendee.copy(checkInTime = now))
                    _scanFeedback.value = ScanFeedback("Check-in successful: ${attendee.name}", true)
                }
            } else {
                // Not found in database -> Show feedback and add them automatically on the fly as Walk-In!
                val now = System.currentTimeMillis()
                val walkInAttendee = Attendee(
                    eventId = currentEvent.id,
                    barcode = cleanBarcode,
                    name = "Walk-In ($cleanBarcode)",
                    checkInTime = now,
                    isWalkIn = true
                )
                repository.insertAttendee(walkInAttendee)
                _scanFeedback.value = ScanFeedback("Checked-In as Walk-In Code: $cleanBarcode", true)
            }
        }
    }

    // Pre-populates a demo event for quick evaluation
    fun populateDemoEvent() {
        viewModelScope.launch {
            val eventId = repository.insertEvent(
                Event(
                    name = "Annual Tech Summit 2026",
                    date = "May 25, 2026",
                    location = "Main Conference Center",
                    description = "Keynote presentations on modern Android, Jetpack Compose performance, and system design."
                )
            )
            val demoAttendees = listOf(
                Attendee(eventId = eventId, barcode = "ATC-001", name = "Le Anh", email = "leanh0094@gmail.com", department = "Engineering"),
                Attendee(eventId = eventId, barcode = "ATC-002", name = "Jane Doe", email = "jane.doe@example.com", department = "Product Management"),
                Attendee(eventId = eventId, barcode = "ATC-003", name = "Bob Smith", email = "bob.smith@example.com", department = "Marketing"),
                Attendee(eventId = eventId, barcode = "ATC-004", name = "Alice Johnson", email = "alice.j@example.com", department = "UI/UX Design"),
                Attendee(eventId = eventId, barcode = "ATC-005", name = "Charlie Brown", email = "charlie@example.com", department = "Developer Relations")
            )
            repository.insertAttendees(demoAttendees)
            
            // Auto select
            val created = repository.getEventById(eventId)
            if (created != null) {
                _selectedEvent.value = created
            }
        }
    }

    // Helper functions for CSV formatting
    fun generateCsvContent(): String {
        val currentEvent = _selectedEvent.value ?: return ""
        val list = attendees.value
        val sb = StringBuilder()
        
        // Header
        sb.append("Ticket/Barcode,Full Name,Email,Department,Status,Check-In Time,Type\n")
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        for (a in list) {
            val status = if (a.checkInTime != null) "Checked-In" else "Absent"
            val timeStr = if (a.checkInTime != null) dateFormat.format(Date(a.checkInTime)) else ""
            val type = if (a.isWalkIn) "Walk-In Registrant" else "Pre-Registered"
            
            // Escape CSV values
            val escapedBarcode = escapeCsv(a.barcode)
            val escapedName = escapeCsv(a.name)
            val escapedEmail = escapeCsv(a.email)
            val escapedDept = escapeCsv(a.department)
            
            sb.append("$escapedBarcode,$escapedName,$escapedEmail,$escapedDept,$status,$timeStr,$type\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    private fun formatTimeShort(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class ScanFeedback(
    val message: String,
    val success: Boolean
)
