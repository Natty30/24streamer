package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import com.example.privacy.ConsentManager
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ads.BannerAdView
import com.example.media.VideoMetadataUtil
import com.example.service.StreamStatus
import com.example.ui.theme.StreamAmber
import com.example.ui.theme.StreamDarkBackground
import com.example.ui.theme.StreamDarkCardBorder
import com.example.ui.theme.StreamDarkSurface
import com.example.ui.theme.StreamDarkSurfaceVariant
import com.example.ui.theme.StreamElectricBlue
import com.example.ui.theme.StreamLiveGreen
import com.example.ui.theme.StreamLiveRed
import com.example.ui.theme.StreamTextMuted
import com.example.ui.theme.StreamTextPrimary
import com.example.ui.theme.StreamTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    viewModel: StreamViewModel,
    consentManager: ConsentManager,
    modifier: Modifier = Modifier,
    canRequestAds: Boolean = true
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val uiState by viewModel.uiState.collectAsState()
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.onVideoSelected(it)
        }
    }

    val isStreaming = uiState.streamStatus is StreamStatus.Live ||
            uiState.streamStatus is StreamStatus.Connecting ||
            uiState.streamStatus is StreamStatus.Reconnecting

    val previousStreamStatus = remember { mutableStateOf(uiState.streamStatus) }
    LaunchedEffect(uiState.streamStatus) {
        val wasStreaming = previousStreamStatus.value is StreamStatus.Live ||
                previousStreamStatus.value is StreamStatus.Connecting ||
                previousStreamStatus.value is StreamStatus.Reconnecting
        val isNowStopped = uiState.streamStatus is StreamStatus.Idle ||
                uiState.streamStatus is StreamStatus.Stopped ||
                uiState.streamStatus is StreamStatus.Error

        if (wasStreaming && isNowStopped) {
            // Natural transition: active livestream has ended, safe to present interstitial
            activity?.let { act ->
                viewModel.showInterstitialIfAppropriate(act)
            }
        }
        previousStreamStatus.value = uiState.streamStatus
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = StreamDarkBackground,
        bottomBar = {
            Surface(
                color = StreamDarkSurface,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, StreamDarkCardBorder)
            ) {
                BannerAdView(
                    modifier = Modifier.padding(vertical = 4.dp),
                    canRequestAds = canRequestAds
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StreamDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Stream Icon",
                                tint = if (isStreaming) StreamLiveRed else StreamElectricBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "24/7 Streamer",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = StreamTextPrimary
                                )
                            )
                            Text(
                                text = "Prerecorded Continuous Broadcast",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = StreamTextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier.testTag("privacy_policy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Policy,
                            contentDescription = "Settings and Privacy Policy",
                            tint = StreamTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StreamDarkSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Status & Metrics Card
            LiveStatusCard(uiState = uiState)

            // Validation Error Banner
            if (uiState.validationError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StreamLiveRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = StreamLiveRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.validationError ?: "",
                            color = StreamTextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearValidationError() }) {
                            Text("✕", color = StreamTextSecondary)
                        }
                    }
                }
            }

            // 2. Video Selection Section
            VideoSelectionCard(
                uiState = uiState,
                onSelectClick = {
                    filePickerLauncher.launch(arrayOf("video/*"))
                }
            )

            // Copyright Compliance Notice
            Card(
                colors = CardDefaults.cardColors(containerColor = StreamDarkSurfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = StreamTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Users are responsible for ensuring that they have the necessary rights and permissions to broadcast content streamed using this application.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = StreamTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    )
                }
            }

            // 3. Streaming Time & Rewarded Ad Wallet Card
            StreamingTimeCard(
                uiState = uiState,
                onWatchAdClick = {
                    activity?.let { viewModel.watchRewardedAd(it) }
                },
                onClearAdMessages = {
                    viewModel.clearAdMessages()
                }
            )

            // 4. Destination Configuration Card
            DestinationCard(
                uiState = uiState,
                onDestinationChange = { viewModel.onDestinationChanged(it) },
                onServerUrlChange = { viewModel.onServerUrlChanged(it) },
                onStreamKeyChange = { viewModel.onStreamKeyChanged(it) },
                onToggleVisibility = { viewModel.toggleStreamKeyVisibility() }
            )

            // 5. Video & Stream Quality Options Card
            StreamOptionsCard(
                uiState = uiState,
                onResolutionChange = { viewModel.onResolutionChanged(it) },
                onBitrateChange = { viewModel.onBitrateChanged(it) },
                onLoopToggle = { viewModel.onLoopToggled(it) },
                onMuteToggle = { viewModel.onMuteToggled(it) },
                onAutoReconnectToggle = { viewModel.onAutoReconnectToggled(it) },
                onRememberToggle = { viewModel.onRememberCredentialsToggled(it) }
            )

            // 6. Large Action Controls (Start / Stop)
            StreamControlButtons(
                isStreaming = isStreaming,
                canStartStream = uiState.availableStreamingTimeSec > 0,
                onStart = { viewModel.startStream(context) },
                onStop = { viewModel.stopStream(context) }
            )

            TextButton(
                onClick = { showPrivacyDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("footer_privacy_policy_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Policy,
                    contentDescription = null,
                    tint = StreamElectricBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Settings → Privacy Policy & Compliance",
                    color = StreamElectricBlue,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            activity = activity,
            consentManager = consentManager,
            onDismiss = { showPrivacyDialog = false },
            onResetData = { viewModel.resetAllStoredData() }
        )
    }
}

