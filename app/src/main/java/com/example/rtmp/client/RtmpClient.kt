package com.example.rtmp.client

import com.example.rtmp.amf.Amf0
import com.example.rtmp.packet.RtmpHeader
import com.example.rtmp.packet.RtmpPacket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Production-ready native RTMP and RTMPS client for Android.
 * Supports TLS encryption for RTMPS, AMF0 commands, chunking,
 * metadata transmission, AVC/H.264 video NALUs, and AAC audio frames.
 */
class RtmpClient {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private var chunkSize = 4096
    private var streamId = 1
    private var transactionId = 1

    @Volatile
    var isConnected = false
        private set

    @Volatile
    var isPublishing = false
        private set

    data class ParsedRtmpUrl(
        val isRtmps: Boolean,
        val host: String,
        val port: Int,
        val app: String,
        val streamKey: String
    )

    companion object {
        fun parseUrl(serverUrl: String, streamKeyInput: String): ParsedRtmpUrl {
            var url = serverUrl.trim()
            val isRtmps = url.startsWith("rtmps://", ignoreCase = true)
            val isRtmp = url.startsWith("rtmp://", ignoreCase = true)

            if (!isRtmp && !isRtmps) {
                // Default to rtmp if scheme missing
                url = "rtmp://$url"
            }

            val uri = URI(url)
            val host = uri.host ?: "127.0.0.1"
            val defaultPort = if (isRtmps) 443 else 1935
            val port = if (uri.port > 0) uri.port else defaultPort

            val rawPath = (uri.rawPath ?: "").trim('/')
            val pathSegments = if (rawPath.isEmpty()) emptyList() else rawPath.split('/')

            val app: String
            val finalStreamKey: String

            if (streamKeyInput.isNotBlank()) {
                // User provided a separate stream key
                app = if (pathSegments.isNotEmpty()) pathSegments.joinToString("/") else "live"
                finalStreamKey = streamKeyInput.trim()
            } else {
                // Stream key might be in the URL path (last segment)
                if (pathSegments.size > 1) {
                    app = pathSegments.dropLast(1).joinToString("/")
                    finalStreamKey = pathSegments.last()
                } else if (pathSegments.size == 1) {
                    app = pathSegments.first()
                    finalStreamKey = ""
                } else {
                    app = "live"
                    finalStreamKey = ""
                }
            }

            return ParsedRtmpUrl(
                isRtmps = isRtmps,
                host = host,
                port = port,
                app = app,
                streamKey = finalStreamKey
            )
        }
    }

    /**
     * Connects to the RTMP/RTMPS server and negotiates publishing.
     */
    fun connect(serverUrl: String, streamKey: String, timeoutMs: Int = 15000) {
        val parsed = parseUrl(serverUrl, streamKey)
        if (parsed.streamKey.isBlank()) {
            throw IllegalArgumentException("Stream key is missing.")
        }

        // 1. Establish TCP/TLS socket
        val rawSocket = Socket()
        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = timeoutMs
        rawSocket.connect(InetSocketAddress(parsed.host, parsed.port), timeoutMs)

        socket = if (parsed.isRtmps) {
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, parsed.host, parsed.port, true) as SSLSocket
            sslSocket.startHandshake()
            sslSocket
        } else {
            rawSocket
        }

        input = BufferedInputStream(socket!!.getInputStream(), 65536)
        output = BufferedOutputStream(socket!!.getOutputStream(), 65536)

        // 2. Perform Handshake (C0+C1, S0+S1+S2, C2)
        doHandshake()

        // 3. Set Chunk Size (4096)
        chunkSize = 4096
        val setChunkSizePayload = RtmpPacket.buildSetChunkSize(chunkSize)
        RtmpPacket.writeChunked(
            output!!,
            RtmpPacket.CSID_CONTROL,
            RtmpPacket.TYPE_SET_CHUNK_SIZE,
            0,
            0,
            setChunkSizePayload,
            chunkSize
        )

        // Window Ack Size
        val windowAckPayload = RtmpPacket.buildWindowAckSize(2500000)
        RtmpPacket.writeChunked(
            output!!,
            RtmpPacket.CSID_CONTROL,
            RtmpPacket.TYPE_WINDOW_ACK_SIZE,
            0,
            0,
            windowAckPayload,
            chunkSize
        )

        output!!.flush()

        // 4. Send "connect" command
        val tcUrl = "${if (parsed.isRtmps) "rtmps" else "rtmp"}://${parsed.host}:${parsed.port}/${parsed.app}"
        sendConnectCommand(parsed.app, tcUrl)
        output!!.flush()

