package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.privacy.ConsentManager
import com.example.ui.theme.StreamAmber
import com.example.ui.theme.StreamDarkCardBorder
import com.example.ui.theme.StreamDarkSurface
import com.example.ui.theme.StreamDarkSurfaceVariant
import com.example.ui.theme.StreamElectricBlue
import com.example.ui.theme.StreamLiveGreen
import com.example.ui.theme.StreamLiveRed
import com.example.ui.theme.StreamTextMuted
import com.example.ui.theme.StreamTextPrimary
import com.example.ui.theme.StreamTextSecondary

@Composable
fun PrivacyPolicyDialog(
    activity: Activity?,
    consentManager: ConsentManager,
    onDismiss: () -> Unit,
    onResetData: () -> Unit
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp)
                .testTag("privacy_policy_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = StreamDarkSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(StreamDarkSurfaceVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = StreamElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Settings & Privacy Policy",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = StreamTextPrimary
                                )
                            )
                            Text(
                                text = "Google Play Policy Compliance",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = StreamTextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_privacy_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = StreamTextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = StreamDarkCardBorder
                )

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Public Web Link Button
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://natty30.github.io/24streamer/privacy-policy.html"))
                                activity?.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().testTag("open_web_privacy_policy_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StreamElectricBlue)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Public Web Privacy Policy")
                    }

                    // Copyright Warning Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StreamAmber.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StreamAmber.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StreamAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Copyright & Broadcasting Notice",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = StreamAmber
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Users are solely responsible for ensuring they possess all necessary rights, licenses, and permissions for any video or audio broadcasted through this app. The app does not provide, host, or bundle third-party copyrighted media.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = StreamTextPrimary,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }
                    }

                    // Section 1: Overview & Developer Identity
                    PolicySection(
                        title = "1. Overview & Developer Identity",
                        icon = Icons.Default.Info
                    ) {
                        Text(
                            text = "• Application: 24/7 Streamer (Continuous RTMP/RTMPS Broadcast)\n" +
                                    "• Developer: 24/7 Streamer Team\n" +
                                    "• Privacy Contact: appmalume@gmail.com\n" +
                                    "• Purpose: Enables broadcasters to stream pre-recorded video continuously to RTMP/RTMPS ingestion servers (such as YouTube Live, Facebook Live, Twitch, or custom RTMP endpoints).\n" +
                                    "• No User Accounts: The application does NOT create, manage, or require user accounts. No registration or sign-in is required.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                    }

                    // Section 2: Advertising & Google AdMob
                    PolicySection(
                        title = "2. Google Mobile Ads (AdMob) & Advertising",
                        icon = Icons.Default.Policy
                    ) {
                        Text(
                            text = "• Rewarded Ads: Users may voluntarily choose to watch a rewarded video advertisement to receive +30 minutes of streaming time allowance. Rewards are credited solely upon verified completion callback from the Google Mobile Ads SDK. Ads are never auto-triggered without user action.\n\n" +
                                    "• Banner & Interstitial Ads: The app may display non-intrusive banner advertisements at the bottom and interstitial ads at natural transitions (e.g. after a stream finishes). Interstitials are NEVER displayed while a livestream is running.\n\n" +
                                    "• Data Processed by AdMob: The Google Mobile Ads SDK may process device identifiers (such as Google Advertising ID), IP address (for coarse geographic location and fraud prevention), and advertising diagnostic telemetry. All ad serving respects Google Play Families and Advertising policies.\n\n" +
                                    "• No Artificial Clicks: The app never encourages artificial ad clicks or simulates ad interactions.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                    }

                    // Section 3: Privacy Choices & Consent Management
                    PolicySection(
                        title = "3. User Consent & Privacy Rights (GDPR / CCPA)",
                        icon = Icons.Default.CheckCircle
                    ) {
                        Text(
                            text = "• Google User Messaging Platform (UMP) SDK is integrated into the app to present privacy and consent choices to users in applicable regions (including the EEA, UK, and US states).\n\n" +
                                    "• You may inspect or update your advertising and privacy consent choices at any time below:",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (activity != null) {
                                    consentManager.showPrivacyOptionsForm(activity) {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("manage_consent_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StreamElectricBlue
                            )
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manage Privacy & Ad Consent Preferences")
                        }
                    }

                    // Section 4: Video Selection, Storage & Hardware Permissions
                    PolicySection(
                        title = "4. Device Hardware, Storage & Permissions",
                        icon = Icons.Default.Lock
                    ) {
                        Text(
                            text = "• Camera & Microphone: The application does NOT request, access, or utilize camera or microphone permissions or hardware.\n\n" +
                                    "• Video & Storage Selection: The app utilizes Android's zero-permission system document picker (OpenDocument) to let users pick local media files. The app does NOT request broad external storage permissions (READ_EXTERNAL_STORAGE / READ_MEDIA_VIDEO).\n\n" +
                                    "• Notifications: The POST_NOTIFICATIONS permission is used strictly to display an active Foreground Service status notification with remaining streaming time and an instant 'STOP STREAM' control.\n\n" +
                                    "• Network & Wake Lock: INTERNET and ACCESS_NETWORK_STATE are used to transmit video packets to the RTMP endpoint and check connectivity. WAKE_LOCK prevents the CPU from sleeping while an active broadcast is in progress.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                    }

                    // Section 5: Streaming Functionality & Direct Ingest
                    PolicySection(
                        title = "5. Streaming Functionality & Foreground Service",
                        icon = Icons.Default.Policy
                    ) {
                        Text(
                            text = "• Direct Ingest: Video decoding and RTMP/RTMPS packetization occur strictly locally on your Android device. Video and audio packets are transmitted directly from your device to the RTMP/RTMPS server you specify (e.g., YouTube Live, Facebook Live, Twitch, or your custom server).\n\n" +
                                    "• No Intermediary Servers: The application developer operates no proxy servers, intermediate relay services, or cloud recording databases. Your live stream content is never intercepted, stored, or viewed by the developer.\n\n" +
                                    "• Foreground Service: In accordance with Android 14+ requirements, the streaming process operates via a user-initiated Foreground Service (FOREGROUND_SERVICE_DATA_SYNC) with a persistent notification containing live time remaining and a direct 'STOP STREAM' control.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                    }

                    // Section 6: Stream Key & Credential Security
                    PolicySection(
                        title = "6. Stream Keys & On-Device Security",
                        icon = Icons.Default.Security
                    ) {
                        Text(
                            text = "• Hardware Encryption: Stream keys and server URLs are saved exclusively on your device using Android KeyStore-backed AES-256 GCM encryption.\n\n" +
                                    "• No Leakage: Stream keys are never written to logcat, never transmitted to analytics providers, and never uploaded to any remote server other than the designated RTMP socket.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                    }

                    // Section 7: Data Retention, Deletion & User Controls
                    PolicySection(
                        title = "7. Data Retention & Deletion",
                        icon = Icons.Default.DeleteOutline
                    ) {
                        Text(
                            text = "• All data (video URI references, stream keys, and streaming time balances) is stored purely locally on your device.\n\n" +
                                    "• Because no cloud accounts or server databases exist, you can delete all stored data instantly using the button below or by clearing app data in Android System Settings.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier.fillMaxWidth().testTag("reset_credentials_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StreamLiveRed.copy(alpha = 0.2f),
                                contentColor = StreamLiveRed
                            )
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset All Saved Credentials & Data")
                        }
                    }

                    // Section 8: Children's Privacy
                    PolicySection(
                        title = "8. Children's Privacy",
                        icon = Icons.Default.Policy
                    ) {
                        Text(
                            text = "24/7 Streamer is designed for general audiences and content broadcasters. It is not intended for or directed to children under 13 years of age (or under 16 in the European Economic Area). We do not knowingly collect personal information from children.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                    }

                    // Section 9: Policy Changes & Contact
                    PolicySection(
                        title = "9. Policy Updates & Contact",
                        icon = Icons.Default.Info
                    ) {
                        Text(
                            text = "We may update this Privacy Policy periodically. Any revisions will be reflected inside the application and at the public policy URL below.\n\n" +
                                    "• Developer: 24/7 Streamer Team\n" +
                                    "• Email: appmalume@gmail.com\n" +
                                    "• Public URL: https://natty30.github.io/24streamer/privacy-policy.html\n" +
                                    "• Effective Date: September 2026",
                            style = MaterialTheme.typography.bodySmall.copy(color = StreamTextSecondary, lineHeight = 16.sp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = StreamDarkCardBorder
                )

                // Footer button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StreamElectricBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text("Close & Return")
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Stored Data?", color = StreamTextPrimary) },
            text = {
                Text(
                    "This will erase your saved stream keys, server URLs, and reset streaming balance. This action cannot be undone.",
                    color = StreamTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetData()
                    }
                ) {
                    Text("Reset All", color = StreamLiveRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = StreamTextSecondary)
                }
            },
            containerColor = StreamDarkSurfaceVariant
        )
    }
}

@Composable
private fun PolicySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StreamDarkSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, StreamDarkCardBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = StreamElectricBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = StreamTextPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
