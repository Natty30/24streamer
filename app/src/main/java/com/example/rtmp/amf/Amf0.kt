package com.example.rtmp.amf

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * AMF0 (Action Message Format 0) serialization and deserialization
 * for RTMP command messages and metadata.
 */
object Amf0 {
    const val TYPE_NUMBER: Byte = 0x00
    const val TYPE_BOOLEAN: Byte = 0x01
    const val TYPE_STRING: Byte = 0x02
    const val TYPE_OBJECT: Byte = 0x03
    const val TYPE_NULL: Byte = 0x05
    const val TYPE_UNDEFINED: Byte = 0x06
    const val TYPE_ECMA_ARRAY: Byte = 0x08
    const val TYPE_OBJECT_END: Byte = 0x09
    const val TYPE_STRICT_ARRAY: Byte = 0x0A

    fun writeString(out: OutputStream, value: String) {
        out.write(TYPE_STRING.toInt())
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write((bytes.size shr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    fun writeNumber(out: OutputStream, value: Double) {
        out.write(TYPE_NUMBER.toInt())
        val dos = DataOutputStream(out)
        dos.writeDouble(value)
    }

    fun writeBoolean(out: OutputStream, value: Boolean) {
        out.write(TYPE_BOOLEAN.toInt())
        out.write(if (value) 1 else 0)
    }

    fun writeNull(out: OutputStream) {
        out.write(TYPE_NULL.toInt())
    }

    fun writeObject(out: OutputStream, properties: Map<String, Any?>) {
        out.write(TYPE_OBJECT.toInt())
        for ((key, value) in properties) {
            writeProperty(out, key, value)
        }
        // Object end: 0x00, 0x00, 0x09
        out.write(0)
        out.write(0)
        out.write(TYPE_OBJECT_END.toInt())
    }

    fun writeEcmaArray(out: OutputStream, properties: Map<String, Any?>) {
        out.write(TYPE_ECMA_ARRAY.toInt())
        val count = properties.size
        out.write((count shr 24) and 0xFF)
        out.write((count shr 16) and 0xFF)
        out.write((count shr 8) and 0xFF)
        out.write(count and 0xFF)
        for ((key, value) in properties) {
            writeProperty(out, key, value)
        }
        out.write(0)
        out.write(0)
        out.write(TYPE_OBJECT_END.toInt())
    }

    private fun writeProperty(out: OutputStream, key: String, value: Any?) {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        out.write((keyBytes.size shr 8) and 0xFF)
        out.write(keyBytes.size and 0xFF)
        out.write(keyBytes)
        writeValue(out, value)
    }

    fun writeValue(out: OutputStream, value: Any?) {
        when (value) {
            null -> writeNull(out)
            is String -> writeString(out, value)
            is Number -> writeNumber(out, value.toDouble())
            is Boolean -> writeBoolean(out, value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(out, value as Map<String, Any?>)
            }
            else -> writeString(out, value.toString())
        }
    }

    fun readValue(input: InputStream): Any? {
        val marker = input.read()
        if (marker == -1) return null
        return when (marker.toByte()) {
            TYPE_NUMBER -> DataInputStream(input).readDouble()
            TYPE_BOOLEAN -> input.read() != 0
            TYPE_STRING -> {
                val len = (input.read() shl 8) or input.read()
                val bytes = ByteArray(len)
                var read = 0
                while (read < len) {
                    val r = input.read(bytes, read, len - read)
                    if (r == -1) break
                    read += r
                }
                String(bytes, Charsets.UTF_8)
            }
            TYPE_OBJECT -> readObjectBody(input)
            TYPE_ECMA_ARRAY -> {
                input.skip(4) // skip count
                readObjectBody(input)
            }
            TYPE_NULL -> null
            TYPE_UNDEFINED -> null
            else -> null
        }
    }

    private fun readObjectBody(input: InputStream): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        while (true) {
            val keyLen = (input.read() shl 8) or input.read()
            if (keyLen <= 0) {
                val endMarker = input.read()
                if (endMarker == TYPE_OBJECT_END.toInt() || endMarker == -1) break
            }
            val keyBytes = ByteArray(keyLen)
            var read = 0
            while (read < keyLen) {
                val r = input.read(keyBytes, read, keyLen - read)
                if (r == -1) break
                read += r
            }
            val key = String(keyBytes, Charsets.UTF_8)
            val value = readValue(input)
            map[key] = value
        }
        return map
    }
}
