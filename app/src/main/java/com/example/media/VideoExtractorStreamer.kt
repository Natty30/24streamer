package com.example.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.rtmp.client.RtmpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Reads H.264/AVC video tracks and AAC audio tracks from a local video file
 * using Android's MediaExtractor, extracts SPS/PPS and AudioSpecificConfig,
 * paces the transmission to match real-time playback, and feeds the stream to RtmpClient.
 * Supports continuous 24/7 video looping and audio muting.
 */
class VideoExtractorStreamer(
    private val context: Context,
    private val uri: Uri,
    private val serverUrl: String,
    private val streamKey: String,
    private val targetBitrateKbps: Int,
    private val loopVideo: Boolean,
    private val muteAudio: Boolean,
    private val onStatusUpdate: (Status) -> Unit,
    private val onMetricsUpdate: (Metrics) -> Unit
) {

    enum class Status {
        CONNECTING,
        LIVE,
        RECONNECTING,
        STOPPED,
        ERROR
    }

    data class Metrics(
        val durationMs: Long,
        val fps: Double,
        val bitrateKbps: Int,
        val totalBytesSent: Long,
        val loopsCompleted: Int
    )

    @Volatile
    var isRunning = false
        private set

    private var rtmpClient: RtmpClient? = null
    private var pfd: ParcelFileDescriptor? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        isRunning = true
        var retryCount = 0
        val maxRetries = 10

        while (isRunning && isActive) {
            try {
                onStatusUpdate(if (retryCount == 0) Status.CONNECTING else Status.RECONNECTING)

                // 1. Initialize RTMP Client
                val client = RtmpClient()
                rtmpClient = client
                client.connect(serverUrl, streamKey)

                // 2. Stream media loop
                retryCount = 0 // reset on successful connection
                onStatusUpdate(Status.LIVE)

                streamMedia(client)

                // If stream finished normally (e.g. loop disabled)
                if (!loopVideo || !isRunning) {
                    break
                }
            } catch (e: Exception) {
                if (!isRunning) break
                retryCount++
                if (retryCount > maxRetries) {
                    onStatusUpdate(Status.ERROR)
                    break
                }
                onStatusUpdate(Status.RECONNECTING)
                // Exponential backoff retry: 2s, 4s, 8s, up to 15s
                val delayMs = minOf(15000L, 2000L * (1L shl (retryCount - 1)))
                kotlinx.coroutines.delay(delayMs)
            } finally {
                try {
                    rtmpClient?.close()
                } catch (_: Exception) {}
            }
        }

        stop()
        onStatusUpdate(Status.STOPPED)
    }

    private suspend fun streamMedia(client: RtmpClient) {
        val extractor = MediaExtractor()
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Cannot open video file descriptor")
            extractor.setDataSource(pfd!!.fileDescriptor)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                throw IllegalStateException("No video track found in selected file.")
            }

            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
            } else {
                30.0
            }

            // Extract SPS and PPS from csd-0 and csd-1
            val spsBuffer = videoFormat.getByteBuffer("csd-0")
            val ppsBuffer = videoFormat.getByteBuffer("csd-1")

            val sps = if (spsBuffer != null) cleanNalu(extractBytes(spsBuffer)) else null
            val pps = if (ppsBuffer != null) cleanNalu(extractBytes(ppsBuffer)) else null

            if (sps == null || pps == null) {
                throw IllegalStateException("Video track does not have valid H.264 SPS/PPS parameters.")
            }

            // Audio parameters
            var audioSpecificConfig: ByteArray? = null
            var sampleRate = 44100
            var channels = 2
            if (audioTrackIndex != -1 && audioFormat != null) {
                sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else 44100

                channels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 2

                val audioCsd = audioFormat.getByteBuffer("csd-0")
                if (audioCsd != null) {
                    audioSpecificConfig = extractBytes(audioCsd)
                }
            }

            // Send metadata
            client.sendMetadata(
                width = width,
                height = height,
                frameRate = frameRate,
                videoBitrateKbps = targetBitrateKbps,
                sampleRate = sampleRate,
                channels = channels
            )

            // Send Video Sequence Header
            client.sendVideoSequenceHeader(sps, pps)

            // Send Audio Sequence Header (if audio present and not muted)
            if (audioSpecificConfig != null && !muteAudio) {
                client.sendAudioSequenceHeader(audioSpecificConfig)
            }

            // Select tracks for extraction
            extractor.selectTrack(videoTrackIndex)
            if (audioTrackIndex != -1 && !muteAudio) {
                extractor.selectTrack(audioTrackIndex)
            }

            val buffer = ByteBuffer.allocateDirect(1024 * 1024 * 2) // 2MB sample buffer
            var totalBytesSent = 0L
            var loopCount = 0
            var timeOffsetUs = 0L
            var maxTimeSeenUs = 0L
            val streamStartRealtime = System.nanoTime()
            var firstSampleTimeUs = -1L

            var framesInCurrentSec = 0
            var lastFpsCalcTime = System.currentTimeMillis()
            var currentFps = frameRate
            val streamStartTime = System.currentTimeMillis()

            while (isRunning && client.isPublishing) {
                val sampleSize = extractor.readSampleData(buffer, 0)

                if (sampleSize < 0) {
                    // Reached end of video file
                    if (loopVideo && isRunning) {
                        loopCount++
                        timeOffsetUs = maxTimeSeenUs + 33_333L // ~30fps delta
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        continue
                    } else {
                        // End of stream
                        break
                    }
                }

                val trackIndex = extractor.sampleTrackIndex
                val rawSampleTimeUs = extractor.sampleTime
                if (firstSampleTimeUs == -1L) {
                    firstSampleTimeUs = rawSampleTimeUs
                }

                val adjustedSampleTimeUs = (rawSampleTimeUs - firstSampleTimeUs) + timeOffsetUs
                if (adjustedSampleTimeUs > maxTimeSeenUs) {
                    maxTimeSeenUs = adjustedSampleTimeUs
                }

                val timestampMs = adjustedSampleTimeUs / 1000

                // Real-time pacing: sync transmission to wall-clock time
                val elapsedStreamNanos = System.nanoTime() - streamStartRealtime
                val expectedStreamNanos = adjustedSampleTimeUs * 1000
                val delayNanos = expectedStreamNanos - elapsedStreamNanos
                if (delayNanos > 2_000_000L) { // > 2ms ahead
                    val delayMs = delayNanos / 1_000_000L
                    kotlinx.coroutines.delay(minOf(delayMs, 50L))
                }

                val sampleData = ByteArray(sampleSize)
                buffer.get(sampleData)
                buffer.clear()

                if (trackIndex == videoTrackIndex) {
                    val isKey = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    val normalizedNalu = normalizeAvcc(sampleData)
                    client.sendVideoData(normalizedNalu, timestampMs, isKey)
                    totalBytesSent += normalizedNalu.size + 9
                    framesInCurrentSec++
                } else if (trackIndex == audioTrackIndex && !muteAudio) {
                    client.sendAudioData(sampleData, timestampMs)
                    totalBytesSent += sampleData.size + 2
                }

                // Update FPS & Metrics periodically
                val now = System.currentTimeMillis()
                if (now - lastFpsCalcTime >= 1000) {
                    currentFps = framesInCurrentSec.toDouble()
                    framesInCurrentSec = 0
                    lastFpsCalcTime = now

                    val elapsedDuration = now - streamStartTime
                    onMetricsUpdate(
                        Metrics(
                            durationMs = elapsedDuration,
                            fps = currentFps,
                            bitrateKbps = targetBitrateKbps,
                            totalBytesSent = totalBytesSent,
                            loopsCompleted = loopCount
                        )
                    )
                }

                extractor.advance()
            }
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
            pfd = null
        }
    }

    fun stop() {
        isRunning = false
        try {
            rtmpClient?.close()
        } catch (_: Exception) {}
        try {
            pfd?.close()
        } catch (_: Exception) {}
        pfd = null
    }

    private fun extractBytes(bb: ByteBuffer): ByteArray {
        val copy = bb.duplicate()
        copy.rewind()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }

    private fun cleanNalu(bytes: ByteArray): ByteArray {
        // Strip Annex B start code 0x00 0x00 0x00 0x01 or 0x00 0x00 0x01
        return if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() && bytes[3] == 1.toByte()) {
            bytes.copyOfRange(4, bytes.size)
        } else if (bytes.size >= 3 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
    }

    private fun normalizeAvcc(bytes: ByteArray): ByteArray {
        // If sample begins with 00 00 00 01 Annex-B start code, replace with 4-byte length prefix
        if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() && bytes[3] == 1.toByte()) {
            val len = bytes.size - 4
            val res = bytes.clone()
            res[0] = ((len shr 24) and 0xFF).toByte()
            res[1] = ((len shr 16) and 0xFF).toByte()
            res[2] = ((len shr 8) and 0xFF).toByte()
            res[3] = (len and 0xFF).toByte()
            return res
        }
        return bytes
    }
}
