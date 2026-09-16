package com.example.ads

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Production-ready AdMob Banner Ad Composable.
 * Handles lifecycle, loading errors, and collapses gracefully if ads are unavailable.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAdLoaded by remember { mutableStateOf(false) }

    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    isAdLoaded = true
                    Log.d("BannerAdView", "Banner ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isAdLoaded = false
                    Log.d("BannerAdView", "Banner ad failed to load: ${error.message} (code: ${error.code})")
                }
            }
            try {
                loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                Log.w("BannerAdView", "Exception requesting banner ad", e)
            }
        }
    }

    DisposableEffect(adView) {
        onDispose {
            try {
                adView.destroy()
            } catch (e: Exception) {
                Log.w("BannerAdView", "Exception destroying AdView", e)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_banner_container"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier.testTag("admob_banner_view")
        )
    }
}
