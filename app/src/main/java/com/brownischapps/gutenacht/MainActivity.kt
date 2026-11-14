package com.brownischapps.gutenacht

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

data class SnoreSession(
    val dir: File,
    val startDate: Date,
    val startTimeStr: String,
    val endTimeStr: String,
    val totalSecs: Int,
    val snoreSecs: Int,
    val score: Int,
    val chartPoints: List<Int>,
    val combinedAudio: File?,
    val eventFiles: List<File>,
    val isArchived: Boolean
)

data class SnoreEventItem(
    val file: File,
    val dateStr: String,
    val timeStr: String,
    val db: Int,
    val durationSecs: Int,
    val durationStr: String,
    val chartPoints: List<Int>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("gutenacht_prefs", Context.MODE_PRIVATE)

        setContent {
            var isDarkMode by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
            var isTestMode by rememberSaveable { mutableStateOf(prefs.getBoolean("test_mode", false)) }

            // Punkt 1: Neue optimierte Standardwerte (55 dB Schwellenwert & 8 Sek. Nachlaufzeit)
            var threshold by rememberSaveable { mutableFloatStateOf(prefs.getFloat("threshold_v2", 55f)) }
            var sleepDelayMinutes by rememberSaveable { mutableIntStateOf(prefs.getInt("sleep_delay_minutes", 20)) }
            var silenceToleranceSecs by rememberSaveable { mutableIntStateOf(prefs.getInt("silence_tolerance_v2", 8)) }
            var wakeDetectionEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("wake_detection", true)) }
            var maxDurationHours by rememberSaveable { mutableIntStateOf(prefs.getInt("max_duration_hours", 10)) }

            // Punkt 4: Sanfter Anti-Schnarch-Impuls direkt in der Haupt-App
            var antiSnorePulse by rememberSaveable { mutableStateOf(prefs.getBoolean("anti_snore_pulse", false)) }

            // Benachrichtigungs-Einstellungen
            var notifyShowLiveCount by rememberSaveable { mutableStateOf(prefs.getBoolean("notify_live_count", true)) }
            var notifyWakeWithSound by rememberSaveable { mutableStateOf(prefs.getBoolean("notify_wake_sound", true)) }
            var notifyMorningSummary by rememberSaveable { mutableStateOf(prefs.getBoolean("notify_morning_summary", true)) }

            // Gesten-Einstellungen
            var swipeGesturesEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("swipe_gestures_enabled", true)) }
            var swapSwipeDirections by rememberSaveable { mutableStateOf(prefs.getBoolean("swap_swipe_directions", false)) }
            var longPressMenuEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("long_press_menu_enabled", true)) }

            val bgColor = if (isDarkMode) Color(0xFF14171F) else Color(0xFFF4F6FA)
            val cardColor = if (isDarkMode) Color(0xFF1E222D) else Color(0xFFFFFFFF)
            val textColor = if (isDarkMode) Color.White else Color(0xFF1A1C24)
            val subTextColor = if (isDarkMode) Color(0xFF8E95A5) else Color(0xFF5A6275)
            val accentColor = Color(0xFF4FC3F7)

            MaterialTheme(
                colorScheme = if (isDarkMode) {
                    darkColorScheme(background = bgColor, surface = cardColor, primary = accentColor, onSurface = textColor)
                } else {
                    lightColorScheme(background = bgColor, surface = cardColor, primary = accentColor, onSurface = textColor)
                }
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
                    MainScreen(
                        isDarkMode = isDarkMode,
                        onDarkModeChange = {
                            isDarkMode = it
                            prefs.edit().putBoolean("dark_mode", it).apply()
                        },
                        isTestMode = isTestMode,
                        onTestModeChange = {
                            isTestMode = it
                            prefs.edit().putBoolean("test_mode", it).apply()
                        },
                        threshold = threshold,
                        onThresholdChange = {
                            threshold = it
                            prefs.edit().putFloat("threshold_v2", it).apply()
                        },
                        sleepDelayMinutes = sleepDelayMinutes,
                        onSleepDelayChange = {
                            sleepDelayMinutes = it
                            prefs.edit().putInt("sleep_delay_minutes", it).apply()
                        },
                        silenceToleranceSecs = silenceToleranceSecs,
                        onSilenceToleranceChange = {
                            silenceToleranceSecs = it
                            prefs.edit().putInt("silence_tolerance_v2", it).apply()
                        },
                        wakeDetectionEnabled = wakeDetectionEnabled,
                        onWakeDetectionChange = {
                            wakeDetectionEnabled = it
                            prefs.edit().putBoolean("wake_detection", it).apply()
                        },
                        maxDurationHours = maxDurationHours,
                        onMaxDurationHoursChange = {
                            maxDurationHours = it
                            prefs.edit().putInt("max_duration_hours", it).apply()
                        },
                        antiSnorePulse = antiSnorePulse,
                        onAntiSnorePulseChange = {
                            antiSnorePulse = it
                            prefs.edit().putBoolean("anti_snore_pulse", it).apply()
                        },
                        notifyShowLiveCount = notifyShowLiveCount,
                        onNotifyShowLiveCountChange = {
                            notifyShowLiveCount = it
                            prefs.edit().putBoolean("notify_live_count", it).apply()
                        },
                        notifyWakeWithSound = notifyWakeWithSound,
                        onNotifyWakeWithSoundChange = {
                            notifyWakeWithSound = it
                            prefs.edit().putBoolean("notify_wake_sound", it).apply()
                        },
                        notifyMorningSummary = notifyMorningSummary,
                        onNotifyMorningSummaryChange = {
                            notifyMorningSummary = it
                            prefs.edit().putBoolean("notify_morning_summary", it).apply()
                        },
                        swipeGesturesEnabled = swipeGesturesEnabled,
                        onSwipeGesturesEnabledChange = {
                            swipeGesturesEnabled = it
                            prefs.edit().putBoolean("swipe_gestures_enabled", it).apply()
                        },
                        swapSwipeDirections = swapSwipeDirections,
                        onSwapSwipeDirectionsChange = {
                            swapSwipeDirections = it
                            prefs.edit().putBoolean("swap_swipe_directions", it).apply()
                        },
                        longPressMenuEnabled = longPressMenuEnabled,
                        onLongPressMenuEnabledChange = {
                            longPressMenuEnabled = it
                            prefs.edit().putBoolean("long_press_menu_enabled", it).apply()
                        },
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardColor = cardColor,
                        bgColor = bgColor,
                        onStartRecord = {
                            startSnoreService(
                                threshold = threshold,
                                testMode = isTestMode,
                                delayMinutes = sleepDelayMinutes,
                                silenceToleranceSecs = silenceToleranceSecs,
                                wakeDetection = wakeDetectionEnabled,
                                maxHours = maxDurationHours
                            )
                        },
                        onStopRecord = { stopSnoreService() },
                        onDismissWakeAlert = { dismissWakeAlertInService() },
                        loadSessions = { getSessions() },
                        onToggleArchiveSession = { session -> toggleArchiveSession(session) },
                        onDeleteSession = { session -> deleteSession(session) },
                        onDeleteSingleEvent = { eventFile -> deleteSingleEventFile(eventFile) },
                        onDeleteAllSessions = { includeArchive -> deleteAllSessions(includeArchive) },
                        getStorageSizeMb = { calculateStorageSizeMb() },
                        onExportMp3 = { eventItem -> exportEventAsMp3(eventItem) }
                    )
                }
            }
        }
    }

    private fun startSnoreService(
        threshold: Float,
        testMode: Boolean,
        delayMinutes: Int,
        silenceToleranceSecs: Int,
        wakeDetection: Boolean,
        maxHours: Int
    ) {
        val serviceIntent = Intent(this, SnoreRecordService::class.java).apply {
            putExtra("THRESHOLD", threshold)
            putExtra("TEST_MODE", testMode)
            putExtra("DELAY_MINUTES", delayMinutes)
            putExtra("SILENCE_TOLERANCE", silenceToleranceSecs)
            putExtra("WAKE_DETECTION", wakeDetection)
            putExtra("MAX_HOURS", maxHours)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopSnoreService() {
        stopService(Intent(this, SnoreRecordService::class.java))
    }

    private fun dismissWakeAlertInService() {
        val intent = Intent(this, SnoreRecordService::class.java).apply {
            action = SnoreRecordService.ACTION_KEEP_SLEEPING
        }
        startService(intent)
    }

    private fun recoverOrphanedActiveSessions(root: File) {
        if (SnoreRecordService.isRunning || SnoreRecordService.isSavingSession) return
        val activeDirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("Session_") && f.name.endsWith("_ACTIVE") }
            ?: return

        for (dir in activeDirs) {
            try {
                File(dir, "temp_listen.m4a").takeIf { it.exists() }?.delete()

                val eventFiles = dir.listFiles { f -> f.name.startsWith("Event_") && f.extension == "m4a" }
                    ?.sortedBy { it.name } ?: emptyList()

                val chartFile = File(dir, "chart.csv")
                val hasChartData = chartFile.exists() && chartFile.length() > 2

                if (eventFiles.isEmpty() && !hasChartData) {
                    dir.deleteRecursively()
                    continue
                }

                val combinedFile = File(dir, "combined.m4a")
                if (eventFiles.isNotEmpty() && !combinedFile.exists()) {
                    SnoreRecordService.mergeAudioFiles(eventFiles, combinedFile)
                }

                val parts = dir.name.split("_")
                val datePart = parts.getOrNull(1) ?: SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(dir.lastModified()))
                val startPart = parts.getOrNull(2) ?: "00-00"

                var endStr = SimpleDateFormat("HH-mm", Locale.getDefault()).format(Date(dir.lastModified()))
                var totalSecs = 60
                var snoreSecs = eventFiles.size * 5
                var score = 0

                val metaFile = File(dir, "meta.txt")
                if (metaFile.exists()) {
                    val metaParts = metaFile.readText().split("|")
                    if (metaParts.size >= 5) {
                        endStr = metaParts[1]
                        totalSecs = metaParts[2].toIntOrNull() ?: totalSecs
                        snoreSecs = metaParts[3].toIntOrNull() ?: snoreSecs
                        score = metaParts[4].toIntOrNull() ?: score
                    }
                } else if (chartFile.exists()) {
                    val points = chartFile.readText().split(",").mapNotNull { it.trim().toIntOrNull() }
                    totalSecs = (points.size / 5).coerceAtLeast(1)
                    score = ((snoreSecs.toFloat() / totalSecs.toFloat()) * 100).toInt().coerceIn(0, 100)
                }

                val recoveredName = "Session_${datePart}_${startPart}_${endStr}_T${totalSecs}_S${snoreSecs}_SC${score}"
                dir.renameTo(File(dir.parentFile, recoveredName))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getSessions(): List<SnoreSession> {
        val root = getExternalFilesDir(null) ?: return emptyList()
        recoverOrphanedActiveSessions(root)

        val dirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("Session_") && !f.name.endsWith("_ACTIVE") }
            ?: return emptyList()

        val sdf = SimpleDateFormat("dd.MM.yyyy_HH-mm", Locale.getDefault())

        return dirs.mapNotNull { dir ->
            try {
                val parts = dir.name.split("_")
                val datePart = parts[1]
                val startTimePart = parts[2]
                val endTimePart = parts[3]
                val totalSecs = parts.find { it.startsWith("T") }?.drop(1)?.toIntOrNull() ?: 0
                val snoreSecs = parts.find { it.startsWith("S") && !it.startsWith("SC") && !it.startsWith("Session") }?.drop(1)?.toIntOrNull() ?: 0
                val score = parts.find { it.startsWith("SC") }?.drop(2)?.toIntOrNull() ?: 0

                val parsedDate = sdf.parse("${datePart}_${startTimePart}") ?: Date(dir.lastModified())

                val chartFile = File(dir, "chart.csv")
                val chartPoints = if (chartFile.exists()) {
                    chartFile.readText().split(",").mapNotNull { it.trim().toIntOrNull() }
                } else emptyList()

                val combined = File(dir, "combined.m4a").takeIf { it.exists() }
                val events = dir.listFiles { f -> f.name.startsWith("Event_") && f.extension == "m4a" }
                    ?.sortedBy { it.name } ?: emptyList()

                val isArchived = File(dir, "archived.flag").exists()

                SnoreSession(
                    dir = dir,
                    startDate = parsedDate,
                    startTimeStr = startTimePart.replace("-", ":"),
                    endTimeStr = endTimePart.replace("-", ":"),
                    totalSecs = totalSecs,
                    snoreSecs = snoreSecs,
                    score = score,
                    chartPoints = if (chartPoints.isEmpty()) listOf(10, 15, 10) else chartPoints,
                    combinedAudio = combined,
                    eventFiles = events,
                    isArchived = isArchived
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.startDate.time }
    }

    private fun toggleArchiveSession(session: SnoreSession) {
        val flagFile = File(session.dir, "archived.flag")
        if (flagFile.exists()) {
            flagFile.delete()
            Toast.makeText(this, "Aus dem Archiv wiederhergestellt", Toast.LENGTH_SHORT).show()
        } else {
            flagFile.createNewFile()
            Toast.makeText(this, "Im Archiv gesichert 🔒", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSession(session: SnoreSession) {
        session.dir.deleteRecursively()
        Toast.makeText(this, "Aufnahme gelöscht", Toast.LENGTH_SHORT).show()
    }

    private fun deleteSingleEventFile(eventFile: File) {
        try {
            val barsFile = File(eventFile.parentFile, "${eventFile.nameWithoutExtension}.bars")
            if (barsFile.exists()) barsFile.delete()
            eventFile.delete()
            Toast.makeText(this, "Einzelspur gelöscht", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteAllSessions(includeArchive: Boolean) {
        val root = getExternalFilesDir(null) ?: return
        val dirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("Session_") && !f.name.endsWith("_ACTIVE") }
            ?: return

        var deletedCount = 0
        for (dir in dirs) {
            val isArchived = File(dir, "archived.flag").exists()
            if (includeArchive || !isArchived) {
                dir.deleteRecursively()
                deletedCount++
            }
        }
        val msg = if (includeArchive) {
            "Alle Aufnahmen inklusive Archiv ($deletedCount) wurden gelöscht."
        } else {
            "Normale Aufnahmen ($deletedCount) gelöscht. Das Archiv blieb geschützt."
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun calculateStorageSizeMb(): String {
        val root = getExternalFilesDir(null) ?: return "0,0 MB"
        val totalBytes = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val mb = totalBytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.GERMAN, "%.1f MB", mb)
    }

    private fun exportEventAsMp3(event: SnoreEventItem) {
        val fileName = "Schnarchen_${event.dateStr}_${event.timeStr.replace(":", "-")}_${event.db}dB.mp3"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Gute Nacht")
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outStream ->
                        event.file.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    Toast.makeText(this, "Exportiert nach: Musik/Gute Nacht/$fileName", Toast.LENGTH_LONG).show()
                }
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val targetDir = File(musicDir, "Gute Nacht").apply { mkdirs() }
                val targetFile = File(targetDir, fileName)
                FileOutputStream(targetFile).use { outStream ->
                    event.file.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                Toast.makeText(this, "Exportiert nach: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler beim Exportieren: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun prepareChartBarsForCanvas(points: List<Int>, maxBarsToDraw: Int): List<Int> {
    if (points.isEmpty() || points.size <= maxBarsToDraw || maxBarsToDraw <= 0) return points
    val step = points.size.toFloat() / maxBarsToDraw.toFloat()
    val result = ArrayList<Int>(maxBarsToDraw)
    for (i in 0 until maxBarsToDraw) {
        val start = (i * step).toInt()
        val end = ceil((i + 1) * step).toInt().coerceIn(start + 1, points.size)
        var maxDb = 0
        for (j in start until end) {
            if (points[j] > maxDb) maxDb = points[j]
        }
        result.add(maxDb)
    }
    return result
}

@Composable
fun MainScreen(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    isTestMode: Boolean,
    onTestModeChange: (Boolean) -> Unit,
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    sleepDelayMinutes: Int,
    onSleepDelayChange: (Int) -> Unit,
    silenceToleranceSecs: Int,
    onSilenceToleranceChange: (Int) -> Unit,
    wakeDetectionEnabled: Boolean,
    onWakeDetectionChange: (Boolean) -> Unit,
    maxDurationHours: Int,
    onMaxDurationHoursChange: (Int) -> Unit,
    antiSnorePulse: Boolean,
    onAntiSnorePulseChange: (Boolean) -> Unit,
    notifyShowLiveCount: Boolean,
    onNotifyShowLiveCountChange: (Boolean) -> Unit,
    notifyWakeWithSound: Boolean,
    onNotifyWakeWithSoundChange: (Boolean) -> Unit,
    notifyMorningSummary: Boolean,
    onNotifyMorningSummaryChange: (Boolean) -> Unit,
    swipeGesturesEnabled: Boolean,
    onSwipeGesturesEnabledChange: (Boolean) -> Unit,
    swapSwipeDirections: Boolean,
    onSwapSwipeDirectionsChange: (Boolean) -> Unit,
    longPressMenuEnabled: Boolean,
    onLongPressMenuEnabledChange: (Boolean) -> Unit,
    textColor: Color,
    subTextColor: Color,
    cardColor: Color,
    bgColor: Color,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onDismissWakeAlert: () -> Unit,
    loadSessions: () -> List<SnoreSession>,
    onToggleArchiveSession: (SnoreSession) -> Unit,
    onDeleteSession: (SnoreSession) -> Unit,
    onDeleteSingleEvent: (File) -> Unit,
    onDeleteAllSessions: (Boolean) -> Unit,
    getStorageSizeMb: () -> String,
    onExportMp3: (SnoreEventItem) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        }
    }

    val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    var hasPermissions by remember {
        mutableStateOf(permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        hasPermissions = map.values.all { it }
    }

    if (!hasPermissions) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Wir benötigen Mikrofon- und Benachrichtigungs-Rechte für eine zuverlässige Schnarch-Erkennung im Standby.",
                color = textColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) }) {
                Text("Berechtigungen erlauben")
            }
        }
        return
    }

    var currentTab by rememberSaveable { mutableStateOf("record") }
    var isRecording by remember { mutableStateOf(SnoreRecordService.isRunning) }
    var isSavingSession by remember { mutableStateOf(SnoreRecordService.isSavingSession) }
    var isWaitingDelay by remember { mutableStateOf(SnoreRecordService.isWaitingDelay) }
    var remainingDelay by remember { mutableIntStateOf(SnoreRecordService.remainingDelaySeconds) }
    var showAwakeDialog by remember { mutableStateOf(false) }

    var selectedSession by remember { mutableStateOf<SnoreSession?>(null) }
    var sessions by remember { mutableStateOf(loadSessions()) }

    LaunchedEffect(Unit) {
        var wasBusyBefore = SnoreRecordService.isRunning || SnoreRecordService.isSavingSession
        while (true) {
            val currentlyRunning = SnoreRecordService.isRunning
            val currentlySaving = SnoreRecordService.isSavingSession
            val currentlyBusy = currentlyRunning || currentlySaving

            isRecording = currentlyRunning
            isSavingSession = currentlySaving
            isWaitingDelay = SnoreRecordService.isWaitingDelay
            remainingDelay = SnoreRecordService.remainingDelaySeconds
            showAwakeDialog = SnoreRecordService.askIfAwake

            if (wasBusyBefore && !currentlyBusy) {
                sessions = withContext(Dispatchers.IO) { loadSessions() }
            }
            wasBusyBefore = currentlyBusy
            delay(250)
        }
    }

    if (showAwakeDialog) {
        AlertDialog(
            containerColor = cardColor,
            titleContentColor = textColor,
            textContentColor = subTextColor,
            onDismissRequest = {
                showAwakeDialog = false
                SnoreRecordService.askIfAwake = false
                onDismissWakeAlert()
            },
            title = { Text("Guten Morgen?") },
            text = { Text("Dein Handy wird gerade bewegt oder genutzt. Bist du wach und möchtest die Aufnahme beenden?") },
            confirmButton = {
                Button(
                    onClick = {
                        showAwakeDialog = false
                        SnoreRecordService.askIfAwake = false
                        isRecording = false
                        onStopRecord()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
                ) { Text("Ja, beenden", color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAwakeDialog = false
                    SnoreRecordService.askIfAwake = false
                    onDismissWakeAlert()
                }) {
                    Text("Weiterschlafen", color = subTextColor)
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = cardColor, tonalElevation = 4.dp) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Aufzeichnen") },
                    label = { Text("Aufzeichnen") },
                    selected = currentTab == "record",
                    onClick = { selectedSession = null; currentTab = "record" },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4FC3F7),
                        selectedTextColor = Color(0xFF4FC3F7),
                        unselectedIconColor = subTextColor,
                        unselectedTextColor = subTextColor,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Ereignisse") },
                    label = { Text("Ereignisse") },
                    selected = currentTab == "events",
                    onClick = {
                        sessions = loadSessions()
                        selectedSession = null
                        currentTab = "events"
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4FC3F7),
                        selectedTextColor = Color(0xFF4FC3F7),
                        unselectedIconColor = subTextColor,
                        unselectedTextColor = subTextColor,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Einstellung") },
                    label = { Text("Einstellung") },
                    selected = currentTab == "settings",
                    onClick = { selectedSession = null; currentTab = "settings" },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4FC3F7),
                        selectedTextColor = Color(0xFF4FC3F7),
                        unselectedIconColor = subTextColor,
                        unselectedTextColor = subTextColor,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(bgColor)) {
            when (currentTab) {
                "record" -> StartRecordScreen(
                    isRecording = isRecording,
                    isSavingSession = isSavingSession,
                    isWaitingDelay = isWaitingDelay,
                    remainingDelaySecs = remainingDelay,
                    isTestMode = isTestMode,
                    sleepDelayMinutes = sleepDelayMinutes,
                    threshold = threshold,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    cardColor = cardColor,
                    onStartClick = {
                        onStartRecord()
                        isRecording = true
                    },
                    onStopClick = {
                        onStopRecord()
                        isRecording = false
                    }
                )
                "events" -> {
                    if (selectedSession != null) {
                        val currentUpdated = sessions.find { it.dir.absolutePath == selectedSession!!.dir.absolutePath } ?: selectedSession!!
                        SessionDetailScreen(
                            session = currentUpdated,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            cardColor = cardColor,
                            bgColor = bgColor,
                            onBack = { selectedSession = null },
                            onToggleArchive = {
                                onToggleArchiveSession(currentUpdated)
                                sessions = loadSessions()
                                selectedSession = sessions.find { it.dir.absolutePath == currentUpdated.dir.absolutePath }
                            },
                            onDeleteConfirmed = {
                                onDeleteSession(currentUpdated)
                                sessions = loadSessions()
                                selectedSession = null
                            },
                            onDeleteSingleEvent = { file ->
                                onDeleteSingleEvent(file)
                                sessions = loadSessions()
                                selectedSession = sessions.find { it.dir.absolutePath == currentUpdated.dir.absolutePath }
                            },
                            onExportMp3 = onExportMp3
                        )
                    } else {
                        EventsMonthListScreen(
                            sessions = sessions,
                            swipeGesturesEnabled = swipeGesturesEnabled,
                            swapSwipeDirections = swapSwipeDirections,
                            longPressMenuEnabled = longPressMenuEnabled,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            cardColor = cardColor,
                            onSessionClick = { selectedSession = it },
                            onToggleArchive = { session ->
                                onToggleArchiveSession(session)
                                sessions = loadSessions()
                            },
                            onDeleteConfirmed = { session ->
                                onDeleteSession(session)
                                sessions = loadSessions()
                            }
                        )
                    }
                }
                "settings" -> SettingsScreen(
                    isRecording = isRecording,
                    isDarkMode = isDarkMode,
                    onDarkModeChange = onDarkModeChange,
                    isTestMode = isTestMode,
                    onTestModeChange = onTestModeChange,
                    threshold = threshold,
                    onThresholdChange = onThresholdChange,
                    sleepDelayMinutes = sleepDelayMinutes,
                    onSleepDelayChange = onSleepDelayChange,
                    silenceToleranceSecs = silenceToleranceSecs,
                    onSilenceToleranceChange = onSilenceToleranceChange,
                    wakeDetectionEnabled = wakeDetectionEnabled,
                    onWakeDetectionChange = onWakeDetectionChange,
                    maxDurationHours = maxDurationHours,
                    onMaxDurationHoursChange = onMaxDurationHoursChange,
                    antiSnorePulse = antiSnorePulse,
                    onAntiSnorePulseChange = onAntiSnorePulseChange,
                    notifyShowLiveCount = notifyShowLiveCount,
                    onNotifyShowLiveCountChange = onNotifyShowLiveCountChange,
                    notifyWakeWithSound = notifyWakeWithSound,
                    onNotifyWakeWithSoundChange = onNotifyWakeWithSoundChange,
                    notifyMorningSummary = notifyMorningSummary,
                    onNotifyMorningSummaryChange = onNotifyMorningSummaryChange,
                    swipeGesturesEnabled = swipeGesturesEnabled,
                    onSwipeGesturesEnabledChange = onSwipeGesturesEnabledChange,
                    swapSwipeDirections = swapSwipeDirections,
                    onSwapSwipeDirectionsChange = onSwapSwipeDirectionsChange,
                    longPressMenuEnabled = longPressMenuEnabled,
                    onLongPressMenuEnabledChange = onLongPressMenuEnabledChange,
                    storageUsedText = getStorageSizeMb(),
                    archivedCount = sessions.count { it.isArchived },
                    onDeleteAllSessions = { includeArchive ->
                        onDeleteAllSessions(includeArchive)
                        sessions = loadSessions()
                    },
                    textColor = textColor,
                    subTextColor = subTextColor,
                    cardColor = cardColor
                )
            }
        }
    }
}

// ==========================================
// STARTSEITE (Clean & Ohne Scrollen)
// ==========================================
@Composable
fun StartRecordScreen(
    isRecording: Boolean,
    isSavingSession: Boolean,
    isWaitingDelay: Boolean,
    remainingDelaySecs: Int,
    isTestMode: Boolean,
    sleepDelayMinutes: Int,
    threshold: Float,
    textColor: Color,
    subTextColor: Color,
    cardColor: Color,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged != 0
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(receiver, filter)
        if (initialIntent != null) {
            val status = initialIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = initialIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged != 0
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isSavingSession) {
                CircularProgressIndicator(color = Color(0xFF4FC3F7), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Aufnahme wird gespeichert...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text("Audiospuren werden im Hintergrund zusammengefügt", fontSize = 13.sp, color = subTextColor, modifier = Modifier.padding(top = 6.dp))
            } else if (!isRecording) {
                TiredSunIllustration(modifier = Modifier.size(120.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bereit zum Schlafen?", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(
                    text = if (isTestMode) {
                        "Testmodus aktiv (Sofort-Start • ab ${threshold.toInt()} dB)"
                    } else {
                        "Startet nach $sleepDelayMinutes Min. • Schwellenwert ${threshold.toInt()} dB"
                    },
                    fontSize = 13.sp,
                    color = if (isTestMode) Color(0xFFFFB74D) else subTextColor,
                    modifier = Modifier.padding(top = 6.dp),
                    textAlign = TextAlign.Center
                )
            } else if (isWaitingDelay) {
                YawningMoonIllustration(modifier = Modifier.size(120.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Einschlafphase...", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = textColor)
                val mins = remainingDelaySecs / 60
                val secs = remainingDelaySecs % 60
                Text(
                    text = String.format(Locale.getDefault(), "Noch %02d:%02d Min. bis zur Aufnahme", mins, secs),
                    fontSize = 15.sp,
                    color = Color(0xFFFFCA28),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                SleepingMoonIllustration(modifier = Modifier.size(120.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Gute Nacht", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(
                    text = "Überwachung aktiv – speichert ab ${threshold.toInt()} dB",
                    fontSize = 14.sp,
                    color = Color(0xFF66BB6A),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.NightlightRound,
                        contentDescription = null,
                        tint = if (isCharging) Color(0xFF66BB6A) else Color(0xFFFFB74D),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tipp: Auf den Nachttisch legen",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isCharging) {
                                "Ladekabel verbunden – optimale Standby-Zuverlässigkeit aktiv."
                            } else {
                                "Mit dem Mikrofon zu dir auf den Nachttisch legen und bestenfalls ans Ladekabel anschließen."
                            },
                            color = if (isCharging) Color(0xFF66BB6A) else subTextColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isRecording) onStopClick() else onStartClick()
                },
                enabled = !isSavingSession,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFE53935) else Color(0xFF4FC3F7)
                )
            ) {
                Text(
                    text = when {
                        isSavingSession -> "Speichert..."
                        isRecording -> "Aufwachen (Aufnahme stoppen)"
                        else -> "Aufnahme starten"
                    },
                    color = if (isRecording) Color.White else Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TiredSunIllustration(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.30f

            for (angle in 0 until 360 step 30) {
                val rad = Math.toRadians(angle.toDouble())
                val start = Offset(
                    x = center.x + (radius * 1.2f) * cos(rad).toFloat(),
                    y = center.y + (radius * 1.2f) * sin(rad).toFloat()
                )
                val end = Offset(
                    x = center.x + (radius * 1.5f) * cos(rad).toFloat(),
                    y = center.y + (radius * 1.5f) * sin(rad).toFloat()
                )
                drawLine(
                    color = Color(0xFFFFB74D),
                    start = start,
                    end = end,
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(color = Color(0xFFFFCA28), radius = radius, center = center)

            val eyeOffsetX = radius * 0.38f
            val eyeY = center.y - radius * 0.08f
            val eyeWidth = radius * 0.32f
            drawArc(
                color = Color(0xFF4E342E),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(center.x - eyeOffsetX - eyeWidth / 2, eyeY - eyeWidth / 4),
                size = Size(eyeWidth, eyeWidth * 0.5f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF4E342E),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(center.x + eyeOffsetX - eyeWidth / 2, eyeY - eyeWidth / 4),
                size = Size(eyeWidth, eyeWidth * 0.5f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )

            drawArc(
                color = Color(0xFF4E342E),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.2f, center.y + radius * 0.25f),
                size = Size(radius * 0.4f, radius * 0.25f),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "zZz",
            color = Color(0xFFFFB74D),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 4.dp)
        )
    }
}

@Composable
fun YawningMoonIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "yawnAnim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yawnScale"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.36f

            drawCircle(color = Color(0xFFFFCA28), radius = radius, center = center)

            val eyeOffsetX = radius * 0.35f
            val eyeY = center.y - radius * 0.12f
            drawLine(Color(0xFF4E342E), Offset(center.x - eyeOffsetX - 10f, eyeY), Offset(center.x - eyeOffsetX + 10f, eyeY), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(Color(0xFF4E342E), Offset(center.x + eyeOffsetX - 10f, eyeY), Offset(center.x + eyeOffsetX + 10f, eyeY), strokeWidth = 5f, cap = StrokeCap.Round)

            drawOval(
                color = Color(0xFF4E342E),
                topLeft = Offset(center.x - radius * 0.22f, center.y + radius * 0.05f),
                size = Size(radius * 0.44f, radius * 0.5f)
            )
        }
        Text(
            text = "Z z Z",
            color = Color(0xFF4FC3F7),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp)
        )
    }
}

@Composable
fun SleepingMoonIllustration(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.36f

            drawCircle(color = Color(0xFF4FC3F7), radius = radius, center = center)

            val eyeOffsetX = radius * 0.35f
            val eyeY = center.y - radius * 0.1f
            val eyeWidth = radius * 0.28f

            drawArc(
                color = Color(0xFF14171F),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - eyeOffsetX - eyeWidth / 2, eyeY),
                size = Size(eyeWidth, eyeWidth * 0.5f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF14171F),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x + eyeOffsetX - eyeWidth / 2, eyeY),
                size = Size(eyeWidth, eyeWidth * 0.5f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )

            drawArc(
                color = Color(0xFF14171F),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.15f, center.y + radius * 0.2f),
                size = Size(radius * 0.3f, radius * 0.2f),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "z Z z",
            color = Color(0xFFFFCA28),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp)
        )
    }
}

// ==========================================
// EREIGNISSE-LISTE
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventsMonthListScreen(
    sessions: List<SnoreSession>,
    swipeGesturesEnabled: Boolean,
    swapSwipeDirections: Boolean,
    longPressMenuEnabled: Boolean,
    textColor: Color,
    subTextColor: Color,
    cardColor: Color,
    onSessionClick: (SnoreSession) -> Unit,
    onToggleArchive: (SnoreSession) -> Unit,
    onDeleteConfirmed: (SnoreSession) -> Unit
) {
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.GERMAN) }
    val dayFormat = remember { SimpleDateFormat("EE. d", Locale.GERMAN) }

    var activeViewTab by rememberSaveable { mutableStateOf("normal") }
    var sessionForMenu by remember { mutableStateOf<SnoreSession?>(null) }
    var sessionToConfirmDelete by remember { mutableStateOf<SnoreSession?>(null) }

    val normalSessions = remember(sessions) { sessions.filter { !it.isArchived } }
    val archivedSessions = remember(sessions) { sessions.filter { it.isArchived } }
    val displayedSessions = if (activeViewTab == "archive") archivedSessions else normalSessions

    val grouped = remember(displayedSessions) {
        displayedSessions.groupBy { s ->
            monthFormat.format(s.startDate).replaceFirstChar { it.uppercase() }
        }
    }

    if (sessionForMenu != null) {
        val target = sessionForMenu!!
        AlertDialog(
            containerColor = cardColor,
            titleContentColor = textColor,
            textContentColor = subTextColor,
            onDismissRequest = { sessionForMenu = null },
            title = { Text("Aufnahme vom ${dayFormat.format(target.startDate)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (target.isArchived) "Status: Im Archiv gesichert 🔒" else "Wähle eine Aktion für diese Sitzung:",
                        color = subTextColor,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            sessionForMenu = null
                            onSessionClick(target)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4FC3F7))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aufnahme öffnen", color = textColor)
                    }

                    OutlinedButton(
                        onClick = {
                            sessionForMenu = null
                            onToggleArchive(target)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (target.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (target.isArchived) "Aus Archiv holen" else "Ins Archiv verschieben",
                            color = textColor
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            sessionForMenu = null
                            sessionToConfirmDelete = target
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFE53935))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aufnahme löschen", color = Color(0xFFE53935))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sessionForMenu = null }) {
                    Text("Schließen", color = subTextColor)
                }
            }
        )
    }

    if (sessionToConfirmDelete != null) {
        val target = sessionToConfirmDelete!!
        if (target.isArchived) {
            AlertDialog(
                containerColor = cardColor,
                titleContentColor = textColor,
                textContentColor = subTextColor,
                onDismissRequest = { sessionToConfirmDelete = null },
                title = { Text("Im Archiv geschützt 🔒") },
                text = {
                    Text("Diese Aufnahme liegt im Archiv und ist vor dem Löschen geschützt. Möchtest du sie zuerst aus dem Archiv holen?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            sessionToConfirmDelete = null
                            onToggleArchive(target)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D))
                    ) {
                        Text("Aus Archiv holen", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToConfirmDelete = null }) {
                        Text("Abbrechen", color = subTextColor)
                    }
                }
            )
        } else {
            AlertDialog(
                containerColor = cardColor,
                titleContentColor = textColor,
                textContentColor = subTextColor,
                onDismissRequest = { sessionToConfirmDelete = null },
                title = { Text("Aufnahme löschen?") },
                text = {
                    Text("Möchtest du die Sitzung vom ${dayFormat.format(target.startDate)} (${target.startTimeStr} Uhr) wirklich unwiderruflich löschen?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            sessionToConfirmDelete = null
                            onDeleteConfirmed(target)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("Ja, löschen", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sessionToConfirmDelete = null }) {
                        Text("Abbrechen", color = subTextColor)
                    }
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ereignisse", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
            if (swipeGesturesEnabled) {
                Text(
                    text = if (!swapSwipeDirections) "⇄ Rechts: Archiv • Links: Löschen" else "⇄ Links: Archiv • Rechts: Löschen",
                    color = subTextColor,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(cardColor)
                .border(1.dp, subTextColor.copy(alpha = 0.25f), RoundedCornerShape(21.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (activeViewTab == "normal") Color(0xFF4FC3F7).copy(alpha = 0.2f) else Color.Transparent)
                    .border(
                        width = if (activeViewTab == "normal") 1.5.dp else 0.dp,
                        color = if (activeViewTab == "normal") Color(0xFF4FC3F7) else Color.Transparent,
                        shape = RoundedCornerShape(21.dp)
                    )
                    .clickable { activeViewTab = "normal" },
                contentAlignment = Alignment.Center
            ) {
                Text("Aufnahmen (${normalSessions.size})", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (activeViewTab == "archive") Color(0xFFFFB74D).copy(alpha = 0.2f) else Color.Transparent)
                    .border(
                        width = if (activeViewTab == "archive") 1.5.dp else 0.dp,
                        color = if (activeViewTab == "archive") Color(0xFFFFB74D) else Color.Transparent,
                        shape = RoundedCornerShape(21.dp)
                    )
                    .clickable { activeViewTab = "archive" },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Archiv (${archivedSessions.size})", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (displayedSessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (activeViewTab == "archive") {
                        "Dein Archiv ist leer.\nWische eine Aufnahme zur Seite oder halte sie gedrückt, um sie hier zu sichern."
                    } else {
                        "Noch keine Aufnahmen vorhanden."
                    },
                    color = subTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (monthTitle, monthSessions) ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardColor.copy(alpha = 0.6f))
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(monthTitle, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val countText = if (monthSessions.size == 1) "1 Sitzung" else "${monthSessions.size} Sitzungen"
                            Text(countText, color = subTextColor, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))
                    }

                    items(monthSessions, key = { it.dir.absolutePath }) { session ->
                        SwipeableSessionRow(
                            session = session,
                            dayTitle = dayFormat.format(session.startDate),
                            swipeEnabled = swipeGesturesEnabled,
                            swapDirections = swapSwipeDirections,
                            longPressEnabled = longPressMenuEnabled,
                            textColor = textColor,
                            subTextColor = subTextColor,
                            onClick = { onSessionClick(session) },
                            onLongPress = { sessionForMenu = session },
                            onSwipeArchive = { onToggleArchive(session) },
                            onSwipeDelete = { sessionToConfirmDelete = session }
                        )
                        HorizontalDivider(color = subTextColor.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableSessionRow(
    session: SnoreSession,
    dayTitle: String,
    swipeEnabled: Boolean,
    swapDirections: Boolean,
    longPressEnabled: Boolean,
    textColor: Color,
    subTextColor: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeArchive: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { 105.dp.toPx() }

    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = rawOffsetX, label = "swipeOffset")

    val h = session.totalSecs / 3600
    val m = (session.totalSecs % 3600) / 60
    val durationText = if (h > 0 || m > 0) "${h}Std ${m}Min" else "${session.totalSecs}Sek"

    val isRightArchive = !swapDirections
    val isSwipingRight = animatedOffsetX > 15f
    val isSwipingLeft = animatedOffsetX < -15f

    val bgTint = when {
        isSwipingRight -> if (isRightArchive) Color(0xFFFFB74D).copy(alpha = 0.25f) else Color(0xFFE53935).copy(alpha = 0.25f)
        isSwipingLeft -> if (isRightArchive) Color(0xFFE53935).copy(alpha = 0.25f) else Color(0xFFFFB74D).copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgTint)
    ) {
        if (isSwipingRight || isSwipingLeft) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showArchiveIcon = (isSwipingRight && isRightArchive) || (isSwipingLeft && !isRightArchive)
                if (showArchiveIcon) {
                    Icon(
                        imageVector = if (session.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = "Archiv",
                        tint = Color(0xFFFFB74D)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (session.isArchived) "Aus Archiv" else "Archivieren",
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Löschen",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Löschen",
                        tint = Color(0xFFE53935)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .then(
                    if (swipeEnabled) {
                        Modifier.pointerInput(session, swapDirections) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (rawOffsetX > thresholdPx) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isRightArchive) onSwipeArchive() else onSwipeDelete()
                                    } else if (rawOffsetX < -thresholdPx) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isRightArchive) onSwipeDelete() else onSwipeArchive()
                                    }
                                    rawOffsetX = 0f
                                },
                                onDragCancel = { rawOffsetX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    rawOffsetX = (rawOffsetX + dragAmount).coerceIn(-thresholdPx * 1.4f, thresholdPx * 1.4f)
                                }
                            )
                        }
                    } else Modifier
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (longPressEnabled) {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    } else null
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(92.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dayTitle, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (session.isArchived) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Archiviert",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(durationText, color = subTextColor, fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .padding(horizontal = 12.dp)
            ) {
                MiniAudioSparkline(points = session.chartPoints)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    ScoreGaugeRing(score = session.score, textColor = textColor, strokeDp = 6f, fontSizeSp = 15)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = subTextColor)
            }
        }
    }
}

