package com.d22127059.timekeeperproto.ui.screens.practice

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
import com.d22127059.timekeeperproto.ui.components.TrafficLightIndicator


@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    onStartClick = { viewModel.startSession() }
                )
            }

            is PracticeUiState.Ready -> {
                ReadyContent(
                    onStartClick = { viewModel.startSession() }
                )
            }

            is PracticeUiState.Active -> {
                ActiveSessionContent(
                    category = state.currentCategory,
                    hitCount = state.hitCount,
                    elapsedTimeMs = state.elapsedTimeMs,
                    bpm = state.bpm,
                    onPauseClick = { viewModel.pauseSession() },
                    onEndClick = { viewModel.endSession() }
                )
            }

            is PracticeUiState.Completed -> {
                CompletedContent(
                    stats = state.stats,
                    onDoneClick = { /* Navigate away */ }
                )
            }

            is PracticeUiState.Error -> {
                ErrorContent(
                    message = state.message
                )
            }
        }
    }
}

@Composable
private fun IdleContent(onStartClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
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

@Composable
private fun ReadyContent(onStartClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
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
        // Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(elapsedTimeMs),
                color = Color.White,
                fontSize = 16.sp
            )
            Text(
                text = "$bpm BPM",
                color = Color.White,
                fontSize = 16.sp
            )
            Text(
                text = "Hits: $hitCount",
                color = Color.White,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Large centered feedback indicator
        TrafficLightIndicator(
            category = category,
            size = 250f
        )

        Spacer(modifier = Modifier.weight(1f))

        // Control buttons
        Row(
            modifier = Modifier.padding(bottom = 32.dp),
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

        // Accuracy percentage
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

        // Hit distribution
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("Perfect", stats.greenHits, Color(0xFF10B981))
            StatCard("Good", stats.yellowHits, Color(0xFFF59E0B))
            StatCard("Off", stats.redHits, Color(0xFFEF4444))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timing pattern
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
            Text("Done", fontSize = 18.sp)
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
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Error: $message",
            color = Color.Red,
            fontSize = 18.sp
        )
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}