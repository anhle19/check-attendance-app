package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Attendee
import com.example.data.Event
import com.example.ui.AttendanceViewModel
import com.example.ui.ScanFeedback
import com.example.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    AttendanceAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AttendanceAppScreen(
    modifier: Modifier = Modifier,
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsStateWithLifecycle()
    val selectedEvent by viewModel.selectedEvent.collectAsStateWithLifecycle()
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val scanFeedback by viewModel.scanFeedback.collectAsStateWithLifecycle()

    var showCreateEventDialog by remember { mutableStateOf(false) }
    var showCreateAttendeeDialog by remember { mutableStateOf(false) }
    var barcodeInput by remember { mutableStateOf("") }
    var showEventSelectorDropdown by remember { mutableStateOf(false) }

    // Clear feedback automatically after 4 seconds
    LaunchedEffect(scanFeedback) {
        if (scanFeedback != null) {
            delay(4000)
            viewModel.clearFeedback()
        }
    }

    // CSV Document Creation contract launcher
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csvContent = viewModel.generateCsvContent()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Attendance report saved successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        AppHeader(
            events = events,
            selectedEvent = selectedEvent,
            onEventSelected = {
                viewModel.selectEvent(it)
                showEventSelectorDropdown = false
            },
            onDropdownToggle = { showEventSelectorDropdown = !showEventSelectorDropdown },
            isDropdownExpanded = showEventSelectorDropdown,
            onCreateEventClick = {
                showCreateEventDialog = true
                showEventSelectorDropdown = false
            },
            onPopulateDemoClick = {
                viewModel.populateDemoEvent()
                showEventSelectorDropdown = false
            }
        )

        if (selectedEvent == null) {
            // Empty State Dashboard (Select/Create Event first)
            EmptyStateDashboard(
                eventsExist = events.isNotEmpty(),
                onCreateEventClick = { showCreateEventDialog = true },
                onPopulateDemoClick = { viewModel.populateDemoEvent() }
            )
        } else {
            val event = selectedEvent!!
            
            // Statistics Bar with details
            StatisticsBar(
                attendees = attendees,
                event = event,
                onDeleteEvent = { viewModel.deleteEvent(event) },
                onResetAttendance = { viewModel.resetAttendance() }
            )

            // Scanning / Manual Entries Box Section
            ScanControlsSection(
                barcodeInput = barcodeInput,
                onBarcodeChange = { barcodeInput = it },
                onCheckInClick = {
                    viewModel.processBarcode(barcodeInput)
                    barcodeInput = ""
                },
                onScanCameraClick = {
                    try {
                        val scanner = GmsBarcodeScanning.getClient(context)
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                barcode.rawValue?.let { scannedCode ->
                                    viewModel.processBarcode(scannedCode)
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Scanner fail: ${e.localizedMessage}. Please use input/simulator below.", Toast.LENGTH_LONG).show()
                            }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Google Play services scanner unavailable. Please use input/simulator fallback.", Toast.LENGTH_LONG).show()
                    }
                },
                scanFeedback = scanFeedback,
                onExportCsvClick = {
                    val defaultFileName = "attendance_report_${event.name.lowercase().replace(" ", "_")}.csv"
                    csvExportLauncher.launch(defaultFileName)
                }
            )

            // Screen Separator line
            HorizontalDivider()

            // Main body section (Search filter + Attendee list)
            AttendeeListSection(
                modifier = Modifier.weight(1f),
                attendees = attendees,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onAttendeeClick = { viewModel.toggleCheckIn(it) },
                onDeleteAttendee = { viewModel.deleteAttendee(it) },
                onCreateAttendeeClick = { showCreateAttendeeDialog = true }
            )
        }
    }

    // Dialogs setup
    if (showCreateEventDialog) {
        CreateEventDialog(
            onDismiss = { showCreateEventDialog = false },
            onConfirm = { name, date, location, desc ->
                viewModel.createEvent(name, date, location, desc)
                showCreateEventDialog = false
            }
        )
    }

    if (showCreateAttendeeDialog) {
        CreateAttendeeDialog(
            onDismiss = { showCreateAttendeeDialog = false },
            onConfirm = { name, email, department, barcode ->
                viewModel.registerAttendee(name, email, department, barcode)
                showCreateAttendeeDialog = false
            }
        )
    }
}