@Composable
fun LiveStatusCard(uiState: StreamUiState) {
    val status = uiState.streamStatus
    val metrics = uiState.metrics

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StreamDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, StreamDarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (status) {
                        is StreamStatus.Live -> {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(StreamLiveRed.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🔴 LIVE",
                                fontWeight = FontWeight.Black,
                                color = StreamLiveRed,
                                fontSize = 16.sp
                            )
                        }
                        is StreamStatus.Connecting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = StreamAmber,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONNECTING...",
                                fontWeight = FontWeight.Bold,
                                color = StreamAmber,
                                fontSize = 14.sp
                            )
                        }
                        is StreamStatus.Reconnecting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = StreamAmber,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚠️ RECONNECTING...",
                                fontWeight = FontWeight.Bold,
                                color = StreamAmber,
                                fontSize = 14.sp
                            )
                        }
                        is StreamStatus.Expired -> {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(StreamAmber)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STREAMING TIME EXPIRED",
                                fontWeight = FontWeight.Bold,
                                color = StreamAmber,
                                fontSize = 14.sp
                            )
                        }
                        is StreamStatus.Error -> {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(StreamLiveRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ERROR",
                                fontWeight = FontWeight.Bold,
                                color = StreamLiveRed,
                                fontSize = 14.sp
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(StreamTextMuted)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "IDLE (READY)",
                                fontWeight = FontWeight.Bold,
                                color = StreamTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Streaming timer
                if (status is StreamStatus.Live) {
                    Text(
                        text = VideoMetadataUtil.formatDuration(status.durationMs),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = StreamTextPrimary,
                        fontSize = 20.sp
                    )
                }
            }

            if (status is StreamStatus.Live) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = StreamDarkCardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem(label = "Destination", value = uiState.activeInfo.destinationName)
                    MetricItem(label = "Resolution", value = uiState.activeInfo.resolution)
                    MetricItem(label = "Bitrate", value = "${metrics.bitrateKbps} kbps")
                    MetricItem(label = "Loops", value = "#${metrics.loopsCompleted}")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem(label = "Speed / FPS", value = String.format("%.1f fps", metrics.fps))
                    val mbSent = metrics.totalBytesSent / (1024.0 * 1024.0)
                    MetricItem(label = "Data Transmitted", value = String.format("%.1f MB", mbSent))
                }
            } else if (status is StreamStatus.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status.message,
                    color = StreamLiveRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, color = StreamTextSecondary, fontSize = 11.sp)
        Text(text = value, color = StreamTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun VideoSelectionCard(
    uiState: StreamUiState,
    onSelectClick: () -> Unit
) {
    val context = LocalContext.current
    val video = uiState.selectedVideo

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StreamDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, StreamDarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "VIDEO SOURCE",
                color = StreamTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoadingVideo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = StreamElectricBlue)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Reading video metadata...", color = StreamTextSecondary)
                }
            } else if (video == null) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StreamDarkCardBorder, RoundedCornerShape(10.dp))
                        .background(StreamDarkSurfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSelectClick() }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = StreamElectricBlue,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SELECT PRERECORDED VIDEO",
                            color = StreamElectricBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "MP4 with H.264 / AAC for 24/7 looping",
                            color = StreamTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // Video selected view
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(StreamDarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = StreamLiveGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.filename,
                                color = StreamTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "${video.width}x${video.height} • ${VideoMetadataUtil.formatDuration(video.durationMs)} • ${video.sizeBytes / (1024 * 1024)} MB",
                                color = StreamTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = onSelectClick,
                            modifier = Modifier.testTag("change_video_button")
                        ) {
                            Text("CHANGE", color = StreamElectricBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (uiState.videoError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.videoError,
                            color = StreamAmber,
                            fontSize = 12.sp
                        )
                    }

                    // Mini ExoPlayer Preview
                    Spacer(modifier = Modifier.height(12.dp))
                    VideoMiniPlayer(uri = video.uri)
                }
            }
        }
    }
}

