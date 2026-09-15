# 24/7 Streamer (24streamer)

Continuous 24/7 live video RTMP/RTMPS streamer for Android. Stream prerecorded videos seamlessly to YouTube Live, Facebook Live, and custom RTMP/RTMPS ingest servers with automated looping, background foreground service, and AdMob rewarded ad streaming wallet.

## Features

- **Continuous 24/7 RTMP/RTMPS Streaming**: Broadcasts live video using hardware-accelerated H.264 video and AAC audio encoding (`root-encoder`).
- **Auto-Looping Playlist Engine**: Continuously replays selected video playlists without stream disconnections or black frames.
- **Background Foreground Service**: Runs 24/7 reliably with battery optimization exemption, wake locks, and persistent status notifications.
- **Streaming Time Wallet**: Integrated AdMob rewarded ads grant +30 minutes of live streaming per completed ad, securely tracked with AES-256 encrypted storage.
- **Platform Presets & Custom Endpoints**: One-touch configuration for YouTube Live, Facebook Live, or custom RTMP/RTMPS server URLs and stream keys.
- **Real-Time Telemetry**: Live bitrates, frame rates, network health indicators, session durations, and remaining time countdown.

## Architecture

- **Jetpack Compose & Material 3**: Modern, responsive UI with dark/light themes.
- **Media3 ExoPlayer**: Efficient video decoding and seamless playlist extraction.
- **RootEncoder**: Hardware-accelerated RTMP/RTMPS streaming pipeline.
- **Room Database**: Local playlist and stream preset persistence.
- **Secure Encrypted Storage**: Android KeyStore-backed AES-256 GCM credential and time wallet management.

## Pre-Built APK

Download and install the ready-to-use APK:
- [Download 24streamer APK (`apk/app-debug.apk`)](./apk/app-debug.apk) (~29MB)
