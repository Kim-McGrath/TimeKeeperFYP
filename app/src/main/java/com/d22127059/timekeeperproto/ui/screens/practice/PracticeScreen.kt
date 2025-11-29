package com.d22127059.timekeeperproto.ui.screens.practice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.ui.components.DebugTimingVisualization
import com.d22127059.timekeeperproto.ui.components.TrafficLightIndicator

/**
 * Practice session screen.
 */
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val debugEvents by viewModel.debugEvents.collectAsState()
    var showDebug by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initializeDetector()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1F2937))
    ) {
        when (val state = uiState) {
            is PracticeUiState.Idle -> {
                IdleContent(
                    onStartClick = { viewModel.initializeDetector() }
                )
            }

            is PracticeUiState.Ready -> {
                ReadyContent(
                    onStartClick = { viewModel.startCountdown() }  // ✅ CHANGED
                )
            }

            is PracticeUiState.Countdown -> {  // ✅ NEW STATE
                CountdownContent(
                    countdownValue = state.countdownValue
                )
            }

            is PracticeUiState.Active -> {
                if (showDebug) {
                    DebugTimingVisualization(
                        events = debugEvents,
                        sessionStartTime = state.sessionStartTime,
                        currentTime = System.currentTimeMillis()
                    )
                } else {
                    ActiveSessionContent(
                        category = state.currentCategory,
                        hitCount = state.hitCount,
                        elapsedTimeMs = state.elapsedTimeMs,
                        bpm = state.bpm,
                        onPauseClick = { viewModel.pauseSession() },
                        onEndClick = { viewModel.endSession() }
                    )
                }

                Button(
                    onClick = { showDebug = !showDebug },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showDebug) Color(0xFFFF6B35) else Color(0xFF6B7280)
                    )
                ) {
                    Text(if (showDebug) "NORMAL VIEW" else "DEBUG VIEW")
                }
            }

            is PracticeUiState.Completed -> {
                CompletedContent(
                    stats = state.stats,
                    onDoneClick = {
                        viewModel.initializeDetector()
                    }
                )
            }

            is PracticeUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetryClick = { viewModel.initializeDetector() }
                )
            }
        }
    }
}

@Composable
fun IdleContent(onStartClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "TimeKeeper",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Practice rhythm accuracy",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .width(200.dp)
                    .height(60.dp)
            ) {
                Text("Initialize Audio", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ReadyContent(onStartClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Ready to Practice",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .width(200.dp)
                    .height(60.dp)
            ) {
                Text("Start Practice", fontSize = 18.sp)
            }
        }
    }
}

// ✅ NEW: Countdown screen
@Composable
private fun CountdownContent(countdownValue: Int) {
    // Animate the countdown number
    val scale by animateFloatAsState(
        targetValue = if (countdownValue > 0) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "countdown_scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Get Ready...",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )

            if (countdownValue > 0) {
                Text(
                    text = countdownValue.toString(),
                    color = Color(0xFF10B981),
                    fontSize = (120 * scale).sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "GO!",
                    color = Color(0xFFFF6B35),
                    fontSize = (100 * scale).sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Listen to the metronome...",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp
            )
        }
    }
}

// ... rest of existing composables (ActiveSessionContent, CompletedContent, etc.) ...

@Composable
private fun ActiveSessionContent(
    category: AccuracyCategory,
    hitCount: Int,
    elapsedTimeMs: Long,
    bpm: Int,
    onPauseClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(elapsedTimeMs),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$bpm BPM",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Hits: $hitCount",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        TrafficLightIndicator(
            category = category,
            size = 250f
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onPauseClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                )
            ) {
                Text("Pause")
            }
            Button(
                onClick = onEndClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Text("End Session")
            }
        }
    }
}

@Composable
private fun CompletedContent(
    stats: com.d22127059.timekeeperproto.domain.SessionStats,
    onDoneClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Session Complete!",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "${stats.accuracyPercentage.toInt()}%",
            color = Color(0xFF10B981),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Overall Accuracy",
            color = Color.White,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("Perfect", stats.greenHits, Color(0xFF10B981))
            StatCard("Good", stats.yellowHits, Color(0xFFF59E0B))
            StatCard("Off", stats.redHits, Color(0xFFEF4444))
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (stats.tendencyToRush) {
            Text(
                text = "Tendency to rush (play early)",
                color = Color(0xFFF59E0B),
                fontSize = 14.sp
            )
        } else if (stats.tendencyToDrag) {
            Text(
                text = "Tendency to drag (play late)",
                color = Color(0xFFF59E0B),
                fontSize = 14.sp
            )
        }

        Text(
            text = "Avg timing: ${stats.averageTimingError.toInt()}ms",
            color = Color.White,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDoneClick,
            modifier = Modifier
                .width(200.dp)
                .height(50.dp)
        ) {
            Text("Start New Session", fontSize = 18.sp)
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            color = color,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Error: $message",
                color = Color.Red,
                fontSize = 18.sp
            )
            Button(onClick = onRetryClick) {
                Text("Retry")
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}