@Composable
fun VideoMiniPlayer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun DestinationCard(
    uiState: StreamUiState,
    onDestinationChange: (DestinationType) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onStreamKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StreamDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, StreamDarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "STREAM DESTINATION",
                color = StreamTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Destination selector tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StreamDarkSurfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DestinationType.values().forEach { dest ->
                    val selected = dest == uiState.destination
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) StreamElectricBlue else Color.Transparent)
                            .clickable { onDestinationChange(dest) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dest.displayName,
                            color = if (selected) Color.Black else StreamTextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Server URL field
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("Stream Server URL (RTMP / RTMPS)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StreamElectricBlue,
                    unfocusedBorderColor = StreamDarkCardBorder,
                    focusedTextColor = StreamTextPrimary,
                    unfocusedTextColor = StreamTextPrimary,
                    focusedLabelColor = StreamElectricBlue,
                    unfocusedLabelColor = StreamTextSecondary
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Stream Key field with Show/Hide toggle
            OutlinedTextField(
                value = uiState.streamKey,
                onValueChange = onStreamKeyChange,
                label = { Text("Stream Key / Token") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stream_key_input"),
                visualTransformation = if (uiState.isStreamKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (uiState.isStreamKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (uiState.isStreamKeyVisible) "Hide Stream Key" else "Show Stream Key",
                            tint = StreamTextSecondary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StreamElectricBlue,
                    unfocusedBorderColor = StreamDarkCardBorder,
                    focusedTextColor = StreamTextPrimary,
                    unfocusedTextColor = StreamTextPrimary,
                    focusedLabelColor = StreamElectricBlue,
                    unfocusedLabelColor = StreamTextSecondary
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "🔒 Credentials are encrypted in hardware KeyStore and never logged.",
                color = StreamTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun StreamOptionsCard(
    uiState: StreamUiState,
    onResolutionChange: (String) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onLoopToggle: (Boolean) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onAutoReconnectToggle: (Boolean) -> Unit,
    onRememberToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StreamDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, StreamDarkCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "STREAM QUALITY & OPTIONS",
                color = StreamTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Resolution Presets
            Text(text = "Target Resolution & Bitrate Preset", color = StreamTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "720p" to "2500 kbps",
                    "1080p" to "4500 kbps",
                    "Source" to "6000 kbps"
                ).forEach { (res, rate) ->
                    val isSelected = uiState.resolution.equals(res, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (isSelected) StreamElectricBlue else StreamDarkCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .background(if (isSelected) StreamElectricBlue.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onResolutionChange(res) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = res,
                                color = if (isSelected) StreamElectricBlue else StreamTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = rate,
                                color = StreamTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = StreamDarkCardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Switches
            OptionSwitchRow(
                title = "Loop Video Continuously",
                subtitle = "Restarts video at end of file without dropping stream (24/7)",
                checked = uiState.loopVideo,
                onCheckedChange = onLoopToggle
            )

            Spacer(modifier = Modifier.height(10.dp))

            OptionSwitchRow(
                title = "Mute Audio",
                subtitle = "Suppress audio track from the broadcast",
                checked = uiState.muteAudio,
                onCheckedChange = onMuteToggle
            )

            Spacer(modifier = Modifier.height(10.dp))

            OptionSwitchRow(
                title = "Automatic Reconnect",
                subtitle = "Auto retry with exponential backoff if network drops",
                checked = uiState.autoReconnect,
                onCheckedChange = onAutoReconnectToggle
            )

            Spacer(modifier = Modifier.height(10.dp))

            OptionSwitchRow(
                title = "Save Credentials Securely",
                subtitle = "Store encrypted stream key in local KeyStore",
                checked = uiState.rememberCredentials,
                onCheckedChange = onRememberToggle
            )
        }
    }
}

@Composable
fun OptionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = StreamTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = StreamTextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = StreamLiveGreen,
                uncheckedThumbColor = StreamTextSecondary,
                uncheckedTrackColor = StreamDarkSurfaceVariant
            )
        )
    }
}

@Composable
fun StreamControlButtons(
    isStreaming: Boolean,
    canStartStream: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    if (!isStreaming) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStart,
                enabled = canStartStream,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_stream_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StreamLiveGreen,
                    disabledContainerColor = StreamDarkSurfaceVariant
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (canStartStream) Color.Black else StreamTextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "START STREAM",
                        color = if (canStartStream) Color.Black else StreamTextMuted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
            if (!canStartStream) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Streaming time has expired. Watch a rewarded ad to earn +30 minutes.",
                    color = StreamAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    } else {
        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("stop_stream_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StreamLiveRed
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "STOP STREAM",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun StreamingTimeCard(
    uiState: StreamUiState,
    onWatchAdClick: () -> Unit,
    onClearAdMessages: () -> Unit
) {
    val totalSec = uiState.availableStreamingTimeSec
    val isLive = uiState.streamStatus is StreamStatus.Live
    val isExpired = totalSec <= 0L

    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    val formattedTime = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("streaming_time_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StreamDarkSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isExpired) StreamAmber.copy(alpha = 0.5f) else StreamElectricBlue.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = if (isLive) StreamLiveGreen else if (isExpired) StreamAmber else StreamElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLive) "STREAMING TIME REMAINING" else if (isExpired) "STREAMING TIME EXPIRED" else "STREAMING TIME",
                        color = if (isExpired) StreamAmber else StreamTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (isLive) {
                    Text(
                        text = "LIVE COUNTDOWN",
                        color = StreamLiveGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (isLive) "Remaining:" else "Available:",
                        color = StreamTextMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formattedTime,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        color = if (isLive) StreamLiveGreen else if (isExpired) StreamAmber else StreamElectricBlue
                    )
                }

                if (isExpired) {
                    Text(
                        text = "Watch Ad to Stream",
                        color = StreamAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (!isLive) {
                    Text(
                        text = "Ready to stream",
                        color = StreamLiveGreen,
                        fontSize = 12.sp
                    )
                }
            }

            if (uiState.adRewardMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = StreamLiveGreen.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StreamLiveGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.adRewardMessage,
                            color = StreamLiveGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearAdMessages, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = StreamTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (uiState.adErrorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = StreamAmber.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = StreamAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.adErrorMessage,
                            color = StreamAmber,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearAdMessages, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = StreamTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onWatchAdClick,
                enabled = !uiState.isAdLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("watch_ad_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StreamElectricBlue
                )
            ) {
                if (uiState.isAdLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LOADING ADMOB AD...",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WATCH AD  •  +30 MINUTES",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