        // Read until we get connect response (_result or onBWDone)
        waitForConnectResult()

        // 5. Send "createStream" command
        sendCreateStreamCommand()
        output!!.flush()

        // Read stream ID from response
        waitForCreateStreamResult()

        // 6. Send "publish" command
        sendPublishCommand(parsed.streamKey)
        output!!.flush()

        isConnected = true
        isPublishing = true
    }

    private fun doHandshake() {
        val out = output!!
        val inp = input!!

        // C0 (1 byte: 0x03)
        out.write(0x03)

        // C1 (1536 bytes: 4 bytes time, 4 bytes zero, 1528 bytes random)
        val c1 = ByteArray(1536)
        SecureRandom().nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0 // timestamp 0
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0 // zero
        out.write(c1)
        out.flush()

        // Read S0 (1 byte: 0x03)
        val s0 = inp.read()
        if (s0 != 0x03) {
            throw IllegalStateException("Invalid RTMP handshake S0 marker: $s0")
        }

        // Read S1 (1536 bytes)
        val s1 = ByteArray(1536)
        readFully(inp, s1)

        // Read S2 (1536 bytes)
        val s2 = ByteArray(1536)
        readFully(inp, s2)

        // Send C2 (1536 bytes echoing S1)
        out.write(s1)
        out.flush()
    }

    private fun sendConnectCommand(appName: String, tcUrl: String) {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "connect")
        Amf0.writeNumber(baos, transactionId++.toDouble())

        val connectObj = mutableMapOf<String, Any?>()
        connectObj["app"] = appName
        connectObj["flashVer"] = "FMLE/3.0 (compatible; FMSc/1.0)"
        connectObj["tcUrl"] = tcUrl
        connectObj["fpad"] = false
        connectObj["capabilities"] = 15.0
        connectObj["audioCodecs"] = 3191.0
        connectObj["videoCodecs"] = 252.0
        connectObj["videoFunction"] = 1.0
        Amf0.writeObject(baos, connectObj)

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            output!!,
            RtmpPacket.CSID_COMMAND,
            RtmpPacket.TYPE_COMMAND_AMF0,
            0,
            0,
            payload,
            chunkSize
        )
    }

    private fun waitForConnectResult() {
        // Read chunks until command _result is received
        // Simple loop to parse incoming RTMP chunks
        var received = false
        var attempts = 0
        while (!received && attempts < 50) {
            attempts++
            val header = readChunkHeader() ?: break
            val payload = ByteArray(header.messageLength)
            readChunkPayload(header.csid, payload, header.messageLength)

            if (header.messageTypeId == RtmpPacket.TYPE_COMMAND_AMF0) {
                val bais = ByteArrayInputStream(payload)
                val cmd = Amf0.readValue(bais) as? String
                if (cmd == "_result" || cmd == "onBWDone") {
                    received = true
                } else if (cmd == "_error") {
                    throw IllegalStateException("Server returned _error on connect")
                }
            } else if (header.messageTypeId == RtmpPacket.TYPE_SET_CHUNK_SIZE) {
                if (payload.size >= 4) {
                    val newSize = ((payload[0].toInt() and 0x7F) shl 24) or
                            ((payload[1].toInt() and 0xFF) shl 16) or
                            ((payload[2].toInt() and 0xFF) shl 8) or
                            (payload[3].toInt() and 0xFF)
                    // Note: We don't change our outgoing chunk size, but server incoming may change
                }
            }
        }
    }

    private fun sendCreateStreamCommand() {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "createStream")
        Amf0.writeNumber(baos, transactionId++.toDouble())
        Amf0.writeNull(baos)

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            output!!,
            RtmpPacket.CSID_COMMAND,
            RtmpPacket.TYPE_COMMAND_AMF0,
            0,
            0,
            payload,
            chunkSize
        )
    }

    private fun waitForCreateStreamResult() {
        var received = false
        var attempts = 0
        while (!received && attempts < 50) {
            attempts++
            val header = readChunkHeader() ?: break
            val payload = ByteArray(header.messageLength)
            readChunkPayload(header.csid, payload, header.messageLength)

            if (header.messageTypeId == RtmpPacket.TYPE_COMMAND_AMF0) {
                val bais = ByteArrayInputStream(payload)
                val cmd = Amf0.readValue(bais) as? String
                if (cmd == "_result") {
                    Amf0.readValue(bais) // txId
                    Amf0.readValue(bais) // command object
                    val num = Amf0.readValue(bais) as? Number
                    streamId = num?.toInt() ?: 1
                    received = true
                }
            }
        }
    }

    private fun sendPublishCommand(streamKey: String) {
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "publish")
        Amf0.writeNumber(baos, 0.0) // transaction ID 0
        Amf0.writeNull(baos)
        Amf0.writeString(baos, streamKey)
        Amf0.writeString(baos, "live")

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            output!!,
            RtmpPacket.CSID_COMMAND,
            RtmpPacket.TYPE_COMMAND_AMF0,
            streamId,
            0,
            payload,
            chunkSize
        )
    }

    /**
     * Send "@setDataFrame" onMetaData AMF0 message.
     */
    @Synchronized
    fun sendMetadata(
        width: Int,
        height: Int,
        frameRate: Double,
        videoBitrateKbps: Int,
        sampleRate: Int = 44100,
        channels: Int = 2,
        audioBitrateKbps: Int = 128
    ) {
        val out = output ?: return
        val baos = ByteArrayOutputStream()
        Amf0.writeString(baos, "@setDataFrame")
        Amf0.writeString(baos, "onMetaData")

        val meta = mutableMapOf<String, Any?>()
        meta["duration"] = 0.0
        meta["width"] = width.toDouble()
        meta["height"] = height.toDouble()
        meta["videodatarate"] = videoBitrateKbps.toDouble()
        meta["framerate"] = frameRate
        meta["videocodecid"] = 7.0 // AVC
        meta["audiodatarate"] = audioBitrateKbps.toDouble()
        meta["audiosamplerate"] = sampleRate.toDouble()
        meta["audiosamplesize"] = 16.0
        meta["stereo"] = (channels == 2)
        meta["audiocodecid"] = 10.0 // AAC

        Amf0.writeEcmaArray(baos, meta)

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            out,
            RtmpPacket.CSID_DATA,
            RtmpPacket.TYPE_DATA_AMF0,
            streamId,
            0,
            payload,
            chunkSize
        )
        out.flush()
    }

    /**
     * Send AVC Video Sequence Header (AVCDecoderConfigurationRecord containing SPS & PPS).
     */
    @Synchronized
    fun sendVideoSequenceHeader(sps: ByteArray, pps: ByteArray) {
        val out = output ?: return
        val baos = ByteArrayOutputStream()

        // Video Tag Header:
        // FrameType = 1 (Keyframe), CodecID = 7 (AVC) -> 0x17
        baos.write(0x17)
        // AVCPacketType = 0 (AVC sequence header)
        baos.write(0x00)
        // CompositionTime = 0 (3 bytes)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)

        // AVCDecoderConfigurationRecord:
        baos.write(0x01) // configurationVersion = 1
        baos.write(sps[1].toInt()) // AVCProfileIndication
        baos.write(sps[2].toInt()) // profile_compatibility
        baos.write(sps[3].toInt()) // AVCLevelIndication
        baos.write(0xFF) // 6 bits reserved (111111b) + lengthSizeMinusOne (3 = 4 bytes)

        // SPS count (1)
        baos.write(0xE1) // 3 bits reserved (111b) + numOfSequenceParameterSets (1)
        baos.write((sps.size shr 8) and 0xFF)
        baos.write(sps.size and 0xFF)
        baos.write(sps)

        // PPS count (1)
        baos.write(0x01) // numOfPictureParameterSets (1)
        baos.write((pps.size shr 8) and 0xFF)
        baos.write(pps.size and 0xFF)
        baos.write(pps)

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            out,
            RtmpPacket.CSID_VIDEO,
            RtmpPacket.TYPE_VIDEO,
            streamId,
            0,
            payload,
            chunkSize
        )
        out.flush()
    }

    /**
     * Send Audio Sequence Header (AudioSpecificConfig).
     */
    @Synchronized
    fun sendAudioSequenceHeader(audioSpecificConfig: ByteArray) {
        val out = output ?: return
        val baos = ByteArrayOutputStream()

        // Audio Tag Header:
        // SoundFormat = 10 (AAC), SoundRate = 3 (44kHz), SoundSize = 1 (16 bit), SoundType = 1 (Stereo) -> 0xAF
        baos.write(0xAF)
        // AACPacketType = 0 (AAC sequence header)
        baos.write(0x00)
        // AudioSpecificConfig bytes
        baos.write(audioSpecificConfig)

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            out,
            RtmpPacket.CSID_AUDIO,
            RtmpPacket.TYPE_AUDIO,
            streamId,
            0,
            payload,
            chunkSize
        )
        out.flush()
    }

    /**
     * Send AVC Video NALU frame (Keyframe or Interframe).
     */
    @Synchronized
    fun sendVideoData(naluData: ByteArray, timestampMs: Long, isKeyframe: Boolean, compositionTimeOffsetMs: Int = 0) {
        val out = output ?: return
        val baos = ByteArrayOutputStream(naluData.size + 9)

        // FrameType = 1 (Keyframe) or 2 (Interframe), CodecID = 7 (AVC)
        val frameType = if (isKeyframe) 0x17 else 0x27
        baos.write(frameType)
        // AVCPacketType = 1 (AVC NALU)
        baos.write(0x01)
        // CompositionTime offset (3 bytes, signed/unsigned)
        baos.write((compositionTimeOffsetMs shr 16) and 0xFF)
        baos.write((compositionTimeOffsetMs shr 8) and 0xFF)
        baos.write(compositionTimeOffsetMs and 0xFF)

        // 4-byte length-prefixed NALU
        baos.write((naluData.size shr 24) and 0xFF)
        baos.write((naluData.size shr 16) and 0xFF)
        baos.write((naluData.size shr 8) and 0xFF)
        baos.write(naluData.size and 0xFF)
        baos.write(naluData)

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            out,
            RtmpPacket.CSID_VIDEO,
            RtmpPacket.TYPE_VIDEO,
            streamId,
            (timestampMs and 0x7FFFFFFF).toInt(),
            payload,
            chunkSize
        )
        out.flush()
    }

    /**
     * Send AAC Audio frame.
     */
    @Synchronized
    fun sendAudioData(aacData: ByteArray, timestampMs: Long) {
        val out = output ?: return
        val baos = ByteArrayOutputStream(aacData.size + 2)

        // SoundFormat = 10 (AAC), SoundRate = 3 (44kHz), SoundSize = 1, SoundType = 1 -> 0xAF
        baos.write(0xAF)
        // AACPacketType = 1 (AAC raw)
        baos.write(0x01)
        baos.write(aacData)

        val payload = baos.toByteArray()
        RtmpPacket.writeChunked(
            out,
            RtmpPacket.CSID_AUDIO,
            RtmpPacket.TYPE_AUDIO,
            streamId,
            (timestampMs and 0x7FFFFFFF).toInt(),
            payload,
            chunkSize
        )
        out.flush()
    }

    fun close() {
        isConnected = false
        isPublishing = false
        try {
            output?.flush()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        input = null
        output = null
    }

    // Helper to read incoming chunk header
    private fun readChunkHeader(): RtmpHeader? {
        val inp = input ?: return null
        val firstByte = inp.read()
        if (firstByte == -1) return null

        val fmt = (firstByte shr 6) and 0x03
        var csid = firstByte and 0x3F

        if (csid == 0) {
            val b = inp.read()
            if (b == -1) return null
            csid = b + 64
        } else if (csid == 1) {
            val b1 = inp.read()
            val b2 = inp.read()
            if (b1 == -1 || b2 == -1) return null
            csid = (b2 shl 8) + b1 + 64
        }

        var timestamp = 0
        var messageLength = 0
        var messageTypeId = 0
        var msgStreamId = 0

        if (fmt == 0) {
            timestamp = (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
            messageLength = (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
            messageTypeId = inp.read()
            msgStreamId = inp.read() or (inp.read() shl 8) or (inp.read() shl 16) or (inp.read() shl 24)
        } else if (fmt == 1) {
            timestamp = (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
            messageLength = (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
            messageTypeId = inp.read()
        } else if (fmt == 2) {
            timestamp = (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
        }

        return RtmpHeader(csid, fmt, timestamp, messageLength, messageTypeId, msgStreamId)
    }

    private fun readChunkPayload(csid: Int, destination: ByteArray, totalLen: Int) {
        val inp = input ?: return
        var offset = 0
        var isFirst = true
        while (offset < totalLen) {
            val chunkLen = minOf(128, totalLen - offset) // default incoming chunk size
            if (!isFirst) {
                // skip subsequent basic header
                inp.read()
            }
            isFirst = false
            readFully(inp, destination, offset, chunkLen)
            offset += chunkLen
        }
    }

    private fun readFully(inp: InputStream, target: ByteArray, offset: Int = 0, length: Int = target.size) {
        var bytesRead = 0
        while (bytesRead < length) {
            val count = inp.read(target, offset + bytesRead, length - bytesRead)
            if (count == -1) throw java.io.EOFException("Unexpected EOF while reading socket stream")
            bytesRead += count
        }
    }
}
