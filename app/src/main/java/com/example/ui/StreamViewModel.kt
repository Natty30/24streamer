package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.InterstitialAdManager
import com.example.ads.RewardedAdManager
import com.example.media.VideoInfo
import com.example.media.VideoMetadataUtil
import com.example.security.SecureCredentialStore
import com.example.service.ActiveStreamInfo
import com.example.service.StreamMetrics
import com.example.service.StreamStatus
import com.example.service.StreamingForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DestinationType(val displayName: String, val defaultUrl: String) {
    YOUTUBE("YouTube", "rtmp://a.rtmp.youtube.com/live2"),
    FACEBOOK("Facebook", "rtmps://live-api-s.facebook.com:443/rtmp/"),
    CUSTOM("Custom RTMP", "")
}

data class StreamUiState(
    val selectedVideo: VideoInfo? = null,
    val isLoadingVideo: Boolean = false,
    val videoError: String? = null,
    val destination: DestinationType = DestinationType.YOUTUBE,
    val serverUrl: String = DestinationType.YOUTUBE.defaultUrl,
    val streamKey: String = "",
    val isStreamKeyVisible: Boolean = false,
    val resolution: String = "1080p",
    val bitrateKbps: Int = 4500,
    val loopVideo: Boolean = true,
    val muteAudio: Boolean = false,
    val autoReconnect: Boolean = true,
    val rememberCredentials: Boolean = true,
    val streamStatus: StreamStatus = StreamStatus.Idle,
    val metrics: StreamMetrics = StreamMetrics(),
    val activeInfo: ActiveStreamInfo = ActiveStreamInfo(),
    val validationError: String? = null,
    val isBatteryOptimized: Boolean = false,
    val availableStreamingTimeSec: Long = 0L,
    val isAdLoading: Boolean = false,
    val isAdReady: Boolean = false,
    val adRewardMessage: String? = null,
    val adErrorMessage: String? = null
)