@Composable
fun AppHeader(
    events: List<Event>,
    selectedEvent: Event?,
    onEventSelected: (Event) -> Unit,
    onDropdownToggle: () -> Unit,
    isDropdownExpanded: Boolean,
    onCreateEventClick: () -> Unit,
    onPopulateDemoClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Active Event Info Row
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDropdownToggle() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu icon",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = selectedEvent?.name ?: "Global Tech Summit",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isDropdownExpanded) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = "Dropdown Trigger",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { onDropdownToggle() },
                    modifier = Modifier.testTag("event_selector")
                ) {
                    DropdownMenuItem(
                        text = { Text("➕ Create New Event", fontWeight = FontWeight.SemiBold) },
                        onClick = onCreateEventClick
                    )
                    
                    if (events.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("🪄 Populate Demo Summit Event") },
                            onClick = onPopulateDemoClick
                        )
                    } else {
                        HorizontalDivider()
                        events.forEach { event ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(event.name, fontWeight = FontWeight.SemiBold)
                                        Text(event.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                },
                                onClick = { onEventSelected(event) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = "Event Icon",
                                        tint = if (selectedEvent?.id == event.id) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Easy Action Quick Buttons
            Row {
                IconButton(onClick = onCreateEventClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Event",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateDashboard(
    eventsExist: Boolean,
    onCreateEventClick: () -> Unit,
    onPopulateDemoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large styled camera icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "App Logo Barcode",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Welcome to Attendance Scanner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Manage event tickets, scan QR/barcodes on check-in, register walk-ins, and export complete CSV reports directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onCreateEventClick,
                    modifier = Modifier.fillMaxWidth().testTag("add_event_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Event")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onPopulateDemoClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Auto")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Populate Demo Technical Summit")
                }
            }
        }
    }
}

