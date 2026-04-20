# TimeKeeper

A rhythm training Android application for drummers and percussionists. TimeKeeper listens to you play through the device microphone, compares your hits against a built-in metronome, and provides immediate visual feedback on your timing accuracy.

Link to Demo video: https://youtu.be/KS28C5F9Pk4

## Overview

The app is designed for both musicians and non-musicians who want to develop rhythmic accuracy. Each detected hit is categorised as perfect, acceptable, or off-beat, and displayed as one of three distinct shapes with corresponding colours. The shape system was designed to remain readable for colour-blind users.

Sessions are saved locally and optionally synced to a cloud account, with per-session analysis including timing direction, consistency score, and segment breakdown.

## Features

- Real-time percussion onset detection via TarsosDSP
- Built-in metronome with BPM slider, presets, and tap tempo
- Traffic light shape indicator system with pulse animations
- Session duration control with preset options
- Surface type selection with empirically calibrated detection values
- Screen tap mode for use in loud environments
- Session history with accuracy trend chart and swipe-to-delete
- Detailed session analysis: hit breakdown, timing direction, consistency score, best streak, session segments
- Firebase Authentication and Firestore for optional cloud accounts
- Global leaderboard for registered users
- Daily streak counter
- Light and dark theme

## Architecture

The app follows MVVM architecture with Jetpack Compose for the UI layer.

- Audio layer: `MetronomeEngine` and `OnsetDetector` handle audio output and microphone input
- Domain layer: `TimingAnalyzer` performs timing calculations and categorisation
- Data layer: Room database for local persistence, Firestore for cloud sync
- UI layer: Compose screens with `PracticeViewModel` managing session state

## Detection

Onset detection uses TarsosDSP's `PercussionOnsetDetector` with a 100ms debounce to suppress snare wire resonance and sympathetic vibration. A latency compensation constant of 230ms accounts for microphone input delay on the test device. Detection values were determined through empirical testing on a physical device.

Headphone use is strongly recommended. When the metronome plays through the device speaker, acoustic echo cancellation cannot fully separate the click from drum transients, which reduces detection reliability.

## Setup

The project requires a `google-services.json` file placed in the `app/` directory to enable Firebase. This file is excluded from the repository. The app will build without it but Firebase features will not function.

## Requirements

- Android SDK 26 or higher
- Microphone permission (requested at launch)
- Internet connection for leaderboard and cloud sync features (optional)