class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = SecureCredentialStore(application)
    val rewardedAdManager = RewardedAdManager(application)
    val interstitialAdManager = InterstitialAdManager(application)

    private val _uiState = MutableStateFlow(
        StreamUiState(
            destination = when (credentialStore.loadDestination()) {
                "facebook" -> DestinationType.FACEBOOK
                "custom" -> DestinationType.CUSTOM
                else -> DestinationType.YOUTUBE
            },
            serverUrl = credentialStore.loadServerUrl(),
            streamKey = credentialStore.loadStreamKey(),
            resolution = credentialStore.loadResolution(),
            bitrateKbps = credentialStore.loadBitrate(),
            loopVideo = credentialStore.loadLoopVideo(),
            muteAudio = credentialStore.loadMuteAudio(),
            autoReconnect = credentialStore.loadAutoReconnect(),
            rememberCredentials = credentialStore.getRememberCredentials(),
            availableStreamingTimeSec = credentialStore.loadStreamingTimeRemainingSeconds()
        )
    )

    val uiState: StateFlow<StreamUiState> = combine(
        _uiState,
        StreamingForegroundService.streamStatus,
        StreamingForegroundService.streamMetrics,
        StreamingForegroundService.activeInfo,
        StreamingForegroundService.streamingTimeRemainingSec
    ) { current, status, metrics, activeInfo, serviceRemainingSec ->
        val effectiveTimeSec = if (status is StreamStatus.Live) {
            serviceRemainingSec
        } else {
            credentialStore.loadStreamingTimeRemainingSeconds()
        }
        current.copy(
            streamStatus = status,
            metrics = metrics,
            activeInfo = activeInfo,
            availableStreamingTimeSec = effectiveTimeSec
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _uiState.value
    )

    init {
        checkBatteryOptimization()
        viewModelScope.launch {
            rewardedAdManager.isAdLoaded.collect { loaded ->
                _uiState.value = _uiState.value.copy(isAdReady = loaded)
            }
        }
        viewModelScope.launch {
            rewardedAdManager.isLoading.collect { loading ->
                _uiState.value = _uiState.value.copy(isAdLoading = loading)
            }
        }
    }

    fun checkBatteryOptimization() {
        try {
            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: true
            _uiState.value = _uiState.value.copy(isBatteryOptimized = !isIgnoring)
        } catch (_: Exception) {}
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVideo = true, videoError = null)
            try {
                val info = withContext(Dispatchers.IO) {
                    VideoMetadataUtil.extractInfo(getApplication(), uri)
                }

                if (!info.isH264) {
                    _uiState.value = _uiState.value.copy(
                        selectedVideo = info,
                        isLoadingVideo = false,
                        videoError = "Warning: Video codec is ${info.videoMime ?: "unknown"}. H.264 (AVC) is recommended for best RTMP compatibility."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        selectedVideo = info,
                        isLoadingVideo = false,
                        videoError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingVideo = false,
                    videoError = "Failed to parse video: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun onDestinationChanged(destination: DestinationType) {
        val newUrl = when (destination) {
            DestinationType.YOUTUBE -> DestinationType.YOUTUBE.defaultUrl
            DestinationType.FACEBOOK -> DestinationType.FACEBOOK.defaultUrl
            DestinationType.CUSTOM -> if (_uiState.value.serverUrl.startsWith("rtmp")) _uiState.value.serverUrl else ""
        }
        _uiState.value = _uiState.value.copy(
            destination = destination,
            serverUrl = newUrl
        )
        credentialStore.saveDestination(destination.name.lowercase())
        credentialStore.saveServerUrl(newUrl)
    }

    fun onServerUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
        credentialStore.saveServerUrl(url)
    }

    fun onStreamKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(streamKey = key)
        credentialStore.saveStreamKey(key)
    }

    fun toggleStreamKeyVisibility() {
        _uiState.value = _uiState.value.copy(isStreamKeyVisible = !_uiState.value.isStreamKeyVisible)
    }

    fun onResolutionChanged(resolution: String) {
        val defaultBitrate = when (resolution) {
            "720p" -> 2500
            "1080p" -> 4500
            else -> 6000
        }
        _uiState.value = _uiState.value.copy(
            resolution = resolution,
            bitrateKbps = defaultBitrate
        )
        credentialStore.saveResolution(resolution)
        credentialStore.saveBitrate(defaultBitrate)
    }

    fun onBitrateChanged(bitrateKbps: Int) {
        _uiState.value = _uiState.value.copy(bitrateKbps = bitrateKbps)
        credentialStore.saveBitrate(bitrateKbps)
    }

    fun onLoopToggled(loop: Boolean) {
        _uiState.value = _uiState.value.copy(loopVideo = loop)
        credentialStore.saveLoopVideo(loop)
    }

    fun onMuteToggled(mute: Boolean) {
        _uiState.value = _uiState.value.copy(muteAudio = mute)
        credentialStore.saveMuteAudio(mute)
    }

    fun onAutoReconnectToggled(auto: Boolean) {
        _uiState.value = _uiState.value.copy(autoReconnect = auto)
        credentialStore.saveAutoReconnect(auto)
    }

    fun onRememberCredentialsToggled(remember: Boolean) {
        _uiState.value = _uiState.value.copy(rememberCredentials = remember)
        credentialStore.saveRememberCredentials(remember)
    }

    fun clearValidationError() {
        _uiState.value = _uiState.value.copy(validationError = null)
    }

    fun clearAdMessages() {
        _uiState.value = _uiState.value.copy(adErrorMessage = null, adRewardMessage = null)
    }

    fun onConsentGathered() {
        rewardedAdManager.loadAd()
        interstitialAdManager.loadAd()
    }

    /**
     * Shows an interstitial ad ONLY at appropriate transitions (e.g. after stream ends).
     * Strictly verifies that livestream is NOT active before showing.
     */
    fun showInterstitialIfAppropriate(activity: Activity) {
        val isStreaming = uiState.value.streamStatus is StreamStatus.Live ||
                uiState.value.streamStatus is StreamStatus.Connecting ||
                uiState.value.streamStatus is StreamStatus.Reconnecting

        if (!isStreaming) {
            interstitialAdManager.showAdIfAvailable(
                activity = activity,
                isStreamActive = false
            )
        }
    }

    fun resetAllStoredData() {
        credentialStore.clearAllStoredData()
        _uiState.value = _uiState.value.copy(
            streamKey = "",
            serverUrl = DestinationType.YOUTUBE.defaultUrl,
            destination = DestinationType.YOUTUBE,
            availableStreamingTimeSec = 0L,
            validationError = null
        )
    }

    fun retryLoadAd() {
        rewardedAdManager.loadAd()
    }

    fun watchRewardedAd(activity: Activity) {
        _uiState.value = _uiState.value.copy(adErrorMessage = null, adRewardMessage = null)
        rewardedAdManager.showAd(
            activity = activity,
            onUserEarnedReward = { rewardMinutes ->
                val updatedSec = credentialStore.addStreamingTimeMinutes(rewardMinutes)
                _uiState.value = _uiState.value.copy(
                    availableStreamingTimeSec = updatedSec,
                    adRewardMessage = "+$rewardMinutes minutes streaming time added.",
                    adErrorMessage = null,
                    validationError = null
                )
                // If stream is active, notify background service
                val context = activity.applicationContext
                val addTimeIntent = Intent(context, StreamingForegroundService::class.java).apply {
                    action = StreamingForegroundService.ACTION_ADD_TIME
                    putExtra(StreamingForegroundService.EXTRA_ADD_MINUTES, rewardMinutes)
                }
                try {
                    context.startService(addTimeIntent)
                } catch (_: Exception) {}
            },
            onAdFailedOrCancelled = { reason ->
                _uiState.value = _uiState.value.copy(adErrorMessage = reason)
            }
        )
    }

    fun startStream(context: Context) {
        val current = _uiState.value

        if (current.availableStreamingTimeSec <= 0L) {
            _uiState.value = current.copy(validationError = "Streaming time has expired. Watch an ad to add +30 minutes.")
            return
        }

        if (current.selectedVideo == null) {
            _uiState.value = current.copy(validationError = "Please select a prerecorded video first.")
            return
        }

        if (current.serverUrl.isBlank()) {
            _uiState.value = current.copy(validationError = "Please enter an RTMP or RTMPS server URL.")
            return
        }

        if (current.streamKey.isBlank()) {
            _uiState.value = current.copy(validationError = "Please enter a stream key or token.")
            return
        }

        _uiState.value = current.copy(validationError = null)

        val intent = Intent(context, StreamingForegroundService::class.java).apply {
            action = StreamingForegroundService.ACTION_START
            putExtra(StreamingForegroundService.EXTRA_VIDEO_URI, current.selectedVideo.uri.toString())
            putExtra(StreamingForegroundService.EXTRA_VIDEO_NAME, current.selectedVideo.filename)
            putExtra(StreamingForegroundService.EXTRA_DESTINATION, current.destination.displayName)
            putExtra(StreamingForegroundService.EXTRA_SERVER_URL, current.serverUrl.trim())
            putExtra(StreamingForegroundService.EXTRA_STREAM_KEY, current.streamKey.trim())
            putExtra(StreamingForegroundService.EXTRA_RESOLUTION, current.resolution)
            putExtra(StreamingForegroundService.EXTRA_BITRATE, current.bitrateKbps)
            putExtra(StreamingForegroundService.EXTRA_LOOP, current.loopVideo)
            putExtra(StreamingForegroundService.EXTRA_MUTE, current.muteAudio)
            putExtra(StreamingForegroundService.EXTRA_AUTO_RECONNECT, current.autoReconnect)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopStream(context: Context) {
        val intent = Intent(context, StreamingForegroundService::class.java).apply {
            action = StreamingForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }
}
