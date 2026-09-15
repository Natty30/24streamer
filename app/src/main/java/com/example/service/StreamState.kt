package com.example.service

import android.net.Uri

sealed interface StreamStatus {
    object Idle : StreamStatus
    object Connecting : StreamStatus
    data class Live(val durationMs: Long = 0L) : StreamStatus
    data class Reconnecting(val attempt: Int = 1) : StreamStatus
    object Stopped : StreamStatus
    object Expired : StreamStatus
    data class Error(val message: String) : StreamStatus
}

data class StreamMetrics(
    val durationMs: Long = 0L,
    val fps: Double = 0.0,
    val bitrateKbps: Int = 0,
    val totalBytesSent: Long = 0L,
    val loopsCompleted: Int = 0
)

data class ActiveStreamInfo(
    val videoUri: Uri? = null,
    val videoName: String = "",
    val destinationName: String = "YouTube",
    val serverUrl: String = "",
    val resolution: String = "1080p",
    val bitrateKbps: Int = 4500,
    val loopVideo: Boolean = true,
    val muteAudio: Boolean = false,
    val autoReconnect: Boolean = true
)
