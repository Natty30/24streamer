package com.example.rtmp.packet

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * RTMP Protocol packet and chunk builder.
 */
data class RtmpHeader(
    val csid: Int,
    val fmt: Int = 0,
    val timestamp: Int = 0,
    val messageLength: Int = 0,
    val messageTypeId: Int = 0,
    val messageStreamId: Int = 0
)

object RtmpPacket {
    const val TYPE_SET_CHUNK_SIZE = 1
    const val TYPE_ABORT_MESSAGE = 2
    const val TYPE_ACKNOWLEDGEMENT = 3
    const val TYPE_USER_CONTROL = 4
    const val TYPE_WINDOW_ACK_SIZE = 5
    const val TYPE_SET_PEER_BANDWIDTH = 6
    const val TYPE_AUDIO = 8
    const val TYPE_VIDEO = 9
    const val TYPE_DATA_AMF0 = 18
    const val TYPE_COMMAND_AMF0 = 20

    const val CSID_CONTROL = 2
    const val CSID_COMMAND = 3
    const val CSID_AUDIO = 4
    const val CSID_VIDEO = 5
    const val CSID_DATA = 6

    /**
     * Write an RTMP message split into chunks according to chunkSize.
     */
    fun writeChunked(
        out: OutputStream,
        csid: Int,
        messageTypeId: Int,
        messageStreamId: Int,
        timestamp: Int,
        payload: ByteArray,
        chunkSize: Int
    ) {
        val totalLen = payload.size
        var offset = 0
        var isFirstChunk = true

        while (offset < totalLen) {
            val chunkLen = minOf(chunkSize, totalLen - offset)

            if (isFirstChunk) {
                // Header format 0
                writeBasicHeader(out, 0, csid)
                writeMessageHeaderFmt0(out, timestamp, totalLen, messageTypeId, messageStreamId)
                isFirstChunk = false
            } else {
                // Header format 3 (continuation of same message)
                writeBasicHeader(out, 3, csid)
            }

            out.write(payload, offset, chunkLen)
            offset += chunkLen
        }
    }

    private fun writeBasicHeader(out: OutputStream, fmt: Int, csid: Int) {
        val fmtBits = (fmt and 0x03) shl 6
        when {
            csid < 64 -> {
                out.write(fmtBits or csid)
            }
            csid < 320 -> {
                out.write(fmtBits)
                out.write(csid - 64)
            }
            else -> {
                out.write(fmtBits or 1)
                val diff = csid - 64
                out.write(diff and 0xFF)
                out.write((diff shr 8) and 0xFF)
            }
        }
    }

    private fun writeMessageHeaderFmt0(
        out: OutputStream,
        timestamp: Int,
        messageLength: Int,
        messageTypeId: Int,
        messageStreamId: Int
    ) {
        // Timestamp (3 bytes, big-endian)
        val ts = if (timestamp >= 0xFFFFFF) 0xFFFFFF else timestamp
        out.write((ts shr 16) and 0xFF)
        out.write((ts shr 8) and 0xFF)
        out.write(ts and 0xFF)

        // Message length (3 bytes, big-endian)
        out.write((messageLength shr 16) and 0xFF)
        out.write((messageLength shr 8) and 0xFF)
        out.write(messageLength and 0xFF)

        // Message type ID (1 byte)
        out.write(messageTypeId and 0xFF)

        // Message stream ID (4 bytes, little-endian!)
        out.write(messageStreamId and 0xFF)
        out.write((messageStreamId shr 8) and 0xFF)
        out.write((messageStreamId shr 16) and 0xFF)
        out.write((messageStreamId shr 24) and 0xFF)

        // Extended timestamp if >= 0xFFFFFF
        if (ts == 0xFFFFFF) {
            out.write((timestamp shr 24) and 0xFF)
            out.write((timestamp shr 16) and 0xFF)
            out.write((timestamp shr 8) and 0xFF)
            out.write(timestamp and 0xFF)
        }
    }

    fun buildSetChunkSize(chunkSize: Int): ByteArray {
        val payload = ByteArray(4)
        payload[0] = ((chunkSize shr 24) and 0x7F).toByte()
        payload[1] = ((chunkSize shr 16) and 0xFF).toByte()
        payload[2] = ((chunkSize shr 8) and 0xFF).toByte()
        payload[3] = (chunkSize and 0xFF).toByte()
        return payload
    }

    fun buildWindowAckSize(ackSize: Int): ByteArray {
        val payload = ByteArray(4)
        payload[0] = ((ackSize shr 24) and 0xFF).toByte()
        payload[1] = ((ackSize shr 16) and 0xFF).toByte()
        payload[2] = ((ackSize shr 8) and 0xFF).toByte()
        payload[3] = (ackSize and 0xFF).toByte()
        return payload
    }

    fun buildSetPeerBandwidth(bandwidth: Int, limitType: Byte = 2): ByteArray {
        val payload = ByteArray(5)
        payload[0] = ((bandwidth shr 24) and 0xFF).toByte()
        payload[1] = ((bandwidth shr 16) and 0xFF).toByte()
        payload[2] = ((bandwidth shr 8) and 0xFF).toByte()
        payload[3] = (bandwidth and 0xFF).toByte()
        payload[4] = limitType
        return payload
    }
}
