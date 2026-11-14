package com.brownischapps.gutenacht

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class SnoreRecordService : Service(), SensorEventListener {

    companion object {
        @Volatile var isRunning = false
        @Volatile var isSavingSession = false
        @Volatile var askIfAwake = false
        @Volatile var isWaitingDelay = false
        @Volatile var remainingDelaySeconds = 0

        const val ACTION_STOP_RECORDING = "com.brownischapps.gutenacht.ACTION_STOP_RECORDING"
        const val ACTION_KEEP_SLEEPING = "com.brownischapps.gutenacht.ACTION_KEEP_SLEEPING"

        // 200ms = 0.2 Sekunden pro Balken
        private const val TICK_INTERVAL_MS = 200L
        private const val TICKS_PER_SECOND = 5

        fun vibrateAntiSnorePattern(context: Context, strengthLevel: Int = 2) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                val timings = when (strengthLevel) {
                    1 -> longArrayOf(0, 140, 120, 140)
                    3 -> longArrayOf(0, 320, 140, 380, 140, 380)
                    else -> longArrayOf(0, 220, 130, 250)
                }
                val amplitudes = when (strengthLevel) {
                    1 -> intArrayOf(0, 95, 0, 115)
                    3 -> intArrayOf(0, 255, 0, 255, 0, 255)
                    else -> intArrayOf(0, 175, 0, 200)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (vibrator.hasAmplitudeControl()) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (strengthLevel == 3) 600L else 300L)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val statusChannelId = "SnoreStatusChannel_v4"
    private val wakeAlertLoudChannelId = "SnoreWakeAlertLoud_v2"
    private val wakeAlertSilentChannelId = "SnoreWakeAlertSilent_v2"
    private val summaryChannelId = "SnoreSummaryChannel_v1"

    private val statusNotificationId = 1
    private val wakeAlertNotificationId = 2
    private val summaryNotificationId = 3

    private var recorder: SnoreRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private var loopRunnable: Runnable? = null

    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var sessionDir: File? = null
    private var currentSegmentFile: File? = null
    private var isFinalized = false

    private var thresholdDb = 55f
    private var isTestMode = false
    private var delayMinutes = 20
    private var silenceToleranceSecs = 8
    private var wakeDetectionEnabled = true
    private var maxDurationHours = 10
    private var antiSnorePulseEnabled = false
    private var antiSnorePulseStrength = 2

    private var notifyShowLiveCount = true
    private var notifyWakeWithSound = true
    private var notifyMorningSummary = true

    private var startWallClockMs = 0L
    private var serviceStartElapsedMs = 0L
    private var recordingStartElapsedMs = 0L
    private var totalDelayMs = 0L

    private var snoreTicks = 0
    private var segmentTicks = 0
    private var silenceTicks = 0
    private var loudTicksInCurrentSegment = 0
    private var saveSnapshotTicks = 0
    private var loudSnoreStreakTicks = 0
    private var lastAntiSnorePulseMs = 0L
    private var isEventTriggered = false
    private var currentEventMaxDb = 0
    private var currentEventStartTime = ""
    private var eventCounter = 0
    private var lastNotificationText = ""

    private val chartHistory = mutableListOf<Int>()
    private val currentSegmentBars = mutableListOf<Int>()

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var baseX = 0f
    private var baseY = 0f
    private var baseZ = 0f
    private var hasBaseline = false
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var activeHandlingScore = 0f
    private var screenInteractiveTicks = 0
    private var lastWakeAlertElapsedMs = 0L

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannels()
        recorder = SnoreRecorder(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_RECORDING -> {
                cancelWakeAlertNotification()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_KEEP_SLEEPING -> {
                askIfAwake = false
                activeHandlingScore = 0f
                screenInteractiveTicks = 0
                lastWakeAlertElapsedMs = SystemClock.elapsedRealtime()
                cancelWakeAlertNotification()
                return START_STICKY
            }
        }

        if (isRunning) return START_STICKY

        val prefs = getSharedPreferences("gutenacht_prefs", Context.MODE_PRIVATE)
        thresholdDb = intent?.getFloatExtra("THRESHOLD", prefs.getFloat("threshold_v2", 55f)) ?: prefs.getFloat("threshold_v2", 55f)
        isTestMode = intent?.getBooleanExtra("TEST_MODE", prefs.getBoolean("test_mode", false)) ?: prefs.getBoolean("test_mode", false)
        delayMinutes = intent?.getIntExtra("DELAY_MINUTES", prefs.getInt("sleep_delay_minutes", 20)) ?: prefs.getInt("sleep_delay_minutes", 20)
        silenceToleranceSecs = intent?.getIntExtra("SILENCE_TOLERANCE", prefs.getInt("silence_tolerance_v2", 8)) ?: prefs.getInt("silence_tolerance_v2", 8)
        wakeDetectionEnabled = intent?.getBooleanExtra("WAKE_DETECTION", prefs.getBoolean("wake_detection", true)) ?: prefs.getBoolean("wake_detection", true)
        maxDurationHours = intent?.getIntExtra("MAX_HOURS", prefs.getInt("max_duration_hours", 10)) ?: prefs.getInt("max_duration_hours", 10)
        antiSnorePulseEnabled = prefs.getBoolean("anti_snore_pulse", false)
        antiSnorePulseStrength = prefs.getInt("anti_snore_strength", 2)

        notifyShowLiveCount = prefs.getBoolean("notify_live_count", true)
        notifyWakeWithSound = prefs.getBoolean("notify_wake_sound", true)
        notifyMorningSummary = prefs.getBoolean("notify_morning_summary", true)

        isRunning = true
        isSavingSession = false
        isFinalized = false
        askIfAwake = false
        snoreTicks = 0
        saveSnapshotTicks = 0
        loudSnoreStreakTicks = 0
        chartHistory.clear()
        currentSegmentBars.clear()
        eventCounter = 0
        lastNotificationText = ""
        activeHandlingScore = 0f
        screenInteractiveTicks = 0
        hasBaseline = false
        lastWakeAlertElapsedMs = SystemClock.elapsedRealtime()

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GuteNacht::SnoreWakeLockTag"
        ).apply {
            setReferenceCounted(false)
            acquire((maxDurationHours + 2) * 60 * 60 * 1000L)
        }

        startWallClockMs = System.currentTimeMillis()
        serviceStartElapsedMs = SystemClock.elapsedRealtime()
        totalDelayMs = if (isTestMode) 0L else delayMinutes * 60 * 1000L
        remainingDelaySeconds = (totalDelayMs / 1000L).toInt()
        isWaitingDelay = remainingDelaySeconds > 0
        recordingStartElapsedMs = serviceStartElapsedMs + totalDelayMs

        val startStr = SimpleDateFormat("dd.MM.yyyy_HH-mm", Locale.getDefault()).format(Date(startWallClockMs))
        sessionDir = File(getExternalFilesDir(null), "Session_${startStr}_ACTIVE").apply { mkdirs() }
        saveIncrementalSnapshot()

        val initialText = if (isWaitingDelay) {
            val mins = remainingDelaySeconds / 60
            val secs = remainingDelaySeconds % 60
            String.format(Locale.getDefault(), "Einschlafphase aktiv – Start in %02d:%02d Min.", mins, secs)
        } else {
            formatActiveStatusText()
        }
        startForegroundImmediate(initialText)

        if (wakeDetectionEnabled) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        startNewSegment()
        startMainLoop()

        return START_STICKY
    }

    private fun formatActiveStatusText(): String {
        val prefix = if (isTestMode) "Testmodus aktiv" else "Überwachung aktiv"
        return if (notifyShowLiveCount) {
            "$prefix (ab ${thresholdDb.toInt()} dB) • $eventCounter Spuren"
        } else {
            "$prefix (ab ${thresholdDb.toInt()} dB)"
        }
    }

    private fun buildStatusNotification(text: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SnoreRecordService::class.java).apply { action = ACTION_STOP_RECORDING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, statusChannelId)
            .setContentTitle("Gute Nacht")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Aufnahme beenden", stopIntent)
            .build()
    }

    private fun triggerWakeUpAlert() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeAlertElapsedMs < 3 * 60_000L) return
        lastWakeAlertElapsedMs = now
        askIfAwake = true

        val openAppIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, SnoreRecordService::class.java).apply { action = ACTION_STOP_RECORDING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val keepSleepingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, SnoreRecordService::class.java).apply { action = ACTION_KEEP_SLEEPING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val chosenChannel = if (notifyWakeWithSound) wakeAlertLoudChannelId else wakeAlertSilentChannelId
        val builder = NotificationCompat.Builder(this, chosenChannel)
            .setContentTitle("Guten Morgen! Bist du wach?")
            .setContentText("Die Schnarch-Aufnahme läuft noch im Hintergrund. Möchtest du sie beenden?")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(if (notifyWakeWithSound) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Ja, beenden", stopIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Weiterschlafen", keepSleepingIntent)

        if (notifyWakeWithSound) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        } else {
            builder.setSilent(true)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(wakeAlertNotificationId, builder.build())
    }

    private fun sendMorningSummaryNotification(score: Int, snoreSecs: Int, eventsCount: Int) {
        if (!notifyMorningSummary) return
        val openAppIntent = PendingIntent.getActivity(
            this,
            5,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = snoreSecs / 60
        val secs = snoreSecs % 60
        val durText = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

        val summaryNotification = NotificationCompat.Builder(this, summaryChannelId)
            .setContentTitle("Schlaf-Analyse gespeichert")
            .setContentText("Schnarch-Score: $score% • $eventsCount Spuren ($durText)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(summaryNotificationId, summaryNotification)
    }

    private fun triggerGentleAntiSnoreVibration() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAntiSnorePulseMs < 60_000L) return
        lastAntiSnorePulseMs = now
        vibrateAntiSnorePattern(this, antiSnorePulseStrength)
    }

    private fun cancelWakeAlertNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(wakeAlertNotificationId)
    }

    private fun startForegroundImmediate(text: String) {
        lastNotificationText = text
        val notification = buildStatusNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(statusNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(statusNotificationId, notification)
        }
    }

    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(statusNotificationId, buildStatusNotification(text))
    }

    private fun startNewSegment() {
        segmentTicks = 0
        silenceTicks = 0
        loudTicksInCurrentSegment = 0
        isEventTriggered = false
        currentEventMaxDb = 0
        currentSegmentBars.clear()
        currentSegmentFile = File(sessionDir, "temp_listen.m4a")
        recorder?.startRecording(currentSegmentFile!!)
    }

    private fun finalizeCurrentSegment(discardOnly: Boolean = false) {
        recorder?.stopRecording()
        val file = currentSegmentFile
        val clipSecs = (segmentTicks / TICKS_PER_SECOND).coerceAtLeast(1)

        if (!discardOnly && isEventTriggered && file != null && file.exists() && file.length() > 512L && clipSecs >= 2) {
            eventCounter++
            val num = String.format(Locale.US, "%03d", eventCounter)
            val baseName = "Event_${num}_${currentEventStartTime}_${currentEventMaxDb}dB_${clipSecs}s"
            val eventFile = File(sessionDir, "$baseName.m4a")
            val barsFile = File(sessionDir, "$baseName.bars")

            file.renameTo(eventFile)
            if (currentSegmentBars.isNotEmpty()) {
                try {
                    barsFile.writeText(currentSegmentBars.joinToString(","))
                } catch (_: Exception) {}
            }
            saveIncrementalSnapshot()
        } else {
            file?.delete()
        }
    }

    private fun saveIncrementalSnapshot() {
        val dir = sessionDir ?: return
        if (!dir.exists()) return
        try {
            val chartFile = File(dir, "chart.csv")
            if (chartHistory.isNotEmpty()) {
                chartFile.writeText(chartHistory.joinToString(","))
            }
            val nowElapsed = SystemClock.elapsedRealtime()
            val totalSecs = (((nowElapsed - serviceStartElapsedMs) / 1000L).toInt()).coerceAtLeast(1)
            val activeListenSecs = if (isWaitingDelay) 1 else (((nowElapsed - recordingStartElapsedMs) / 1000L).toInt()).coerceAtLeast(1)
            val snoreSecs = (snoreTicks / TICKS_PER_SECOND)
            val score = ((snoreSecs.toFloat() / activeListenSecs.toFloat()) * 100).toInt().coerceIn(0, 100)
            val endStr = SimpleDateFormat("HH-mm", Locale.getDefault()).format(Date())

            val metaFile = File(dir, "meta.txt")
            metaFile.writeText("$startWallClockMs|$endStr|$totalSecs|$snoreSecs|$score")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startMainLoop() {
        loopRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                val nowElapsed = SystemClock.elapsedRealtime()
                val elapsedSinceStartMs = nowElapsed - serviceStartElapsedMs

                if (elapsedSinceStartMs >= maxDurationHours * 3600_000L) {
                    stopSelf()
                    return
                }

                if (isWaitingDelay) {
                    val remainingMs = totalDelayMs - elapsedSinceStartMs
                    if (remainingMs > 0) {
                        remainingDelaySeconds = ((remainingMs + 999) / 1000).toInt()
                        val mins = remainingDelaySeconds / 60
                        val secs = remainingDelaySeconds % 60
                        updateNotification(
                            String.format(Locale.getDefault(), "Einschlafphase aktiv – Start in %02d:%02d Min.", mins, secs)
                        )

                        segmentTicks++
                        if (segmentTicks >= 30) {
                            finalizeCurrentSegment(discardOnly = true)
                            startNewSegment()
                        }

                        handler.postDelayed(this, TICK_INTERVAL_MS)
                        return
                    } else {
                        isWaitingDelay = false
                        remainingDelaySeconds = 0
                        recordingStartElapsedMs = nowElapsed
                        finalizeCurrentSegment(discardOnly = true)
                        startNewSegment()
                        updateNotification(formatActiveStatusText())
                    }
                }

                if (wakeDetectionEnabled && (nowElapsed - recordingStartElapsedMs) > 60_000L) {
                    if (powerManager.isInteractive) {
                        screenInteractiveTicks++
                        if (screenInteractiveTicks >= 10 * TICKS_PER_SECOND && !askIfAwake) {
                            triggerWakeUpAlert()
                            screenInteractiveTicks = 0
                        }
                    } else {
                        screenInteractiveTicks = 0
                    }
                }

                segmentTicks++
                saveSnapshotTicks++

                val amp = recorder?.getAmplitude() ?: 0
                val currentDb = if (amp > 0) (20 * log10(amp.toDouble())).toInt().coerceAtLeast(0) else 0

                chartHistory.add(currentDb)
                currentSegmentBars.add(currentDb)

                if (saveSnapshotTicks >= 50) {
                    saveSnapshotTicks = 0
                    saveIncrementalSnapshot()
                }

                if (currentDb >= thresholdDb) {
                    snoreTicks++
                    silenceTicks = 0
                    loudTicksInCurrentSegment++

                    if (!isEventTriggered && loudTicksInCurrentSegment >= 3) {
                        isEventTriggered = true
                        currentEventStartTime = SimpleDateFormat("dd.MM.yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
                    }
                    if (currentDb > currentEventMaxDb) {
                        currentEventMaxDb = currentDb
                    }

                    if (antiSnorePulseEnabled && currentDb >= 70) {
                        loudSnoreStreakTicks++
                        if (loudSnoreStreakTicks >= 15) {
                            loudSnoreStreakTicks = 0
                            triggerGentleAntiSnoreVibration()
                        }
                    } else {
                        loudSnoreStreakTicks = 0
                    }
                } else {
                    silenceTicks++
                    loudSnoreStreakTicks = 0
                }

                val silenceLimitTicks = silenceToleranceSecs * TICKS_PER_SECOND
                val maxSegmentTicks = 90 * TICKS_PER_SECOND

                if (isEventTriggered && (silenceTicks >= silenceLimitTicks || segmentTicks >= maxSegmentTicks)) {
                    finalizeCurrentSegment(discardOnly = false)
                    startNewSegment()
                    updateNotification(formatActiveStatusText())
                } else if (!isEventTriggered && segmentTicks >= 30 && loudTicksInCurrentSegment == 0) {
                    finalizeCurrentSegment(discardOnly = true)
                    startNewSegment()
                } else if (!isEventTriggered && segmentTicks >= 40) {
                    finalizeCurrentSegment(discardOnly = true)
                    startNewSegment()
                }

                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
        handler.post(loopRunnable!!)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!wakeDetectionEnabled || isWaitingDelay) return
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (!hasBaseline) {
                baseX = x; baseY = y; baseZ = z
                hasBaseline = true
            }

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = abs(currentAcceleration - lastAcceleration)

            val tiltDiff = sqrt(((x - baseX) * (x - baseX) + (y - baseY) * (y - baseY) + (z - baseZ) * (z - baseZ)).toDouble()).toFloat()

            baseX += (x - baseX) * 0.02f
            baseY += (y - baseY) * 0.02f
            baseZ += (z - baseZ) * 0.02f

            val isScreenOn = powerManager.isInteractive

            if (isScreenOn && (delta > 0.25f || tiltDiff > 2.2f)) {
                activeHandlingScore += 1.5f
            } else if (!isScreenOn && (delta > 0.45f && tiltDiff > 2.8f)) {
                activeHandlingScore += 1.0f
            } else {
                activeHandlingScore = (activeHandlingScore - 0.6f).coerceAtLeast(0f)
            }

            if (activeHandlingScore >= 22f) {
                activeHandlingScore = 0f
                triggerWakeUpAlert()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveIncrementalSnapshot()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Punkt 5: Keine doppelte combined.m4a-Kopie mehr erzeugen!
     * Spart 50% Speicherplatz und macht das Beenden der Aufnahme am Morgen extrem schnell.
     */
    private fun finalizeSessionDirectoryAsync() {
        if (isFinalized) return
        isFinalized = true

        val wasWaitingDelay = isWaitingDelay
        isRunning = false
        isWaitingDelay = false
        askIfAwake = false
        isSavingSession = true

        sensorManager.unregisterListener(this)
        loopRunnable?.let { handler.removeCallbacks(it) }
        cancelWakeAlertNotification()

        finalizeCurrentSegment(discardOnly = wasWaitingDelay)

        val dir = sessionDir
        val chartSnapshot = ArrayList(chartHistory)
        val nowElapsed = SystemClock.elapsedRealtime()
        val totalSecs = (((nowElapsed - serviceStartElapsedMs) / 1000L).toInt()).coerceAtLeast(1)
        val activeListenSecs = if (wasWaitingDelay) 1 else (((nowElapsed - recordingStartElapsedMs) / 1000L).toInt()).coerceAtLeast(1)
        val snoreSecs = (snoreTicks / TICKS_PER_SECOND)
        val score = ((snoreSecs.toFloat() / activeListenSecs.toFloat()) * 100).toInt().coerceIn(0, 100)
        val startStr = SimpleDateFormat("dd.MM.yyyy_HH-mm", Locale.getDefault()).format(Date(startWallClockMs))
        val endStr = SimpleDateFormat("HH-mm", Locale.getDefault()).format(Date())
        val lockToRelease = wakeLock

        Thread {
            try {
                if (dir != null && dir.exists()) {
                    val chartFile = File(dir, "chart.csv")
                    if (chartSnapshot.isNotEmpty()) {
                        chartFile.writeText(chartSnapshot.joinToString(","))
                    } else if (!chartFile.exists()) {
                        chartFile.writeText("0")
                    }

                    val eventFiles = dir.listFiles { f -> f.name.startsWith("Event_") && f.extension == "m4a" }
                        ?.sortedBy { it.name } ?: emptyList()

                    val finalDirName = "Session_${startStr}_${endStr}_T${totalSecs}_S${snoreSecs}_SC${score}"
                    dir.renameTo(File(dir.parentFile, finalDirName))

                    if (!wasWaitingDelay) {
                        sendMorningSummaryNotification(score = score, snoreSecs = snoreSecs, eventsCount = eventFiles.size)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSavingSession = false
                try {
                    if (lockToRelease?.isHeld == true) {
                        lockToRelease.release()
                    }
                } catch (_: Exception) {}
            }
        }.start()
    }

    override fun onDestroy() {
        finalizeSessionDirectoryAsync()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusChannel = NotificationChannel(
                statusChannelId,
                "Schnarchaufnahme Status (Lautlos)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Zeigt sofort den laufenden Status der Schnarch-Aufnahme"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val wakeAlertLoudChannel = NotificationChannel(
                wakeAlertLoudChannelId,
                "Aufwach-Erinnerung (Mit Ton/Vibration)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Meldet sich hörbar/spürbar, wenn du wach bist und die Aufnahme noch läuft"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val wakeAlertSilentChannel = NotificationChannel(
                wakeAlertSilentChannelId,
                "Aufwach-Erinnerung (Lautlos)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Meldet sich lautlos, wenn du wach bist und die Aufnahme noch läuft"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val summaryChannel = NotificationChannel(
                summaryChannelId,
                "Schlaf-Bilanz am Morgen",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Zeigt nach dem Beenden der Aufnahme eine Zusammenfassung der Nacht"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(wakeAlertLoudChannel)
            manager.createNotificationChannel(wakeAlertSilentChannel)
            manager.createNotificationChannel(summaryChannel)
        }
    }
}