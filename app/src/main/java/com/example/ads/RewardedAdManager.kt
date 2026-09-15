package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production-ready AdMob Rewarded Ad integration.
 * Manages SDK initialization, preloading, lifecycle callbacks,
 * and reliable reward granting strictly upon completion.
 */
class RewardedAdManager(private val context: Context) {

    private var rewardedAd: RewardedAd? = null

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _adStatusMessage = MutableStateFlow<String?>(null)
    val adStatusMessage: StateFlow<String?> = _adStatusMessage.asStateFlow()

    private val adUnitId: String = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID

    init {
        try {
            val reqConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(reqConfig)
            MobileAds.initialize(context) {
                loadAd()
            }
        } catch (e: Exception) {
            Log.w("RewardedAdManager", "Failed initializing MobileAds SDK", e)
        }
    }

    fun loadAd(onLoaded: (() -> Unit)? = null) {
        if (rewardedAd != null) {
            _isAdLoaded.value = true
            onLoaded?.invoke()
            return
        }
        if (_isLoading.value) return

        _isLoading.value = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _isAdLoaded.value = true
                    _isLoading.value = false
                    _adStatusMessage.value = "Rewarded ad ready (+30 min)"
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    _isAdLoaded.value = false
                    _isLoading.value = false
                    _adStatusMessage.value = adError.message
                }
            }
        )
    }

    fun showAd(
        activity: Activity,
        onUserEarnedReward: (rewardMinutes: Long) -> Unit,
        onAdFailedOrCancelled: (reason: String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            onAdFailedOrCancelled("Rewarded ad unavailable. Please try again.")
            loadAd()
            return
        }

        var rewardGranted = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // Ad displayed in full screen
            }

            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _isAdLoaded.value = false
                if (!rewardGranted) {
                    onAdFailedOrCancelled("Ad closed before reward requirements were met.")
                }
                // Preload the next rewarded ad immediately
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                _isAdLoaded.value = false
                onAdFailedOrCancelled(adError.message)
                loadAd()
            }
        }

        ad.show(activity) { _ ->
            // Official Google AdMob callback: Called strictly when user watches required ad duration
            rewardGranted = true
            onUserEarnedReward(30L)
        }
    }
}
