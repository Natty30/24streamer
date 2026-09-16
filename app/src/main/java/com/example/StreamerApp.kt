package com.example

import android.app.Application
import java.io.File

class StreamerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            // Pre-create WebView HTTP Code Cache directories to eliminate Chromium opendir / index reconstruction errors
            File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
            File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
        } catch (_: Exception) {
        }
    }
}
