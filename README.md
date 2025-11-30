# TimeKeeper

A rhythm training Android application designed to help drummers improve their timing accuracy through real-time audio feedback.

## Overview

TimeKeeper will be an app that provides drummers with immediate visual feedback on their timing accuracy. The application uses the device's microphone to detect drum hits and analyses their timing relative to a metronome beat, categorising each hit as perfect (green), acceptable (yellow), or off-beat (red).

## Current Features

### Core Functionality
- **Real-time onset detection** using TarsosDSP library to identify drum hits through microphone input
- **Built-in metronome** with adjustable BPM that plays synchronized click tracks
- **Timing analysis** that measures timing error for each detected hit
- **Visual feedback system** using traffic light indicators (circle for perfect, diamond for acceptable, triangle for off-beat)
- **Session statistics** including accuracy percentage, hit breakdown, and timing tendencies (rushing vs dragging)

### Technical Implementation
- Audio processing with 44.1kHz sample rate and 2048-sample buffers
- Latency compensation system accounting for audio output delays (280ms)
- Intelligent filtering to distinguish drum hits from metronome clicks (15ms threshold)
- Configurable accuracy thresholds (currently set to ±50ms for perfect, ±150ms for acceptable)
- Room database integration for persistent session storage

### User Interface
- Countdown sequence before session begins (beats 0-4)
- Active session display with real-time accuracy indicator
- Session completion screen with detailed statistics
- Debug mode for visualising metronome clicks and detected hits on a timeline

## Architecture

The application follows MVVM architecture with clear separation of concerns:

- **Audio Layer**: `MetronomeEngine` and `OnsetDetector` handle audio generation and input processing
- **Domain Layer**: `TimingAnalyzer` performs timing calculations and categorisation
- **Data Layer**: Room database with `Session` and `Hit` entities
- **UI Layer**: Jetpack Compose screens with `PracticeViewModel` managing state

## Future Implementations

### Enhanced Features
- **Multiple practice modes**: isolated timing exercises, pattern recognition drills, tempo ramping
- **Customisable surfaces**: different sensitivity profiles for drum kits, practice pads, and tables
- **Historical tracking**: progress visualisation over time with graphs and trends

### Technical Improvements
- **Improved metronome playback**: buffer underrun fixes for reliable performance on physical devices
- **Advanced filtering algorithms**: better distinction between drum hits and ambient noise
- **Adaptive latency compensation**: automatic calibration based on device hardware
- **Audio focus management**: proper handling of background/foreground transitions
- **Configuration persistence**: save user preferences and session settings

### User Experience
- **Custom click sounds**: user-selectable metronome sounds (wood block, cowbell, electronic)
- **Subdivision support**: practice with eighth notes, triplets, and other subdivisions
- **Visual metronome**: optional visual cue alongside audio clicks
- **Session presets**: save and load common practice configurations
- **Export functionality**: share session data for analysis or coaching

## Project Status

This is a prototype version developed for demonstration and evaluation purposes. The application currently functions reliably on the Android Emulator with forgiving accuracy thresholds to showcase the core timing detection and feedback system.

## Technologies Used

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Audio Processing**: TarsosDSP 2.5
- **Database**: Room Persistence Library
- **Concurrency**: Kotlin Coroutines
- **Architecture**: Android ViewModel with StateFlow

## Author
Student Name: Kim McGrath
Student ID: D22127059  
Supervisor: Emma Murphy  
Institution: Technological University Dublin

Academic References
This project builds upon established research in rhythm perception and audio processing:
Repp, B. H., & Su, Y. H. (2013). Sensorimotor synchronization: A review of recent research (2006–2012). Psychonomic Bulletin & Review, 20(3), 403-452.
[Informed timing threshold design and asymmetric tolerance for early/late hits]
Miendlarzewska, E. A., & Trost, W. J. (2014). How musical training affects cognitive development: Rhythm, reward and other modulating variables. Frontiers in Neuroscience, 7, 279.
[Cognitive benefits of rhythm training that motivated the project]
Duggan, B. (2010). TunePal: An iPhone Application for Session Musicians. Proceedings of the Sound and Music Computing Conference 2010.
[Demonstrated feasibility of mobile audio processing for musical applications]

Technical Resources
TarsosDSP Library (v2.5) by Joren Six: Open-source audio processing framework used for onset detection
https://github.com/JorenSix/TarsosDSP

AI Assistance
Claude AI was used for:
Understanding audio pipeline architecture and metronome synchronization timing
Debugging AudioTrack latency compensation strategies

All core algorithms (timing analysis, onset detection integration, metronome engine) were designed and implemented without AI.
