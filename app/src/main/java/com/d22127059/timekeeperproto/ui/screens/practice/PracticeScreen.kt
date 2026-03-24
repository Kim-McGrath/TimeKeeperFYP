package com.d22127059.timekeeperproto.ui.screens.practice

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.audio.SurfaceType
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.ui.components.DebugTimingVisualization
import com.d22127059.timekeeperproto.ui.components.TrafficLightIndicator

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    currentSurfaceType: SurfaceType,
    onSurfaceTypeChanged: (SurfaceType) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val debugEvents by viewModel.debugEvents.collectAsState()
    var showDebug by remember { mutableStateOf(false) }
    var selectedBpm by remember { mutableStateOf(100) }
    var selectedDurationMinutes by remember { mutableStateOf(5) }

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
                IdleContent(onStartClick = { viewModel.initializeDetector() })
            }

            is PracticeUiState.Ready -> {
                ReadyContent(
                    currentSurfaceType = currentSurfaceType,
                    onSurfaceTypeChanged = onSurfaceTypeChanged,
                    selectedBpm = selectedBpm,
                    onBpmChanged = { selectedBpm = it },
                    selectedDurationMinutes = selectedDurationMinutes,
                    onDurationChanged = { selectedDurationMinutes = it },
                    onStartClick = {
                        viewModel.startCountdown(
                            bpm = selectedBpm,
                            durationMinutes = selectedDurationMinutes
                        )
                    },
                    onNavigateBack = onNavigateBack
                )
            }

            is PracticeUiState.Countdown -> {
                CountdownContent(countdownValue = state.countdownValue)
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
                        durationMs = state.durationMs,
                        bpm = state.bpm,
                        surfaceType = state.surfaceType,
                        isPaused = state.isPaused,
                        onPauseClick = { viewModel.pauseSession() },
                        onResumeClick = { viewModel.resumeSession() },
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
                    onDoneClick = onNavigateBack
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("TimeKeeper", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Practice rhythm accuracy", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStartClick, modifier = Modifier.width(200.dp).height(60.dp)) {
                Text("Initialize Audio", fontSize = 18.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    currentSurfaceType: SurfaceType,
    onSurfaceTypeChanged: (SurfaceType) -> Unit,
    selectedBpm: Int,
    onBpmChanged: (Int) -> Unit,
    selectedDurationMinutes: Int,
    onDurationChanged: (Int) -> Unit,
    onStartClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Text(
                text = "Ready to Practice",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // BPM Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF374151)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Tempo", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$selectedBpm BPM",
                        color = Color(0xFF3B82F6),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = selectedBpm.toFloat(),
                        onValueChange = { onBpmChanged(it.toInt()) },
                        valueRange = 40f..200f,
                        steps = 159,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF3B82F6),
                            activeTrackColor = Color(0xFF3B82F6),
                            inactiveTrackColor = Color(0xFF6B7280)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("40", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Text("120", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Text("200", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(60, 80, 100, 120, 140).forEach { bpm ->
                            BpmPresetButton(bpm.toString(), bpm, onBpmChanged)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = getTempoDescription(selectedBpm),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF374151)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Session Duration", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedDurationMinutes == 0) "Unlimited" else "$selectedDurationMinutes min",
                        color = Color(0xFF10B981),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Duration preset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(0 to "∞", 2 to "2m", 5 to "5m", 10 to "10m", 15 to "15m").forEach { (mins, label) ->
                            DurationPresetButton(
                                label = label,
                                isSelected = selectedDurationMinutes == mins,
                                onClick = { onDurationChanged(mins) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedDurationMinutes == 0)
                            "Session runs until you tap End"
                        else
                            "Session ends automatically after $selectedDurationMinutes minutes",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Surface selector
            Text("Practice Surface", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.width(250.dp)
            ) {
                OutlinedTextField(
                    value = getSurfaceDisplayName(currentSurfaceType),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF374151))
                ) {
                    SurfaceType.values().filter { it != SurfaceType.CUSTOM }.forEach { surfaceType ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(getSurfaceDisplayName(surfaceType), color = Color.White)
                                    Text(
                                        getSurfaceDescription(surfaceType),
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            },
                            onClick = { onSurfaceTypeChanged(surfaceType); expanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartClick,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Practice", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DurationPresetButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(56.dp).height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF10B981) else Color(0xFF4B5563)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BpmPresetButton(label: String, bpm: Int, onSelect: (Int) -> Unit) {
    OutlinedButton(
        onClick = { onSelect(bpm) },
        modifier = Modifier.width(60.dp).height(40.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}

@Composable
private fun CountdownContent(countdownValue: Int) {
    val scale by animateFloatAsState(
        targetValue = if (countdownValue > 0) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "countdown_scale"
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Get Ready...", color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, fontWeight = FontWeight.Medium)
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
            Text("Listen to the metronome...", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
        }
    }
}

@Composable
private fun ActiveSessionContent(
    category: AccuracyCategory?,
    hitCount: Int,
    elapsedTimeMs: Long,
    durationMs: Long,
    bpm: Int,
    surfaceType: SurfaceType,
    isPaused: Boolean,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(formatTime(elapsedTimeMs), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                // Show remaining time if duration is set
                if (durationMs != Long.MAX_VALUE) {
                    val remaining = (durationMs - elapsedTimeMs).coerceAtLeast(0)
                    Text(
                        text = "-${formatTime(remaining)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Text(getSurfaceDisplayName(surfaceType), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Text("$bpm BPM", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("Hits: $hitCount", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        // Duration progress bar (only shown if duration is set)
        if (durationMs != Long.MAX_VALUE) {
            val progress = (elapsedTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color(0xFF10B981),
                trackColor = Color(0xFF374151)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Paused overlay or indicator
        if (isPaused) {
            PausedOverlay()
        } else {
            if (category == null) {
                WaitingForHitIndicator()
            } else {
                TrafficLightIndicator(category = category, size = 380f)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control buttons
        Row(
            modifier = Modifier.padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isPaused) {
                Button(
                    onClick = onResumeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.width(130.dp).height(50.dp)
                ) {
                    Text("Resume", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onPauseClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)),
                    modifier = Modifier.width(130.dp).height(50.dp)
                ) {
                    Text("Pause", fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = onEndClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.width(130.dp).height(50.dp)
            ) {
                Text("End Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PausedOverlay() {
    val pulse = rememberInfiniteTransition(label = "pausePulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pauseAlpha"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⏸",
            fontSize = 80.sp
        )
        Text(
            text = "Paused",
            color = Color.White.copy(alpha = alpha),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tap Resume to continue",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun WaitingForHitIndicator() {
    val pulse = rememberInfiniteTransition(label = "waitPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waitAlpha"
    )
    val scale by pulse.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waitScale"
    )

    Box(modifier = Modifier.size(380.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val centerX = this.size.width / 2f
            val centerY = this.size.height / 2f

            drawCircle(
                color = Color(0xFF6B7280).copy(alpha = alpha * 0.4f),
                radius = canvasSize * 0.46f * scale,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color(0xFF9CA3AF).copy(alpha = alpha * 0.6f),
                radius = canvasSize * 0.38f * scale,
                center = Offset(centerX, centerY),
                style = Stroke(width = canvasSize * 0.025f)
            )

            val iconRadius = canvasSize * 0.18f
            drawCircle(
                color = Color(0xFF6B7280).copy(alpha = alpha),
                radius = iconRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = canvasSize * 0.022f)
            )
            drawLine(
                color = Color(0xFF9CA3AF).copy(alpha = alpha),
                start = Offset(centerX - iconRadius * 0.3f, centerY - iconRadius * 0.6f),
                end = Offset(centerX - iconRadius * 1.1f, centerY - iconRadius * 1.6f),
                strokeWidth = canvasSize * 0.022f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF9CA3AF).copy(alpha = alpha),
                start = Offset(centerX + iconRadius * 0.3f, centerY - iconRadius * 0.6f),
                end = Offset(centerX + iconRadius * 1.1f, centerY - iconRadius * 1.6f),
                strokeWidth = canvasSize * 0.022f,
                cap = StrokeCap.Round
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 80.dp)
        ) {
            Text(
                text = "Start Playing",
                color = Color.White.copy(alpha = alpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Hit in time with the metronome",
                color = Color.White.copy(alpha = alpha * 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun CompletedContent(
    stats: com.d22127059.timekeeperproto.domain.SessionStats,
    onDoneClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Session Complete!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Text("${stats.accuracyPercentage.toInt()}%", color = Color(0xFF10B981), fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text("Overall Accuracy", color = Color.White, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("Perfect", stats.greenHits, Color(0xFF10B981))
            StatCard("Good", stats.yellowHits, Color(0xFFF59E0B))
            StatCard("Off", stats.redHits, Color(0xFFEF4444))
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (stats.tendencyToRush) {
            Text("Tendency to rush (play early)", color = Color(0xFFF59E0B), fontSize = 14.sp)
        } else if (stats.tendencyToDrag) {
            Text("Tendency to drag (play late)", color = Color(0xFFF59E0B), fontSize = 14.sp)
        }
        Text("Avg timing: ${stats.averageTimingError.toInt()}ms", color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDoneClick, modifier = Modifier.width(200.dp).height(50.dp)) {
            Text("Done", fontSize = 18.sp)
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorContent(message: String, onRetryClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Error: $message", color = Color.Red, fontSize = 18.sp)
            Button(onClick = onRetryClick) { Text("Retry") }
        }
    }
}

private fun getTempoDescription(bpm: Int) = when {
    bpm < 60 -> "Largo - Very slow"
    bpm < 76 -> "Adagio - Slow"
    bpm < 108 -> "Andante - Walking pace"
    bpm < 120 -> "Moderato - Moderate"
    bpm < 168 -> "Allegro - Fast"
    else -> "Presto - Very fast"
}

private fun getSurfaceDisplayName(surfaceType: SurfaceType) = when (surfaceType) {
    SurfaceType.DRUM_KIT -> "Drum Kit"
    SurfaceType.PRACTICE_PAD -> "Practice Pad"
    SurfaceType.TABLE -> "Table/Surface"
    SurfaceType.CUSTOM -> "Custom"
}

private fun getSurfaceDescription(surfaceType: SurfaceType) = when (surfaceType) {
    SurfaceType.DRUM_KIT -> "Loud, sharp attacks"
    SurfaceType.PRACTICE_PAD -> "Quieter, damped response"
    SurfaceType.TABLE -> "Very damped, subtle"
    SurfaceType.CUSTOM -> "User-defined settings"
}

private fun formatTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    val seconds = (clamped / 1000) % 60
    val minutes = (clamped / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}