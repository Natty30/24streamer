package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.media.VideoExtractorStreamer
import com.example.media.VideoMetadataUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service that handles 24/7 background streaming.
 * Maintains WakeLock, WifiLock, and persistent notification with live status and Stop action.
 */
class StreamingForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_ADD_TIME = "com.example.service.ACTION_ADD_TIME"
        const val EXTRA_ADD_MINUTES = "extra_add_minutes"

        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_NAME = "extra_video_name"
        const val EXTRA_DESTINATION = "extra_destination"
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_STREAM_KEY = "extra_stream_key"
        const val EXTRA_RESOLUTION = "extra_resolution"
        const val EXTRA_BITRATE = "extra_bitrate"
        const val EXTRA_LOOP = "extra_loop"
        const val EXTRA_MUTE = "extra_mute"
        const val EXTRA_AUTO_RECONNECT = "extra_auto_reconnect"

        private const val CHANNEL_ID = "streamer_live_channel"
        private const val NOTIFICATION_ID = 1001

        private val _streamStatus = MutableStateFlow<StreamStatus>(StreamStatus.Idle)
        val streamStatus: StateFlow<StreamStatus> = _streamStatus.asStateFlow()

        private val _streamMetrics = MutableStateFlow(StreamMetrics())
        val streamMetrics: StateFlow<StreamMetrics> = _streamMetrics.asStateFlow()

        private val _activeInfo = MutableStateFlow(ActiveStreamInfo())
        val activeInfo: StateFlow<ActiveStreamInfo> = _activeInfo.asStateFlow()

        private val _streamingTimeRemainingSec = MutableStateFlow(0L)
        val streamingTimeRemainingSec: StateFlow<Long> = _streamingTimeRemainingSec.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var streamingJob: Job? = null
    private var timeTickerJob: Job? = null
    private var streamer: VideoExtractorStreamer? = null

    private val credentialStore by lazy { com.example.security.SecureCredentialStore(applicationContext) }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var currentDestination = "YouTube"
    private var currentVideoName = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        _streamingTimeRemainingSec.value = credentialStore.loadStreamingTimeRemainingSeconds()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD_TIME -> {
                val minutes = intent.getLongExtra(EXTRA_ADD_MINUTES, 30L)
                val newRemaining = credentialStore.addStreamingTimeMinutes(minutes)
                _streamingTimeRemainingSec.value = newRemaining
                if (_streamStatus.value is StreamStatus.Live) {
                    val remStr = formatTimeRemaining(newRemaining)
                    updateNotification("🔴 Live — $currentDestination", "⏱ Remaining: $remStr")
                }
            }
            ACTION_START -> {
                val availableSec = credentialStore.loadStreamingTimeRemainingSeconds()
                if (availableSec <= 0L) {
                    _streamStatus.value = StreamStatus.Expired
                    return START_NOT_STICKY
                }
                _streamingTimeRemainingSec.value = availableSec

                val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return START_NOT_STICKY
                val videoUri = Uri.parse(uriStr)
                val videoName = intent.getStringExtra(EXTRA_VIDEO_NAME) ?: "video.mp4"
                val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: "YouTube"
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: ""
                val streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""
                val resolution = intent.getStringExtra(EXTRA_RESOLUTION) ?: "1080p"
                val bitrate = intent.getIntExtra(EXTRA_BITRATE, 4500)
                val loop = intent.getBooleanExtra(EXTRA_LOOP, true)
                val mute = intent.getBooleanExtra(EXTRA_MUTE, false)
                val autoReconnect = intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, true)

                currentDestination = destination
                currentVideoName = videoName

                _activeInfo.value = ActiveStreamInfo(
                    videoUri = videoUri,
                    videoName = videoName,
                    destinationName = destination,
                    serverUrl = serverUrl,
                    resolution = resolution,
                    bitrateKbps = bitrate,
                    loopVideo = loop,
                    muteAudio = mute,
                    autoReconnect = autoReconnect
                )

                startForegroundServiceInternal()
                startStreaming(videoUri, serverUrl, streamKey, bitrate, loop, mute)
            }
            ACTION_STOP -> {
                stopStreamingInternal()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceInternal() {
        acquireLocks()
        val notification = buildNotification("Connecting...", "Establishing stream to $currentDestination")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startStreaming(
        videoUri: Uri,
        serverUrl: String,
        streamKey: String,
        bitrateKbps: Int,
        loopVideo: Boolean,
        muteAudio: Boolean
    ) {
        streamingJob?.cancel()
        streamer?.stop()

        _streamStatus.value = StreamStatus.Connecting

        val newStreamer = VideoExtractorStreamer(
            context = applicationContext,
            uri = videoUri,
            serverUrl = serverUrl,
            streamKey = streamKey,
            targetBitrateKbps = bitrateKbps,
            loopVideo = loopVideo,
            muteAudio = muteAudio,
            onStatusUpdate = { status ->
                serviceScope.launch {
                    when (status) {
                        VideoExtractorStreamer.Status.CONNECTING -> {
                            _streamStatus.value = StreamStatus.Connecting
                            updateNotification("Connecting...", "Connecting to $currentDestination")
                        }
                        VideoExtractorStreamer.Status.LIVE -> {
                            _streamStatus.value = StreamStatus.Live(0L)
                            startTimeTicker()
                            val remStr = formatTimeRemaining(_streamingTimeRemainingSec.value)
                            updateNotification("🔴 Live — $currentDestination", "⏱ Remaining: $remStr | $currentVideoName")
                        }
                        VideoExtractorStreamer.Status.RECONNECTING -> {
                            _streamStatus.value = StreamStatus.Reconnecting(1)
                            updateNotification("Reconnecting...", "Attempting to reconnect to $currentDestination")
                        }
                        VideoExtractorStreamer.Status.STOPPED -> {
                            stopStreamingInternal(isExpired = false)
                        }
                        VideoExtractorStreamer.Status.ERROR -> {
                            _streamStatus.value = StreamStatus.Error("Connection lost or server rejected stream.")
                            stopStreamingInternal(isExpired = false)
                        }
                    }
                }
            },
            onMetricsUpdate = { metrics ->
                serviceScope.launch {
                    _streamMetrics.value = StreamMetrics(
                        durationMs = metrics.durationMs,
                        fps = metrics.fps,
                        bitrateKbps = metrics.bitrateKbps,
                        totalBytesSent = metrics.totalBytesSent,
                        loopsCompleted = metrics.loopsCompleted
                    )
                    _streamStatus.value = StreamStatus.Live(metrics.durationMs)
                    val durationStr = VideoMetadataUtil.formatDuration(metrics.durationMs)
                    val remStr = formatTimeRemaining(_streamingTimeRemainingSec.value)
                    val subtext = if (metrics.loopsCompleted > 0) {
                        "⏱ $remStr | $durationStr (Loop #${metrics.loopsCompleted + 1})"
                    } else {
                        "⏱ $remStr | $durationStr"
                    }
                    updateNotification("🔴 Live — $currentDestination", subtext)
                }
            }
        )

        streamer = newStreamer

        streamingJob = serviceScope.launch(Dispatchers.IO) {
            newStreamer.start()
        }
    }

    private fun startTimeTicker() {
        timeTickerJob?.cancel()
        timeTickerJob = serviceScope.launch(Dispatchers.Default) {
            var lastElapsed = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                kotlinx.coroutines.delay(1000L)
                val now = android.os.SystemClock.elapsedRealtime()
                val deltaSec = (now - lastElapsed) / 1000L
                if (deltaSec >= 1L) {
                    lastElapsed = now
                    val remaining = credentialStore.consumeStreamingTimeSeconds(deltaSec)
                    _streamingTimeRemainingSec.value = remaining
                    if (remaining <= 0L) {
                        withContext(Dispatchers.Main) {
                            stopStreamingInternal(isExpired = true)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun formatTimeRemaining(totalSec: Long): String {
        val safe = maxOf(0L, totalSec)
        val hours = safe / 3600
        val mins = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    private fun stopStreamingInternal(isExpired: Boolean = false) {
        timeTickerJob?.cancel()
        timeTickerJob = null
        streamingJob?.cancel()
        streamer?.stop()
        streamer = null
        releaseLocks()

        _streamStatus.value = if (isExpired) StreamStatus.Expired else StreamStatus.Stopped
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "STOP STREAM", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "24/7 Live Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live streaming status and background controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "247Streamer::StreamingLock")?.apply {
                acquire(24 * 60 * 60 * 1000L) // 24 hours
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "247Streamer::WifiLock")?.apply {
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreamingInternal()
        serviceScope.cancel()
    }
}