@Composable
fun MiniAudioSparkline(points: List<Int>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseHeight = size.height * 0.20f
        drawRect(
            color = Color(0xFF37474F).copy(alpha = 0.55f),
            topLeft = Offset(0f, size.height - baseHeight),
            size = Size(size.width, baseHeight)
        )

        if (points.isEmpty()) return@Canvas
        val maxBars = (size.width / 2f).toInt().coerceAtLeast(30)
        val displayPoints = prepareChartBarsForCanvas(points, maxBars)
        val maxVal = (displayPoints.maxOrNull() ?: 80).coerceAtLeast(50).toFloat()
        val barWidth = size.width / displayPoints.size.coerceAtLeast(1)

        displayPoints.forEachIndexed { idx, db ->
            val ratio = (db / maxVal).coerceIn(0.05f, 1f)
            val h = ratio * size.height
            val color = when {
                db < 45 -> Color(0xFF37474F)
                db < 58 -> Color(0xFF4DB6AC)
                db < 70 -> Color(0xFFFFB74D)
                else -> Color(0xFFE57373)
            }
            drawRect(
                color = color,
                topLeft = Offset(idx * barWidth, size.height - h),
                size = Size((barWidth * 0.8f).coerceAtLeast(0.8f), h)
            )
        }
    }
}