@Composable
fun StatisticsBar(
    attendees: List<Attendee>,
    event: Event,
    onDeleteEvent: () -> Unit,
    onResetAttendance: () -> Unit
) {
    val totalPreRegistered = attendees.count { !it.isWalkIn }
    val checkedInCount = attendees.count { it.checkInTime != null }
    val walkInsCount = attendees.count { it.isWalkIn }
    val totalAttendees = attendees.size
    val percentPresent = if (totalAttendees > 0) (checkedInCount * 100) / totalAttendees else 0

    Column(modifier = Modifier.padding(12.dp)) {
        // Event Title & Actions Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = "Date", modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Text(event.date, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (event.location.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Place, contentDescription = "Loc", modifier = Modifier.size(12.dp), tint = Color.Gray)
                        Text(event.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            
            Row {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("🔄 Reset Checked-In Guest List", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onResetAttendance()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("❌ Delete This Event", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDeleteEvent()
                            showMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Sleek Summary Stats Card (Matches bg-[#F3EEF4] and rounded-3xl p-6)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Number stats on left
                Column {
                    Text(
                        text = "Total Attendance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color(0xFF21005D))) {
                                append(checkedInCount.toString())
                            }
                            withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(" / $totalAttendees")
                            }
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Sleek border percentage circle on right
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { percentPresent.toFloat() / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    Text(
                        text = "$percentPresent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        
        // Compact Breakdown Stat Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Walk-Ins",
                value = walkInsCount.toString(),
                color = MaterialTheme.colorScheme.secondary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Pre-Registered",
                value = totalPreRegistered.toString(),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun ScanControlsSection(
    barcodeInput: String,
    onBarcodeChange: (String) -> Unit,
    onCheckInClick: () -> Unit,
    onScanCameraClick: () -> Unit,
    scanFeedback: ScanFeedback?,
    onExportCsvClick: () -> Unit
) {
    val context = LocalContext.current
    var isFlashlightOn by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Viewfinder Mock (Matches bg-[#1D1B20] rounded-[32px])
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1D1B20)),
                contentAlignment = Alignment.Center
            ) {
                // Viewfinder Corners Mock (8dp lengths, white, alpha 0.4)
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Top-Left corner lines
                    Box(modifier = Modifier.align(Alignment.TopStart)) {
                        Row {
                            Box(modifier = Modifier.size(16.dp, 4.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                        Column {
                            Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                    }
                    // Top-Right corner lines
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        Row(modifier = Modifier.align(Alignment.TopEnd)) {
                            Box(modifier = Modifier.size(16.dp, 4.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                        Column(modifier = Modifier.align(Alignment.TopEnd)) {
                            Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                    }
                    // Bottom-Left corner lines
                    Box(modifier = Modifier.align(Alignment.BottomStart)) {
                        Row(modifier = Modifier.align(Alignment.BottomStart)) {
                            Box(modifier = Modifier.size(16.dp, 4.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                        Column(modifier = Modifier.align(Alignment.BottomStart)) {
                            Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                    }
                    // Bottom-Right corner lines
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        Row(modifier = Modifier.align(Alignment.BottomEnd)) {
                            Box(modifier = Modifier.size(16.dp, 4.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                        Column(modifier = Modifier.align(Alignment.BottomEnd)) {
                            Box(modifier = Modifier.size(4.dp, 16.dp).background(Color.White.copy(alpha = 0.45f)))
                        }
                    }
                }

                // Dynamic Animated Scanner Laser Beam line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.dp)
                        .background(Color(0xFFD0BCFF))
                        .align(Alignment.Center)
                )

                // Label and Icon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "QR Viewfinder Center",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Align barcode within frame",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Simulated Flash button (bg-white/10 active:bg-white/20)
                IconButton(
                    onClick = {
                        isFlashlightOn = !isFlashlightOn
                        val term = if (isFlashlightOn) "ON" else "OFF"
                        Toast.makeText(context, "Flashlight Simulator: $term", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Simulate Flash",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Station Headers
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Check-In Control Station",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "MANUAL ENTRY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Manual input text field
                TextField(
                    value = barcodeInput,
                    onValueChange = { onBarcodeChange(it) },
                    placeholder = { Text("Enter Barcode / Ticket ID") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("barcode_input_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onAny = {
                            if (barcodeInput.isNotEmpty()) {
                                onCheckInClick()
                            }
                        }
                    )
                )

                // Confirm input Check-In button
                Button(
                    onClick = onCheckInClick,
                    enabled = barcodeInput.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("check_in_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Check-In")
                }

                // Camera Scan button
                Button(
                    onClick = onScanCameraClick,
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("scan_camera_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Camera Scanner Button")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row: Export Report
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onExportCsvClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_csv_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV Report", fontSize = 13.sp)
                }
            }

            // Real-time scan result alert with animated visibility
            AnimatedVisibility(
                visible = scanFeedback != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (scanFeedback != null) {
                    val isSuccess = scanFeedback.success
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            )
                            .border(
                                1.dp,
                                if (isSuccess) Color(0xFFC8E6C9) else Color(0xFFFFCDD2),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = if (isSuccess) "Success Status" else "Error Status",
                                tint = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = scanFeedback.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendeeListSection(
    modifier: Modifier = Modifier,
    attendees: List<Attendee>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAttendeeClick: (Attendee) -> Unit,
    onDeleteAttendee: (Attendee) -> Unit,
    onCreateAttendeeClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Attendees List",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(
                onClick = onCreateAttendeeClick,
                modifier = Modifier.testTag("add_attendee_btn")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Attendee icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Attendee", fontSize = 13.sp)
            }
        }

        // Search Bar Row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by name, email, department") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search query")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // Filter and compile list
        val filteredAttendees = attendees.filter { attendee ->
            attendee.name.contains(searchQuery, ignoreCase = true) ||
            attendee.barcode.contains(searchQuery, ignoreCase = true) ||
            attendee.email.contains(searchQuery, ignoreCase = true) ||
            attendee.department.contains(searchQuery, ignoreCase = true)
        }

        if (filteredAttendees.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "Empty list",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results matching research" else "Pre-registered attendee list is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onCreateAttendeeClick) {
                            Text("Pre-register First Attendee")
                        }
                    }
                }
            }
        } else {
            // Simulator Section Helper Header (Only if pre-registered exists to simulated easily)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "📱 Web Emulator Scanning Simulator",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Tap on any attendee item's status pill below to simulate scanning their barcode (ATC-00X) instantly!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredAttendees, key = { it.id }) { attendee ->
                    AttendeeRowItem(
                        attendee = attendee,
                        onClick = { onAttendeeClick(attendee) },
                        onDeleteClick = { onDeleteAttendee(attendee) }
                    )
                }
            }
        }
    }
}

@Composable
fun AttendeeRowItem(
    attendee: Attendee,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val initials = remember(attendee.name) {
        attendee.name.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (attendee.checkInTime != null) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant circular initials avatar (matches background / colors)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (attendee.checkInTime != null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (attendee.checkInTime != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details section
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = attendee.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (attendee.isWalkIn) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFFFF3E0))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Walk-In",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Subtitles: time checked in + mode
                val subtitleText = if (attendee.checkInTime != null) {
                    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                    val checkedInTimeStr = sdf.format(Date(attendee.checkInTime))
                    "Checked in $checkedInTimeStr • ${if (attendee.isWalkIn) "Manual" else "Barcode"}"
                } else {
                    listOfNotNull(
                        attendee.department.takeIf { it.isNotEmpty() },
                        "Not registered yet"
                    ).joinToString(" • ")
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (attendee.checkInTime == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        BarcodeIcon(modifier = Modifier.width(22.dp).height(8.dp))
                        Text(
                            text = attendee.barcode,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action section & Check indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (attendee.checkInTime != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Checked In Circle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            .clickable { onClick() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Simulate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Guest Icon",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BarcodeIcon(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barWidths = listOf(1.5.dp, 3.dp, 1.dp, 2.5.dp, 1.dp, 3.dp, 1.5.dp, 1.dp, 2.dp, 1.5.dp)
        barWidths.forEach { width ->
            Box(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .background(Color.DarkGray.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun CreateEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    LaunchedEffect(Unit) {
        date = sdf.format(Date())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Create Event Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Event Name*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (e.g., May 25, 2026)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (Venue/Hall)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name, date, location, description) },
                        enabled = name.trim().isNotEmpty()
                    ) {
                        Text("Create Event")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateAttendeeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }

    // Auto generate a mock barcode based on standard conference numbers
    LaunchedEffect(Unit) {
        val randomNum = (100..999).random()
        barcode = "ATC-$randomNum"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Pre-Register Attendee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department / Company") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Ticket Barcode / Code*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g., ATC-101") }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name, email, department, barcode) },
                        enabled = name.trim().isNotEmpty() && barcode.trim().isNotEmpty()
                    ) {
                        Text("Register Guest")
                    }
                }
            }
        }
    }
}
