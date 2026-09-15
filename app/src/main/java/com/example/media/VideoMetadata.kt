package com.example.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileDescriptor

data class VideoInfo(
    val uri: Uri,
    val filename: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val videoMime: String?,
    val audioMime: String?,
    val isH264: Boolean,
    val isAac: Boolean,
    val hasAudio: Boolean,
    val sizeBytes: Long
)

object VideoMetadataUtil {

    fun extractInfo(context: Context, uri: Uri): VideoInfo {
        var filename = "video.mp4"
        var sizeBytes = 0L

        // Query filename and size
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) filename = cursor.getString(nameIndex) ?: "video.mp4"
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        var bitrate = 0

        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            // Handle rotation if video is rotated 90 or 270 degrees
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Check codecs using MediaExtractor
        var videoMime: String? = null
        var audioMime: String? = null
        var isH264 = false
        var isAac = false
        var hasAudio = false

        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        videoMime = mime
                        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ||
                            mime.contains("avc", ignoreCase = true) ||
                            mime.contains("h264", ignoreCase = true)
                        ) {
                            isH264 = true
                        }
                    } else if (mime.startsWith("audio/")) {
                        audioMime = mime
                        hasAudio = true
                        if (mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) ||
                            mime.contains("mp4a", ignoreCase = true) ||
                            mime.contains("aac", ignoreCase = true)
                        ) {
                            isAac = true
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }

        return VideoInfo(
            uri = uri,
            filename = filename,
            durationMs = durationMs,
            width = width,
            height = height,
            bitrate = bitrate,
            videoMime = videoMime,
            audioMime = audioMime,
            isH264 = isH264,
            isAac = isAac,
            hasAudio = hasAudio,
            sizeBytes = sizeBytes
        )
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