// ==========================================
// DETAILANSICHT (Mit 5er-Zeitachse, vertikalen Hilfslinien & Einzelspur-Diagrammen)
// ==========================================

/**
 * Berechnet 5 gleichmäßig verteilte Uhrzeiten (Start, 25%, 50%, 75%, Ende)
 * sowie optional die exakte Uhrzeit für die aktuelle Abspielposition.
 */
fun calculateSessionTimeLabels(startTimeStr: String, endTimeStr: String, totalSecs: Int): List<String> {
    return try {
        val startParts = startTimeStr.split(":")
        val endParts = endTimeStr.split(":")
        val startMins = (startParts[0].toInt() * 60) + startParts[1].toInt()
        var endMins = (endParts[0].toInt() * 60) + endParts[1].toInt()

        // Falls über Mitternacht geschlafen wurde (z.B. 23:30 bis 07:00)
        if (endMins < startMins) {
            endMins += 24 * 60
        }

        val diffMins = if (endMins > startMins) {
            (endMins - startMins).toFloat()
        } else {
            (totalSecs / 60f).coerceAtLeast(1f)
        }

        listOf(0f, 0.25f, 0.50f, 0.75f, 1f).map { fraction ->
            val totalM = (startMins + (diffMins * fraction).roundToInt()) % (24 * 60)
            val h = totalM / 60
            val m = totalM % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        }
    } catch (e: Exception) {
        listOf(startTimeStr, "", "", "", endTimeStr)
    }
}

