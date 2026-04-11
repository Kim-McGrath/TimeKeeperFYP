package com.d22127059.timekeeperproto.ui.screens.practice

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.audio.SurfaceType
import com.d22127059.timekeeperproto.domain.model.AccuracyCategory
import com.d22127059.timekeeperproto.ui.components.DebugTimingVisualization
import com.d22127059.timekeeperproto.ui.components.TrafficLightIndicator
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    currentSurfaceType: SurfaceType,
    onSurfaceTypeChanged: (SurfaceType) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val keepScreenOn = uiState is PracticeUiState.Active || uiState is PracticeUiState.Countdown

    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }
    val debugEvents by viewModel.debugEvents.collectAsState()
    var showDebug by remember { mutableStateOf(false) }
    var selectedBpm by remember { mutableIntStateOf(100) }
    var selectedDurationMinutes by remember { mutableIntStateOf(5) }
    var tapModeEnabled by remember { mutableStateOf(false) }


    val colors = MaterialTheme.colorScheme

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.initializeDetector()
    }

    fun requestAudioAndInit() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.initializeDetector()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        when (val state = uiState) {
            is PracticeUiState.Idle -> {
                IdleContent(onStartClick = { requestAudioAndInit() })
            }
            is PracticeUiState.Ready -> {
                ReadyContent(
                    currentSurfaceType = currentSurfaceType,
                    onSurfaceTypeChanged = onSurfaceTypeChanged,
                    selectedBpm = selectedBpm,
                    onBpmChanged = { selectedBpm = it },
                    selectedDurationMinutes = selectedDurationMinutes,
                    onDurationChanged = { selectedDurationMinutes = it },
                    tapModeEnabled = tapModeEnabled,
                    onTapModeChanged = { tapModeEnabled = it },
                    onStartClick = {
                        viewModel.startCountdown(
                            bpm = selectedBpm,
                            durationMinutes = selectedDurationMinutes,
                            tapMode = tapModeEnabled
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
                        tapModeEnabled = state.tapModeEnabled,
                        onTapHit = { viewModel.onTapHit() },
                        onPauseClick = { viewModel.pauseSession() },
                        onResumeClick = { viewModel.resumeSession() },
                        onEndClick = { viewModel.endSession() }
                    )
                }
                Button(
                    onClick = { showDebug = !showDebug },
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showDebug) Color(0xFFFF6B35) else colors.surfaceVariant,
                        contentColor = if (showDebug) Color.White else colors.onSurfaceVariant
                    )
                ) {
                    Text(if (showDebug) "NORMAL VIEW" else "DEBUG VIEW")
                }
            }
            is PracticeUiState.Completed -> {
                CompletedContent(stats = state.stats, onDoneClick = onNavigateBack)
            }
            is PracticeUiState.Error -> {
                ErrorContent(message = state.message, onRetryClick = { requestAudioAndInit() })
            }
        }
    }
}

