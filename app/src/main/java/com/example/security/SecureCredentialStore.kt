package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed/KeyStore encrypted credential storage for stream keys and settings.
 * Ensures stream keys are never stored in plaintext and never leaked to logs.
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("streamer_secure_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "StreamerMasterKey"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private const val KEY_DESTINATION = "destination"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_STREAM_KEY_ENC = "stream_key_enc"
        private const val KEY_STREAM_KEY_IV = "stream_key_iv"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_BITRATE = "bitrate_kbps"
        private const val KEY_LOOP = "loop_video"
        private const val KEY_MUTE = "mute_audio"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_REMEMBER = "remember_credentials"
        private const val KEY_STREAMING_TIME_REMAINING = "streaming_time_remaining_sec"
    }

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Fallback handled gracefully
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (_: Exception) {
            null
        }
    }

    private fun encrypt(plaintext: String): Pair<String, String>? {
        if (plaintext.isEmpty()) return Pair("", "")
        return try {
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val encStr = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
            Pair(encStr, ivStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(encryptedBase64: String, ivBase64: String): String {
        if (encryptedBase64.isEmpty() || ivBase64.isEmpty()) return ""
        return try {
            val secretKey = getSecretKey() ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipherText = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun saveStreamKey(key: String) {
        if (!getRememberCredentials()) return
        val encrypted = encrypt(key)
        if (encrypted != null) {
            prefs.edit()
                .putString(KEY_STREAM_KEY_ENC, encrypted.first)
                .putString(KEY_STREAM_KEY_IV, encrypted.second)
                .apply()
        }
    }

    fun loadStreamKey(): String {
        val enc = prefs.getString(KEY_STREAM_KEY_ENC, null) ?: return ""
        val iv = prefs.getString(KEY_STREAM_KEY_IV, null) ?: return ""
        return decrypt(enc, iv)
    }

    fun saveDestination(destination: String) {
        prefs.edit().putString(KEY_DESTINATION, destination).apply()
    }

    fun loadDestination(): String {
        return prefs.getString(KEY_DESTINATION, "youtube") ?: "youtube"
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun loadServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, "rtmp://a.rtmp.youtube.com/live2") ?: "rtmp://a.rtmp.youtube.com/live2"
    }

    fun saveResolution(res: String) {
        prefs.edit().putString(KEY_RESOLUTION, res).apply()
    }

    fun loadResolution(): String {
        return prefs.getString(KEY_RESOLUTION, "1080p") ?: "1080p"
    }

    fun saveBitrate(bitrateKbps: Int) {
        prefs.edit().putInt(KEY_BITRATE, bitrateKbps).apply()
    }

    fun loadBitrate(): Int {
        return prefs.getInt(KEY_BITRATE, 4500)
    }

    fun saveLoopVideo(loop: Boolean) {
        prefs.edit().putBoolean(KEY_LOOP, loop).apply()
    }

    fun loadLoopVideo(): Boolean {
        return prefs.getBoolean(KEY_LOOP, true)
    }

    fun saveMuteAudio(mute: Boolean) {
        prefs.edit().putBoolean(KEY_MUTE, mute).apply()
    }

    fun loadMuteAudio(): Boolean {
        return prefs.getBoolean(KEY_MUTE, false)
    }

    fun saveAutoReconnect(auto: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, auto).apply()
    }

    fun loadAutoReconnect(): Boolean {
        return prefs.getBoolean(KEY_AUTO_RECONNECT, true)
    }

    fun saveRememberCredentials(remember: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER, remember).apply()
        if (!remember) {
            prefs.edit().remove(KEY_STREAM_KEY_ENC).remove(KEY_STREAM_KEY_IV).apply()
        }
    }

    fun getRememberCredentials(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER, true)
    }

    @Synchronized
    fun saveStreamingTimeRemainingSeconds(seconds: Long) {
        val safeSec = maxOf(0L, seconds)
        prefs.edit().putLong(KEY_STREAMING_TIME_REMAINING, safeSec).apply()
    }

    @Synchronized
    fun loadStreamingTimeRemainingSeconds(): Long {
        return prefs.getLong(KEY_STREAMING_TIME_REMAINING, 0L)
    }

    @Synchronized
    fun addStreamingTimeMinutes(minutes: Long): Long {
        val current = loadStreamingTimeRemainingSeconds()
        val updated = current + (minutes * 60L)
        saveStreamingTimeRemainingSeconds(updated)
        return updated
    }

    @Synchronized
    fun consumeStreamingTimeSeconds(seconds: Long): Long {
        val current = loadStreamingTimeRemainingSeconds()
        val updated = maxOf(0L, current - seconds)
        saveStreamingTimeRemainingSeconds(updated)
        return updated
    }
}
