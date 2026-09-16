package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production-ready AdMob Interstitial Ad Manager.
 *
 * Rules:
 * 1. Preloads interstitial ads in the background.
 * 2. NEVER shows an interstitial during an active livestream.
 * 3. Only shows at natural transitions (e.g., after stream ends or transitions to idle).
 * 4. Includes cooldown to avoid spamming the user.
 * 5. Handles all load and presentation errors gracefully without crashing.
 */
class InterstitialAdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var lastShownTimeMs = 0L

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private val adUnitId: String = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID

    init {
        loadAd()
    }

    fun loadAd() {
        if (interstitialAd != null || isLoading) return
        isLoading = true

        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                context,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoading = false
                        _isAdLoaded.value = true
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isLoading = false
                        _isAdLoaded.value = false
                        Log.d("InterstitialAdManager", "Failed to load interstitial: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isLoading = false
            Log.w("InterstitialAdManager", "Exception loading interstitial ad", e)
        }
    }

    /**
     * Shows the interstitial ad ONLY if:
     * - An ad is currently loaded
     * - The stream is NOT currently active / live
     * - Cooldown period (at least 60 seconds since last shown) has passed
     */
    fun showAdIfAvailable(
        activity: Activity,
        isStreamActive: Boolean,
        onAdDismissed: () -> Unit = {}
    ) {
        if (isStreamActive) {
            Log.d("InterstitialAdManager", "Skipping interstitial: Livestream is currently active")
            onAdDismissed()
            return
        }

        val now = System.currentTimeMillis()
        // Minimum 60-second cooldown between interstitials to respect AdMob policies & user experience
        if (now - lastShownTimeMs < 60_000L) {
            Log.d("InterstitialAdManager", "Skipping interstitial: Cooldown period active")
            onAdDismissed()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            loadAd()
            onAdDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                _isAdLoaded.value = false
                lastShownTimeMs = System.currentTimeMillis()
                loadAd()
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                _isAdLoaded.value = false
                Log.d("InterstitialAdManager", "Failed to show interstitial: ${adError.message}")
                loadAd()
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                lastShownTimeMs = System.currentTimeMillis()
            }
        }

        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.w("InterstitialAdManager", "Exception presenting interstitial ad", e)
            interstitialAd = null
            _isAdLoaded.value = false
            loadAd()
            onAdDismissed()
        }
    }
}