@Composable
fun IdleContent(onStartClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("TimeKeeper", color = colors.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Practice rhythm accuracy", color = colors.onSurfaceVariant, fontSize = 18.sp)
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
    tapModeEnabled: Boolean,
    onTapModeChanged: (Boolean) -> Unit,
    onStartClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    // Tap tempo state
    val tapTimes = remember { mutableStateListOf<Long>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
            }
            Text(
                "Set Up Practice",
                color = colors.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // BPM Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tempo", color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$selectedBpm BPM",
                    color = colors.primary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    getTempoDescription(selectedBpm),
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = selectedBpm.toFloat(),
                    onValueChange = { onBpmChanged(it.toInt()) },
                    valueRange = 40f..200f,
                    steps = 159,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = colors.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("40", color = colors.onSurfaceVariant, fontSize = 11.sp)
                    Text("120", color = colors.onSurfaceVariant, fontSize = 11.sp)
                    Text("200", color = colors.onSurfaceVariant, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(60, 80, 100, 120, 140).forEach { bpm ->
                        BpmPresetButton(bpm.toString(), bpm, onBpmChanged)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Tap tempo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Tap Tempo",
                            color = colors.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Tap the button to the beat",
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            // Reset if last tap was more than 3 seconds ago
                            if (tapTimes.isNotEmpty() && now - tapTimes.last() > 3000) {
                                tapTimes.clear()
                            }
                            tapTimes.add(now)
                            if (tapTimes.size >= 2) {
                                val intervals = tapTimes.zipWithNext { a, b -> b - a }
                                val avgInterval = intervals.average()
                                val calculatedBpm = (60000.0 / avgInterval).toInt().coerceIn(40, 200)
                                onBpmChanged(calculatedBpm)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(80.dp).height(44.dp)
                    ) {
                        Text("TAP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Duration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Session Duration", color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (selectedDurationMinutes == 0) "Unlimited" else "$selectedDurationMinutes min",
                    color = colors.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0 to "∞", 2 to "2m", 5 to "5m", 10 to "10m", 15 to "15m").forEach { (mins, label) ->
                        DurationPresetButton(
                            label = label,
                            isSelected = selectedDurationMinutes == mins,
                            onClick = { onDurationChanged(mins) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Surface Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Practice Surface", color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = getSurfaceDisplayName(currentSurfaceType),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onBackground,
                            unfocusedTextColor = colors.onBackground,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outline
                        ),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(colors.surface)
                    ) {
                        SurfaceType.entries.filter { it != SurfaceType.CUSTOM }.forEach { surfaceType ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(getSurfaceDisplayName(surfaceType), color = colors.onBackground)
                                        Text(getSurfaceDescription(surfaceType), color = colors.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                },
                                onClick = { onSurfaceTypeChanged(surfaceType); expanded = false }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tap mode toggle — presented as a demo/accessibility option
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (tapModeEnabled) colors.primary.copy(alpha = 0.08f) else colors.surface
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Screen Tap Mode",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "DEMO",
                                color = colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "Tap the screen instead of using the microphone. Useful in loud environments.",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = tapModeEnabled,
                    onCheckedChange = onTapModeChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Start Practice", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DurationPresetButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = Modifier.width(56.dp).height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) colors.primary else colors.surfaceVariant,
            contentColor = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BpmPresetButton(label: String, bpm: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = { onSelect(bpm) },
        modifier = Modifier.width(56.dp).height(40.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun CountdownContent(countdownValue: Int) {
    val colors = MaterialTheme.colorScheme
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
            Text("Get Ready...", color = colors.onSurfaceVariant, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            if (countdownValue > 0) {
                Text(countdownValue.toString(), color = colors.primary, fontSize = (120 * scale).sp, fontWeight = FontWeight.Bold)
            } else {
                Text("GO!", color = Color(0xFFFF6B35), fontSize = (100 * scale).sp, fontWeight = FontWeight.Bold)
            }
            Text("Listen to the metronome...", color = colors.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 16.sp)
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
    tapModeEnabled: Boolean,
    onTapHit: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onEndClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(formatTime(elapsedTimeMs), color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (durationMs != Long.MAX_VALUE) {
                    val remaining = (durationMs - elapsedTimeMs).coerceAtLeast(0)
                    Text("-${formatTime(remaining)}", color = colors.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(getSurfaceDisplayName(surfaceType), color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
            Text("$bpm BPM", color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("Hits: $hitCount", color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        if (durationMs != Long.MAX_VALUE) {
            val progress = (elapsedTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = colors.primary,
                trackColor = colors.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isPaused) {
            PausedOverlay()
        } else if (tapModeEnabled) {
            // Tap mode UI — large tappable area with indicator
            TapModeContent(
                category = category,
                onTapHit = onTapHit
            )
        } else {
            if (category == null) WaitingForHitIndicator()
            else TrafficLightIndicator(category = category, size = 380f)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isPaused) {
                Button(
                    onClick = onResumeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    modifier = Modifier.width(130.dp).height(50.dp)
                ) { Text("Resume", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = onPauseClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceVariant,
                        contentColor = colors.onSurfaceVariant
                    ),
                    modifier = Modifier.width(130.dp).height(50.dp)
                ) { Text("Pause", fontWeight = FontWeight.Bold) }
            }
            Button(
                onClick = onEndClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.error),
                modifier = Modifier.width(130.dp).height(50.dp)
            ) { Text("End Session", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TapModeContent(
    category: AccuracyCategory?,
    onTapHit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Flash animation on tap
    val flashAnim = remember { Animatable(0f) }
    LaunchedEffect(category) {
        if (category != null) {
            flashAnim.snapTo(1f)
            flashAnim.animateTo(0f, tween(300))
        }
    }

    val tapColor = when (category) {
        AccuracyCategory.GREEN -> Color(0xFF10B981)
        AccuracyCategory.YELLOW -> Color(0xFFF59E0B)
        AccuracyCategory.RED -> Color(0xFFEF4444)
        null -> colors.primary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // The small indicator above the tap button
        if (category != null) {
            TrafficLightIndicator(category = category, size = 120f)
        } else {
            Spacer(modifier = Modifier.height(120.dp))
        }

        // Large tap button
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(
                    color = tapColor.copy(alpha = 0.1f + flashAnim.value * 0.3f),
                    shape = RoundedCornerShape(32.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { onTapHit() }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TAP", color = tapColor, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(
                    "to the beat",
                    color = colors.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PausedOverlay() {
    val colors = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pausePulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pauseAlpha"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Paused",
            color = colors.onBackground.copy(alpha = alpha),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Tap Resume to continue", color = colors.onSurfaceVariant, fontSize = 16.sp)
    }
}

@Composable
private fun WaitingForHitIndicator() {
    val colors = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "waitPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "waitAlpha"
    )
    val scale by pulse.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "waitScale"
    )
    Box(modifier = Modifier.size(380.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size.minDimension
            val centerX = this.size.width / 2f
            val centerY = this.size.height / 2f
            drawCircle(color = Color(0xFF6B7280).copy(alpha = alpha * 0.4f), radius = canvasSize * 0.46f * scale, center = Offset(centerX, centerY))
            drawCircle(color = Color(0xFF9CA3AF).copy(alpha = alpha * 0.6f), radius = canvasSize * 0.38f * scale, center = Offset(centerX, centerY), style = Stroke(width = canvasSize * 0.025f))
            val iconRadius = canvasSize * 0.18f
            drawCircle(color = Color(0xFF6B7280).copy(alpha = alpha), radius = iconRadius, center = Offset(centerX, centerY), style = Stroke(width = canvasSize * 0.022f))
            drawLine(color = Color(0xFF9CA3AF).copy(alpha = alpha), start = Offset(centerX - iconRadius * 0.3f, centerY - iconRadius * 0.6f), end = Offset(centerX - iconRadius * 1.1f, centerY - iconRadius * 1.6f), strokeWidth = canvasSize * 0.022f, cap = StrokeCap.Round)
            drawLine(color = Color(0xFF9CA3AF).copy(alpha = alpha), start = Offset(centerX + iconRadius * 0.3f, centerY - iconRadius * 0.6f), end = Offset(centerX + iconRadius * 1.1f, centerY - iconRadius * 1.6f), strokeWidth = canvasSize * 0.022f, cap = StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 80.dp)) {
            Text("Start Playing", color = colors.onBackground.copy(alpha = alpha), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Hit in time with the metronome", color = colors.onSurfaceVariant.copy(alpha = alpha), fontSize = 14.sp)
        }
    }
}

@Composable
private fun CompletedContent(stats: com.d22127059.timekeeperproto.domain.SessionStats, onDoneClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Session Complete!", color = colors.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Text("${stats.accuracyPercentage.toInt()}%", color = colors.primary, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Text("Overall Accuracy", color = colors.onBackground, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCard("Perfect", stats.greenHits, colors.primary)
            StatCard("Good", stats.yellowHits, colors.secondary)
            StatCard("Off", stats.redHits, colors.error)
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (stats.tendencyToRush) Text("Tendency to rush (play early)", color = colors.secondary, fontSize = 14.sp)
        else if (stats.tendencyToDrag) Text("Tendency to drag (play late)", color = colors.secondary, fontSize = 14.sp)
        Text("Avg timing: ${stats.averageTimingError.toInt()}ms", color = colors.onBackground, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDoneClick, modifier = Modifier.width(200.dp).height(50.dp)) {
            Text("Done", fontSize = 18.sp)
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, color: Color) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(label, color = colors.onBackground, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorContent(message: String, onRetryClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Error: $message", color = colors.error, fontSize = 18.sp)
            Button(onClick = onRetryClick) { Text("Retry") }
        }
    }
}

private fun getTempoDescription(bpm: Int) = when {
    bpm < 60  -> "Very slow - good for careful practice"
    bpm < 76  -> "Slow - great for beginners"
    bpm < 108 -> "Medium - comfortable pace"
    bpm < 120 -> "Moderate - steady and controlled"
    bpm < 168 -> "Fast - challenging timing"
    else      -> "Very fast - advanced practice"
}

fun getSurfaceDisplayName(surfaceType: SurfaceType) = when (surfaceType) {
    SurfaceType.DRUM_KIT -> "Drum Kit"
    SurfaceType.PRACTICE_PAD -> "Practice Pad"
    SurfaceType.TABLE -> "Table / Surface"
    SurfaceType.CUSTOM -> "Custom"
}

private fun getSurfaceDescription(surfaceType: SurfaceType) = when (surfaceType) {
    SurfaceType.DRUM_KIT -> "Loud, sharp attacks"
    SurfaceType.PRACTICE_PAD -> "Quieter, damped response"
    SurfaceType.TABLE -> "Very damped, subtle"
    SurfaceType.CUSTOM -> "User-defined settings"
}

@SuppressLint("DefaultLocale")
private fun formatTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    val seconds = (clamped / 1000) % 60
    val minutes = (clamped / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}