fun calculateTimeAtFraction(startTimeStr: String, endTimeStr: String, totalSecs: Int, fraction: Float): String {
    return try {
        val startParts = startTimeStr.split(":")
        val endParts = endTimeStr.split(":")
        val startMins = (startParts[0].toInt() * 60) + startParts[1].toInt()
        var endMins = (endParts[0].toInt() * 60) + endParts[1].toInt()
        if (endMins < startMins) endMins += 24 * 60

        val diffMins = if (endMins > startMins) (endMins - startMins).toFloat() else (totalSecs / 60f)
        val totalM = (startMins + (diffMins * fraction.coerceIn(0f, 1f)).roundToInt()) % (24 * 60)
        val h = totalM / 60
        val m = totalM % 60
        String.format(Locale.getDefault(), "%02d:%02d Uhr", h, m)
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun SessionDetailScreen(
    session: SnoreSession,
    textColor: Color,
    subTextColor: Color,
    cardColor: Color,
    bgColor: Color,
    onBack: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteSingleEvent: (File) -> Unit,
    onExportMp3: (SnoreEventItem) -> Unit
) {
    BackHandler { onBack() }

    val headerDateFormat = remember { SimpleDateFormat("EEEE dd MMM.", Locale.GERMAN) }
    val titleDate = remember(session) {
        headerDateFormat.format(session.startDate).replaceFirstChar { it.uppercase() }
    }

    // 5 Uhrzeiten für die Zeitachse über dem Hauptdiagramm
    val timeAxisLabels = remember(session) {
        calculateSessionTimeLabels(session.startTimeStr, session.endTimeStr, session.totalSecs)
    }

    var activeSubTab by rememberSaveable { mutableStateOf("overview") }
    var trackSortOrder by rememberSaveable { mutableStateOf("time") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var singleEventToDelete by remember { mutableStateOf<SnoreEventItem?>(null) }

    val mediaPlayer = remember { MediaPlayer() }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(1) }

    var playingSingleEventFile by remember { mutableStateOf<File?>(null) }
    val audioToPlay = session.combinedAudio ?: session.eventFiles.firstOrNull()

    if (showDeleteConfirm) {
        if (session.isArchived) {
            AlertDialog(
                containerColor = cardColor,
                titleContentColor = textColor,
                textContentColor = subTextColor,
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Im Archiv geschützt 🔒") },
                text = { Text("Diese Aufnahme ist archiviert und daher vor dem Löschen geschützt. Bitte hebe zuerst die Archivierung oben rechts über das Schloss-Symbol auf.") },
                confirmButton = {
                    Button(
                        onClick = { showDeleteConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
                    ) { Text("Verstanden", color = Color.Black) }
                }
            )
        } else {
            AlertDialog(
                containerColor = cardColor,
                titleContentColor = textColor,
                textContentColor = subTextColor,
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Aufnahme löschen?") },
                text = { Text("Möchtest du diese gesamte Schlaf-Aufnahme wirklich unwiderruflich löschen?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteConfirmed()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) { Text("Ja, löschen", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Abbrechen", color = subTextColor)
                    }
                }
            )
        }
    }

    if (singleEventToDelete != null) {
        val target = singleEventToDelete!!
        AlertDialog(
            containerColor = cardColor,
            titleContentColor = textColor,
            textContentColor = subTextColor,
            onDismissRequest = { singleEventToDelete = null },
            title = { Text("Einzelspur löschen?") },
            text = { Text("Möchtest du die Spur von ${target.timeStr} Uhr (${target.db} dB) löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        if (playingSingleEventFile == target.file) {
                            try { mediaPlayer.stop() } catch (_: Exception) {}
                            isPlaying = false
                            playingSingleEventFile = null
                        }
                        singleEventToDelete = null
                        onDeleteSingleEvent(target.file)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("Spur löschen", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { singleEventToDelete = null }) {
                    Text("Abbrechen", color = subTextColor)
                }
            }
        )
    }

    DisposableEffect(session) {
        if (audioToPlay != null && audioToPlay.exists()) {
            try {
                mediaPlayer.setDataSource(audioToPlay.absolutePath)
                mediaPlayer.prepare()
                totalDurationMs = mediaPlayer.duration.coerceAtLeast(1)
                isPrepared = true
                mediaPlayer.setOnCompletionListener {
                    isPlaying = false
                    currentPosMs = 0
                    playingSingleEventFile = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosMs = mediaPlayer.currentPosition
            delay(50)
        }
    }

    fun playOrSeekCombined(targetFraction: Float? = null) {
        if (audioToPlay == null || !audioToPlay.exists()) return
        try {
            if (playingSingleEventFile != null) {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(audioToPlay.absolutePath)
                mediaPlayer.prepare()
                totalDurationMs = mediaPlayer.duration.coerceAtLeast(1)
                playingSingleEventFile = null
                isPrepared = true
            }
            if (!isPrepared) return

            if (targetFraction != null) {
                val targetMs = (targetFraction.coerceIn(0f, 1f) * totalDurationMs).toInt()
                mediaPlayer.seekTo(targetMs)
                currentPosMs = targetMs
            }
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleOrSeekSingleEvent(file: File, seekFraction: Float? = null) {
        try {
            if (playingSingleEventFile == file) {
                if (seekFraction != null) {
                    val targetMs = (seekFraction.coerceIn(0f, 1f) * totalDurationMs).toInt()
                    mediaPlayer.seekTo(targetMs)
                    currentPosMs = targetMs
                    if (!mediaPlayer.isPlaying) {
                        mediaPlayer.start()
                        isPlaying = true
                    }
                } else if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    mediaPlayer.start()
                    isPlaying = true
                }
            } else {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(file.absolutePath)
                mediaPlayer.prepare()
                totalDurationMs = mediaPlayer.duration.coerceAtLeast(1)
                playingSingleEventFile = file
                if (seekFraction != null) {
                    val targetMs = (seekFraction.coerceIn(0f, 1f) * totalDurationMs).toInt()
                    mediaPlayer.seekTo(targetMs)
                    currentPosMs = targetMs
                } else {
                    currentPosMs = 0
                }
                mediaPlayer.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun formatDurationClean(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun formatMsTime(ms: Int): String {
        val totalSecs = ms / 1000
        val m = totalSecs / 60
        val s = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    val eventItems by produceState(initialValue = emptyList<SnoreEventItem>(), session) {
        value = withContext(Dispatchers.IO) {
            val totalEvents = session.eventFiles.size.coerceAtLeast(1)
            session.eventFiles.mapIndexed { index, file ->
                val parts = file.nameWithoutExtension.split("_")
                val dateStr = parts.getOrNull(2) ?: ""
                val timeStr = (parts.getOrNull(3) ?: "").replace("-", ":")
                val dbVal = parts.getOrNull(4)?.removeSuffix("dB")?.toIntOrNull() ?: 50

                val secsFromName = parts.getOrNull(5)?.removeSuffix("s")?.toIntOrNull()
                val secs = secsFromName ?: ((file.length() / 16000L).toInt()).coerceAtLeast(1)
                val m = secs / 60
                val s = secs % 60
                val durStr = String.format(Locale.getDefault(), "%d:%02d min", m, s)

                val barsFile = File(file.parentFile, "${file.nameWithoutExtension}.bars")
                val barPoints = if (barsFile.exists()) {
                    barsFile.readText().split(",").mapNotNull { it.trim().toIntOrNull() }
                } else {
                    val barCount = (secs * 5).coerceIn(15, 120)
                    val sessionPts = session.chartPoints
                    if (sessionPts.size > barCount) {
                        val startIdx = ((index.toFloat() / totalEvents.toFloat()) * (sessionPts.size - barCount)).toInt().coerceAtLeast(0)
                        sessionPts.subList(startIdx, (startIdx + barCount).coerceAtMost(sessionPts.size))
                    } else {
                        List(barCount) { idx ->
                            val wave = sin(idx * 0.45).toFloat()
                            (dbVal - 10 + (wave * 10).toInt()).coerceIn(30, 85)
                        }
                    }
                }

                SnoreEventItem(
                    file = file,
                    dateStr = dateStr,
                    timeStr = timeStr,
                    db = dbVal,
                    durationSecs = secs,
                    durationStr = durStr,
                    chartPoints = if (barPoints.isEmpty()) listOf(dbVal) else barPoints
                )
            }
        }
    }

    val sortedEventItems = remember(eventItems, trackSortOrder) {
        when (trackSortOrder) {
            "loudest" -> eventItems.sortedByDescending { it.db }
            "longest" -> eventItems.sortedByDescending { it.durationSecs }
            else -> eventItems
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = Color(0xFF4FC3F7))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = titleDate,
                    color = textColor,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                val activeCursorInfo = if (playingSingleEventFile == null && (isPlaying || currentPosMs > 0)) {
                    val frac = (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    "Position: ${calculateTimeAtFraction(session.startTimeStr, session.endTimeStr, session.totalSecs, frac)}"
                } else {
                    "Auflösung: 0,2 Sek./Balken"
                }
                Text(
                    text = activeCursorInfo,
                    color = if (playingSingleEventFile == null && (isPlaying || currentPosMs > 0)) Color(0xFF4FC3F7) else subTextColor,
                    fontSize = 11.sp
                )
            }
            Row {
                IconButton(onClick = onToggleArchive) {
                    Icon(
                        imageVector = if (session.isArchived) Icons.Default.Lock else Icons.Default.Archive,
                        contentDescription = "Archivieren",
                        tint = if (session.isArchived) Color(0xFFFFB74D) else subTextColor
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Löschen", tint = subTextColor)
                }
            }
        }

        // 5er-Uhrzeitachse direkt über dem Hauptdiagramm (0%, 25%, 50%, 75%, 100%)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            timeAxisLabels.forEachIndexed { index, label ->
                val isEdge = index == 0 || index == timeAxisLabels.lastIndex
                Text(
                    text = label,
                    color = if (isEdge) textColor else subTextColor,
                    fontSize = if (isEdge) 14.sp else 12.sp,
                    fontWeight = if (isEdge) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .pointerInput(session) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        playOrSeekCombined(frac)
                    }
                }
                .pointerInput(session) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        playOrSeekCombined(frac)
                    }
                }
        ) {
            val rawPoints = session.chartPoints

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Horizontale Hilfslinien
                for (i in 1..3) {
                    val y = size.height * (i / 4f)
                    drawLine(
                        color = subTextColor.copy(alpha = 0.18f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5f
                    )
                }

                // Vertikale Hilfslinien passend zu den Zwischen-Uhrzeiten (25%, 50%, 75%)
                for (i in 1..3) {
                    val x = size.width * (i / 4f)
                    drawLine(
                        color = subTextColor.copy(alpha = 0.22f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5f
                    )
                }

                val baseH = size.height * 0.12f
                drawRect(
                    color = Color(0xFF37474F).copy(alpha = 0.6f),
                    topLeft = Offset(0f, size.height - baseH),
                    size = Size(size.width, baseH)
                )

                val maxPossibleBars = (size.width / 1.5f).toInt().coerceAtLeast(100)
                val points = prepareChartBarsForCanvas(rawPoints, maxPossibleBars)
                val maxVal = (points.maxOrNull() ?: 80).coerceAtLeast(60).toFloat()

                val barW = size.width / points.size.coerceAtLeast(1)
                val drawnBarW = (barW * 0.82f).coerceAtLeast(0.6f)

                points.forEachIndexed { idx, db ->
                    val ratio = (db / maxVal).coerceIn(0.06f, 1f)
                    val barH = ratio * size.height
                    val barColor = when {
                        db < 45 -> Color(0xFF37474F)
                        db < 58 -> Color(0xFF4DB6AC)
                        db < 70 -> Color(0xFFFFB74D)
                        else -> Color(0xFFE57373)
                    }
                    drawRect(
                        color = barColor,
                        topLeft = Offset(idx * barW, size.height - barH),
                        size = Size(drawnBarW, barH)
                    )
                }

                if (playingSingleEventFile == null && (isPlaying || currentPosMs > 0)) {
                    val progress = (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    val x = progress * size.width
                    drawLine(
                        color = Color(0xFF4FC3F7),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 4f
                    )
                    drawCircle(
                        color = Color(0xFF4FC3F7),
                        radius = 10f,
                        center = Offset(x, 10f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendBadge(color = Color(0xFF37474F), label = "Leise", textColor = textColor)
            LegendBadge(color = Color(0xFF4DB6AC), label = "Leicht", textColor = textColor)
            LegendBadge(color = Color(0xFFFFB74D), label = "Laut", textColor = textColor)
            LegendBadge(color = Color(0xFFE57373), label = "Stark", textColor = textColor)
        }

        if (audioToPlay != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isPlaying && playingSingleEventFile == null) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            playOrSeekCombined()
                        }
                    }) {
                        Icon(
                            imageVector = if (isPlaying && playingSingleEventFile == null) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Abspielen",
                            tint = Color(0xFF4FC3F7)
                        )
                    }

                    Text(formatMsTime(if (playingSingleEventFile == null) currentPosMs else 0), color = subTextColor, fontSize = 12.sp)

                    Slider(
                        value = if (playingSingleEventFile == null) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac -> playOrSeekCombined(frac) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF4FC3F7), activeTrackColor = Color(0xFF4FC3F7))
                    )

                    Text(formatMsTime(if (playingSingleEventFile == null) totalDurationMs else 0), color = subTextColor, fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(cardColor)
                .border(1.dp, subTextColor.copy(alpha = 0.25f), RoundedCornerShape(21.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (activeSubTab == "overview") Color(0xFF4FC3F7).copy(alpha = 0.2f) else Color.Transparent)
                    .border(
                        width = if (activeSubTab == "overview") 1.5.dp else 0.dp,
                        color = if (activeSubTab == "overview") Color(0xFF4FC3F7) else Color.Transparent,
                        shape = RoundedCornerShape(21.dp)
                    )
                    .clickable { activeSubTab = "overview" },
                contentAlignment = Alignment.Center
            ) {
                Text("Überblick", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(if (activeSubTab == "tracks") Color(0xFF4FC3F7).copy(alpha = 0.2f) else Color.Transparent)
                    .border(
                        width = if (activeSubTab == "tracks") 1.5.dp else 0.dp,
                        color = if (activeSubTab == "tracks") Color(0xFF4FC3F7) else Color.Transparent,
                        shape = RoundedCornerShape(21.dp)
                    )
                    .clickable { activeSubTab = "tracks" },
                contentAlignment = Alignment.Center
            ) {
                Text("Einzelne Spuren (${session.eventFiles.size})", color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeSubTab == "overview") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.2f)) {
                    DetailStatItem(
                        icon = Icons.Default.Bed,
                        label = "Zeit im Bett",
                        value = formatDurationClean(session.totalSecs),
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardColor = cardColor
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    DetailStatItem(
                        icon = Icons.Default.GraphicEq,
                        label = "Schnarchdauer",
                        value = "${formatDurationClean(session.snoreSecs)} | ${session.score}%",
                        textColor = textColor,
                        subTextColor = subTextColor,
                        cardColor = cardColor
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(145.dp), contentAlignment = Alignment.Center) {
                        ScoreGaugeRing(score = session.score, textColor = textColor, strokeDp = 14f, fontSizeSp = 34)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Schnarch-Score", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            if (sortedEventItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine einzelnen Schnarch-Spuren vorhanden.", color = subTextColor, textAlign = TextAlign.Center)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sortieren:", color = subTextColor, fontSize = 12.sp)
                    FilterChip(
                        selected = trackSortOrder == "time",
                        onClick = { trackSortOrder = "time" },
                        label = { Text("Uhrzeit", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = trackSortOrder == "loudest",
                        onClick = { trackSortOrder = "loudest" },
                        label = { Text("Lauteste", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = trackSortOrder == "longest",
                        onClick = { trackSortOrder = "longest" },
                        label = { Text("Längste", fontSize = 12.sp) }
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(sortedEventItems, key = { it.file.name }) { item ->
                        val isThisPlaying = playingSingleEventFile == item.file && isPlaying
                        val singleProgress = if (playingSingleEventFile == item.file) {
                            (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.dateStr} • ${item.timeStr} Uhr",
                                            color = textColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Länge: ${item.durationStr}  •  Max: ${item.db} dB",
                                            color = Color(0xFFFFB74D),
                                            fontSize = 13.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { toggleOrSeekSingleEvent(item.file) }) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Abspielen",
                                                tint = if (isThisPlaying) Color(0xFFE53935) else Color(0xFF4FC3F7)
                                            )
                                        }
                                        IconButton(onClick = { onExportMp3(item) }) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Als MP3 exportieren",
                                                tint = Color(0xFF66BB6A)
                                            )
                                        }
                                        IconButton(onClick = { singleEventToDelete = item }) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Spur löschen",
                                                tint = subTextColor
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                SingleTrackBarChart(
                                    points = item.chartPoints,
                                    isActive = playingSingleEventFile == item.file,
                                    progress = singleProgress,
                                    onSeek = { frac -> toggleOrSeekSingleEvent(item.file, frac) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Punkt 2: Balkendiagramm für eine einzelne Schnarch-Spur (0.2s Auflösung)
 */
@Composable
fun SingleTrackBarChart(
    points: List<Int>,
    isActive: Boolean,
    progress: Float,
    onSeek: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14171F).copy(alpha = 0.45f))
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.isEmpty()) return@Canvas
            val maxBars = (size.width / 2.2f).toInt().coerceAtLeast(25)
            val displayPoints = prepareChartBarsForCanvas(points, maxBars)
            val maxVal = (displayPoints.maxOrNull() ?: 80).coerceAtLeast(60).toFloat()
            val barW = size.width / displayPoints.size.coerceAtLeast(1)
            val drawnBarW = (barW * 0.78f).coerceAtLeast(1.2f)

            displayPoints.forEachIndexed { idx, db ->
                val ratio = (db / maxVal).coerceIn(0.10f, 1f)
                val barH = ratio * size.height
                val barColor = when {
                    db < 45 -> Color(0xFF37474F)
                    db < 58 -> Color(0xFF4DB6AC)
                    db < 70 -> Color(0xFFFFB74D)
                    else -> Color(0xFFE57373)
                }
                drawRect(
                    color = barColor,
                    topLeft = Offset(idx * barW, size.height - barH),
                    size = Size(drawnBarW, barH)
                )
            }

            if (isActive) {
                val x = progress.coerceIn(0f, 1f) * size.width
                drawLine(
                    color = Color(0xFF4FC3F7),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 3f
                )
                drawCircle(
                    color = Color(0xFF4FC3F7),
                    radius = 6f,
                    center = Offset(x, 6f)
                )
            }
        }
    }
}

@Composable
fun LegendBadge(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DetailStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    textColor: Color,
    subTextColor: Color,
    cardColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(cardColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(label, color = subTextColor, fontSize = 14.sp)
            Text(value, color = textColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ScoreGaugeRing(score: Int, textColor: Color, strokeDp: Float, fontSizeSp: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
            val strokePx = strokeDp.dp.toPx()
            drawArc(
                color = Color(0xFF37474F),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
            )
            val sweep = 270f * (score.coerceIn(0, 100) / 100f)
            val color = when {
                score < 20 -> Color(0xFF4DB6AC)
                score < 50 -> Color(0xFFFFB74D)
                else -> Color(0xFFE57373)
            }
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
            )
        }
        Text(
            text = score.toString(),
            color = textColor,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// EINSTELLUNGEN (Punkt 4: Ohne Beta-Ballast, mit Anti-Schnarch-Impuls & Raum-Kalibrierung)
// ==========================================
@Composable
fun SettingsScreen(
    isRecording: Boolean,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    isTestMode: Boolean,
    onTestModeChange: (Boolean) -> Unit,
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    sleepDelayMinutes: Int,
    onSleepDelayChange: (Int) -> Unit,
    silenceToleranceSecs: Int,
    onSilenceToleranceChange: (Int) -> Unit,
    wakeDetectionEnabled: Boolean,
    onWakeDetectionChange: (Boolean) -> Unit,
    maxDurationHours: Int,
    onMaxDurationHoursChange: (Int) -> Unit,
    antiSnorePulse: Boolean,
    onAntiSnorePulseChange: (Boolean) -> Unit,
    notifyShowLiveCount: Boolean,
    onNotifyShowLiveCountChange: (Boolean) -> Unit,
    notifyWakeWithSound: Boolean,
    onNotifyWakeWithSoundChange: (Boolean) -> Unit,
    notifyMorningSummary: Boolean,
    onNotifyMorningSummaryChange: (Boolean) -> Unit,
    swipeGesturesEnabled: Boolean,
    onSwipeGesturesEnabledChange: (Boolean) -> Unit,
    swapSwipeDirections: Boolean,
    onSwapSwipeDirectionsChange: (Boolean) -> Unit,
    longPressMenuEnabled: Boolean,
    onLongPressMenuEnabledChange: (Boolean) -> Unit,
    storageUsedText: String,
    archivedCount: Int,
    onDeleteAllSessions: (Boolean) -> Unit,
    textColor: Color,
    subTextColor: Color,
    cardColor: Color
) {
    val context = LocalContext.current

    var isTestingMic by remember { mutableStateOf(false) }
    var isCalibratingRoom by remember { mutableStateOf(false) }
    var liveDb by remember { mutableIntStateOf(0) }

    LaunchedEffect(isTestingMic, isCalibratingRoom) {
        if (isTestingMic || isCalibratingRoom) {
            val tempFile = File(context.cacheDir, "mic_test.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(tempFile.absolutePath)
                    prepare()
                    start()
                }
                if (isCalibratingRoom) {
                    val samples = mutableListOf<Int>()
                    repeat(25) { // 25 * 200ms = 5 Sekunden Raum-Kalibrierung
                        val amp = recorder.maxAmplitude
                        val db = if (amp > 0) (20 * log10(amp.toDouble())).toInt().coerceAtLeast(0) else 0
                        liveDb = db
                        if (db > 0) samples.add(db)
                        delay(200)
                    }
                    val avgRoomDb = if (samples.isNotEmpty()) samples.average().roundToInt() else 42
                    val recommended = (avgRoomDb + 12).coerceIn(48, 72).toFloat()
                    onThresholdChange(recommended)
                    Toast.makeText(
                        context,
                        "Raum-Grundpegel: $avgRoomDb dB → Schwellenwert optimal auf ${recommended.toInt()} dB gesetzt!",
                        Toast.LENGTH_LONG
                    ).show()
                    isCalibratingRoom = false
                } else {
                    while (isTestingMic) {
                        val amp = recorder.maxAmplitude
                        liveDb = if (amp > 0) (20 * log10(amp.toDouble())).toInt().coerceAtLeast(0) else 0
                        delay(150)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isCalibratingRoom = false
                isTestingMic = false
            } finally {
                try { recorder.stop(); recorder.release() } catch (_: Exception) {}
                tempFile.delete()
            }
        } else {
            liveDb = 0
        }
    }

    var deleteDialogStep by remember { mutableIntStateOf(0) }

    if (deleteDialogStep == 1) {
        AlertDialog(
            containerColor = cardColor,
            titleContentColor = textColor,
            textContentColor = subTextColor,
            onDismissRequest = { deleteDialogStep = 0 },
            title = { Text("Aufnahmen löschen (Frage 1/2)") },
            text = {
                Text(
                    if (archivedCount > 0) {
                        "Möchtest du nur die normalen Aufnahmen löschen (deine $archivedCount archivierten Aufnahmen bleiben geschützt) oder soll das Archiv AUCH geleert werden?"
                    } else {
                        "Möchtest du alle gespeicherten Aufnahmen löschen? Soll das Archiv dabei ebenfalls geleert werden?"
                    }
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            deleteDialogStep = 0
                            onDeleteAllSessions(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
                    ) {
                        Text("Nur normale löschen (Archiv behalten)", color = Color.Black)
                    }

                    OutlinedButton(
                        onClick = { deleteDialogStep = 2 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Archiv AUCH leeren...", color = Color(0xFFE53935))
                    }

                    TextButton(
                        onClick = { deleteDialogStep = 0 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abbrechen", color = subTextColor)
                    }
                }
            }
        )
    } else if (deleteDialogStep == 2) {
        AlertDialog(
            containerColor = cardColor,
            titleContentColor = textColor,
            textContentColor = subTextColor,
            onDismissRequest = { deleteDialogStep = 0 },
            title = { Text("Archiv wirklich leeren? (Frage 2/2)") },
            text = {
                Text("2. Sicherheitsabfrage: Bist du absolut sicher, dass auch alle geschützten Aufnahmen im Archiv unwiderruflich gelöscht werden sollen?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteDialogStep = 0
                        onDeleteAllSessions(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Ja, alles inkl. Archiv löschen", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogStep = 0 }) {
                    Text("Abbrechen (Archiv schützen)", color = subTextColor)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Einstellungen",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // KATEGORIE 1: Aufnahme, Schwellenwert, Nachlaufzeit & Anti-Schnarch-Impuls
        Text("Aufnahme & Mikrofon", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Testmodus (Sofort-Start)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Überspringt die Einschlafzeit für schnelle Tests.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = isTestMode, onCheckedChange = onTestModeChange)
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                // Aufnahme-Schwellenwert (Standard 55 dB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aufnahme-Schwellenwert", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${threshold.toInt()} dB", color = Color(0xFF4FC3F7), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Empfohlen: 54–58 dB. Filtert leises Atmen und Deckenrascheln heraus (Standard: 55 dB).",
                    color = subTextColor,
                    fontSize = 12.sp
                )
                Slider(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    valueRange = 35f..85f,
                    enabled = !isRecording && !isCalibratingRoom,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4FC3F7),
                        activeTrackColor = Color(0xFF4FC3F7),
                        inactiveTrackColor = subTextColor.copy(alpha = 0.25f)
                    )
                )

                if (isTestingMic || isCalibratingRoom) {
                    val isAbove = liveDb >= threshold.toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = if (isCalibratingRoom) "Messe Raum-Stille (5 Sek.)... " else "Aktuelle Umgebung: ",
                            color = textColor,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "$liveDb dB",
                            color = if (isAbove) Color(0xFFE53935) else Color(0xFF66BB6A),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { isTestingMic = !isTestingMic },
                        enabled = !isRecording && !isCalibratingRoom,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isTestingMic) Icons.Default.MicOff else Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isTestingMic) Color(0xFFE53935) else Color(0xFF4FC3F7)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTestingMic) "Stopp" else "Pegel testen",
                            color = if (isTestingMic) Color(0xFFE53935) else Color(0xFF4FC3F7),
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            isTestingMic = false
                            isCalibratingRoom = true
                        },
                        enabled = !isRecording && !isCalibratingRoom,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFFFFB74D))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Kalibrieren", color = Color(0xFFFFB74D), fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                // Nachlaufzeit (Standard 8 Sek. gegen abgehackte 5s-Aufnahmen!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nachlaufzeit pro Spur", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("$silenceToleranceSecs Sek.", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    "Überbrückt die Atempause zwischen zwei Schnarchern, damit zusammenhängende Schnarchphasen nicht abgehackt werden (Standard: 8 Sek.).",
                    color = subTextColor,
                    fontSize = 12.sp
                )
                Slider(
                    value = silenceToleranceSecs.toFloat(),
                    onValueChange = { onSilenceToleranceChange(it.roundToInt()) },
                    valueRange = 3f..20f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF4FC3F7), activeTrackColor = Color(0xFF4FC3F7))
                )

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                // Einschlafzeit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Einschlafzeit", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("$sleepDelayMinutes Min.", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    "Wartezeit nach dem Starten, bevor Schnarchen aufgezeichnet wird (Standard: 20 Min.).",
                    color = subTextColor,
                    fontSize = 12.sp
                )
                Slider(
                    value = sleepDelayMinutes.toFloat(),
                    onValueChange = { onSleepDelayChange(it.roundToInt()) },
                    valueRange = 1f..60f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF4FC3F7), activeTrackColor = Color(0xFF4FC3F7))
                )

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                // Punkt 4: Sanfter Anti-Schnarch-Impuls fest in der Hauptapp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sanfter Anti-Schnarch-Impuls", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Kurze, sanfte Vibration bei anhaltend starkem Schnarchen (> 70 dB), um im Schlaf die Liegeposition zu wechseln.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = antiSnorePulse, onCheckedChange = onAntiSnorePulseChange)
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                // Max Dauer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Automatischer Stopp nach", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("$maxDurationHours Std.", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    "Beendet die Aufnahme automatisch, falls sie am Morgen vergessen wurde.",
                    color = subTextColor,
                    fontSize = 12.sp
                )
                Slider(
                    value = maxDurationHours.toFloat(),
                    onValueChange = { onMaxDurationHoursChange(it.roundToInt()) },
                    valueRange = 4f..14f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF4FC3F7), activeTrackColor = Color(0xFF4FC3F7))
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // KATEGORIE 2: Benachrichtigungen
        Text("Benachrichtigungen", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aufwach-Erkennung („Guten Morgen?“)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Fragt nach, wenn das Handy morgens länger bewegt oder bedient wird.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = wakeDetectionEnabled, onCheckedChange = onWakeDetectionChange)
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aufwach-Meldung mit Ton & Vibration", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Macht sich im Hintergrund spürbar bemerkbar, wenn die Aufnahme vergessen wurde.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(
                        checked = notifyWakeWithSound,
                        onCheckedChange = onNotifyWakeWithSoundChange,
                        enabled = wakeDetectionEnabled
                    )
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live-Schnarchzähler in Statusleiste", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Zeigt in der laufenden Benachrichtigung live die Anzahl der erkannten Spuren.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = notifyShowLiveCount, onCheckedChange = onNotifyShowLiveCountChange)
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Morgen-Bilanz nach Aufnahmeende", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Sendet nach dem Stoppen eine Zusammenfassung mit Score und Schnarchdauer.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = notifyMorningSummary, onCheckedChange = onNotifyMorningSummaryChange)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // KATEGORIE 3: Gesten & Bedienung
        Text("Gesten & Bedienung", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wisch-Gesten in Ereignisliste", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Erlaubt das schnelle Archivieren und Löschen durch Wischen nach links/rechts.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = swipeGesturesEnabled, onCheckedChange = onSwipeGesturesEnabledChange)
                }

                if (swipeGesturesEnabled) {
                    HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wisch-Richtungen tauschen", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (!swapSwipeDirections) "Aktuell: Rechts = Archivieren • Links = Löschen" else "Aktuell: Links = Archivieren • Rechts = Löschen",
                                color = Color(0xFF4FC3F7),
                                fontSize = 12.sp
                            )
                        }
                        Switch(checked = swapSwipeDirections, onCheckedChange = onSwapSwipeDirectionsChange)
                    }
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gedrückt halten für Aktionsmenü", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Öffnet beim langen Drücken auf eine Aufnahme ein Auswahlmenü.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = longPressMenuEnabled, onCheckedChange = onLongPressMenuEnabledChange)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // KATEGORIE 4: Darstellung & Speicher
        Text("Darstellung & Speicher", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark Mode (Dunkles Design)", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Wechselt zwischen Tag- und augenschonendem Nachtmodus.", color = subTextColor, fontSize = 12.sp)
                    }
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
                }

                HorizontalDivider(color = subTextColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Belegter Speicherplatz", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Davon im geschützten Archiv: $archivedCount Sitzungen", color = subTextColor, fontSize = 12.sp)
                    }
                    Text(storageUsedText, color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { deleteDialogStep = 1 },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFE53935))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alle Aufnahmen löschen", color = Color(0xFFE53935))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Offizielle Versionsanzeige aus dem Android-Paketmanager
        val versionInfo = remember {
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val vName = pInfo.versionName ?: "0.1.0-alpha"
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode.toLong()
                "v$vName (Build $vCode)"
            } catch (e: Exception) {
                "v0.1.0-alpha"
            }
        }

        Text(
            text = "Gute Nacht • $versionInfo",
            color = subTextColor.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
    }
}