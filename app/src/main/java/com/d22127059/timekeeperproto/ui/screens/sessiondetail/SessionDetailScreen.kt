package com.d22127059.timekeeperproto.ui.screens.sessiondetail

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.data.local.entities.Hit
import com.d22127059.timekeeperproto.data.local.entities.Session
import com.d22127059.timekeeperproto.data.repository.SessionRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    repository: SessionRepository,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<Session?>(null) }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }

    LaunchedEffect(sessionId) {
        scope.launch {
            session = repository.getSession(sessionId)
            hits = repository.getHitsForSession(sessionId)
        }
    }

    val s = session

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (s == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // Pre-compute all derived statistics once so they can be passed to sub-components
            val derivedStats = remember(hits) { computeDerivedStats(hits) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        colors.primary.copy(alpha = 0.9f),
                                        colors.primary.copy(alpha = 0.5f)
                                    )
                                )
                            )
                            .padding(bottom = 28.dp)
                    ) {
                        Column {
                            TopAppBar(
                                title = { },
                                navigationIcon = {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent
                                )
                            )
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = formatDate(s.timestamp),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "${s.accuracyPercentage.toInt()}",
                                        color = Color.White,
                                        fontSize = 80.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 80.sp
                                    )
                                    Text(
                                        text = "%",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                                    )
                                }
                                Text(
                                    text = getOverallVerdict(s.accuracyPercentage),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    QuickStat("${s.bpm} BPM", "Tempo")
                                    QuickStat(formatDuration(s.actualDurationMs), "Duration")
                                    QuickStat(s.surfaceType.replace("_", " "), "Surface")
                                    QuickStat("${s.totalHits}", "Hits")
                                }
                            }
                        }
                    }
                }

                // Hit breakdown card
                item {
                    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Hit Breakdown",
                            color = colors.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (s.totalHits > 0) {
                            AnimatedBreakdownBar(
                                green = s.greenHits,
                                yellow = s.yellowHits,
                                red = s.redHits,
                                total = s.totalHits
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            HitStatColumn(s.greenHits, "Perfect", "Within ±50ms", colors.primary)
                            HitStatColumn(s.yellowHits, "Good", "Within ±150ms", colors.secondary)
                            HitStatColumn(s.redHits, "Off Beat", "Beyond ±150ms", colors.error)
                        }
                    }
                }

                // Enhanced stats card — early/late, consistency, streak, best segment
                if (hits.size >= 3) {
                    item {
                        SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "Performance Breakdown",
                                color = colors.onBackground,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Early vs late row
                            EarlyLateRow(
                                earlyCount = derivedStats.earlyCount,
                                lateCount = derivedStats.lateCount,
                                onTimeCount = derivedStats.onTimeCount,
                                total = hits.size
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(16.dp))

                            // Consistency and streak row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ConsistencyBadge(
                                    label = "Consistency",
                                    value = derivedStats.consistencyLabel,
                                    sublabel = "±${derivedStats.standardDeviationMs.toInt()}ms spread",
                                    color = derivedStats.consistencyColor(colors)
                                )
                                ConsistencyBadge(
                                    label = "Best Streak",
                                    value = "${derivedStats.longestGreenStreak}",
                                    sublabel = "consecutive perfect hits",
                                    color = colors.primary
                                )
                            }

                            // Best segment — only meaningful if enough hits exist
                            if (hits.size >= 9) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(16.dp))
                                BestSegmentRow(derivedStats = derivedStats)
                            }
                        }
                    }
                }

                // Timing analysis card
                item {
                    SectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Timing Analysis",
                            color = colors.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TimingMeter(averageErrorMs = s.averageTimingError)
                        Spacer(modifier = Modifier.height(20.dp))
                        val analysis = getTendencyAnalysis(
                            s.averageTimingError,
                            s.tendencyToRush,
                            s.tendencyToDrag
                        )
                        TendencyCard(
                            title = analysis.title,
                            description = analysis.description,
                            tip = analysis.tip,
                            color = analysis.color
                        )
                    }
                }

                // Individual hits
                if (hits.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent Hits",
                            color = colors.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    items(hits.takeLast(20).reversed()) { hit ->
                        HitRow(
                            hit = hit,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                        )
                    }
                    if (hits.size > 20) {
                        item {
                            Text(
                                text = "Showing last 20 of ${hits.size} hits",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Derived statistics ────────────────────────────────────────────────────────

data class DerivedStats(
    val earlyCount: Int,
    val lateCount: Int,
    val onTimeCount: Int,
    val standardDeviationMs: Double,
    val consistencyLabel: String,
    val longestGreenStreak: Int,
    // Best segment: index 0=first third, 1=middle, 2=final third
    val bestSegmentIndex: Int,
    val segmentAccuracies: List<Double>
) {
    @Composable
    fun consistencyColor(colors: ColorScheme): Color = when (consistencyLabel) {
        "Excellent" -> colors.primary
        "Good" -> colors.secondary
        else -> colors.error
    }
}

private fun computeDerivedStats(hits: List<Hit>): DerivedStats {
    if (hits.isEmpty()) {
        return DerivedStats(0, 0, 0, 0.0, "N/A", 0, 0, listOf(0.0, 0.0, 0.0))
    }

    // Early / late / on-time counts
    // "On time" means within ±10ms — effectively indistinguishable from the beat
    val earlyCount = hits.count { it.timingErrorMs < -10.0 }
    val lateCount = hits.count { it.timingErrorMs > 10.0 }
    val onTimeCount = hits.size - earlyCount - lateCount

    // Standard deviation of timing errors
    val errors = hits.map { it.timingErrorMs }
    val mean = errors.average()
    val variance = errors.map { (it - mean) * (it - mean) }.average()
    val stdDev = sqrt(variance)

    val consistencyLabel = when {
        stdDev < 30.0 -> "Excellent"
        stdDev < 60.0 -> "Good"
        else -> "Developing"
    }

    // Longest consecutive green streak
    var longestStreak = 0
    var currentStreak = 0
    for (hit in hits) {
        if (hit.accuracyCategory == "GREEN") {
            currentStreak++
            if (currentStreak > longestStreak) longestStreak = currentStreak
        } else {
            currentStreak = 0
        }
    }

    // Best segment — split hits into thirds and find the most accurate portion
    val segmentSize = hits.size / 3
    val segmentAccuracies = if (segmentSize > 0) {
        listOf(
            hits.take(segmentSize),
            hits.drop(segmentSize).take(segmentSize),
            hits.drop(segmentSize * 2)
        ).map { segment ->
            if (segment.isEmpty()) 0.0
            else {
                val acceptable = segment.count {
                    it.accuracyCategory == "GREEN" || it.accuracyCategory == "YELLOW"
                }
                (acceptable.toDouble() / segment.size) * 100.0
            }
        }
    } else {
        listOf(0.0, 0.0, 0.0)
    }

    val bestSegmentIndex = segmentAccuracies.indexOf(segmentAccuracies.max())

    return DerivedStats(
        earlyCount = earlyCount,
        lateCount = lateCount,
        onTimeCount = onTimeCount,
        standardDeviationMs = stdDev,
        consistencyLabel = consistencyLabel,
        longestGreenStreak = longestStreak,
        bestSegmentIndex = bestSegmentIndex,
        segmentAccuracies = segmentAccuracies
    )
}

// ── New sub-components ────────────────────────────────────────────────────────

@Composable
private fun EarlyLateRow(
    earlyCount: Int,
    lateCount: Int,
    onTimeCount: Int,
    total: Int
) {
    val colors = MaterialTheme.colorScheme
    val blue = Color(0xFF3B82F6)

    Column {
        Text(
            "Timing Direction",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DirectionStat(
                count = earlyCount,
                total = total,
                label = "Early",
                sublabel = "hit before the beat",
                color = blue
            )
            DirectionStat(
                count = onTimeCount,
                total = total,
                label = "On Time",
                sublabel = "within ±10ms",
                color = colors.primary
            )
            DirectionStat(
                count = lateCount,
                total = total,
                label = "Late",
                sublabel = "hit after the beat",
                color = colors.secondary
            )
        }

        // Visual proportion bar
        if (total > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                if (earlyCount > 0) Box(
                    modifier = Modifier
                        .weight(earlyCount.toFloat())
                        .fillMaxHeight()
                        .background(blue)
                )
                if (onTimeCount > 0) Box(
                    modifier = Modifier
                        .weight(onTimeCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.primary)
                )
                if (lateCount > 0) Box(
                    modifier = Modifier
                        .weight(lateCount.toFloat())
                        .fillMaxHeight()
                        .background(colors.secondary)
                )
                // Fill remainder if all counts are zero edge case
                if (earlyCount == 0 && onTimeCount == 0 && lateCount == 0) Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors.surfaceVariant)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Early", color = blue.copy(alpha = 0.7f), fontSize = 10.sp)
                Text("Late", color = colors.secondary.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DirectionStat(
    count: Int,
    total: Int,
    label: String,
    sublabel: String,
    color: Color
) {
    val colors = MaterialTheme.colorScheme
    val percentage = if (total > 0) (count * 100 / total) else 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$count",
            color = color,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$percentage%",
            color = color.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(label, color = colors.onBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(sublabel, color = colors.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun ConsistencyBadge(
    label: String,
    value: String,
    sublabel: String,
    color: Color
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(label, color = colors.onSurfaceVariant, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            sublabel,
            color = colors.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BestSegmentRow(derivedStats: DerivedStats) {
    val colors = MaterialTheme.colorScheme
    val segmentLabels = listOf("First third", "Middle third", "Final third")
    val bestLabel = segmentLabels[derivedStats.bestSegmentIndex]
    val bestAccuracy = derivedStats.segmentAccuracies[derivedStats.bestSegmentIndex].toInt()

    Column {
        Text("Session Segments", color = colors.onSurfaceVariant, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            derivedStats.segmentAccuracies.forEachIndexed { index, accuracy ->
                val isBest = index == derivedStats.bestSegmentIndex
                val segColor = if (isBest) colors.primary else colors.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isBest) colors.primary.copy(alpha = 0.10f)
                            else colors.surfaceVariant
                        )
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${accuracy.toInt()}%",
                        color = segColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        segmentLabels[index],
                        color = segColor.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isBest) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Best",
                            color = colors.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your strongest section was the $bestLabel at $bestAccuracy% accuracy.",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

// ── Existing sub-components (unchanged) ──────────────────────────────────────

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        content = { Column(modifier = Modifier.padding(20.dp), content = content) }
    )
}

@Composable
private fun QuickStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun AnimatedBreakdownBar(green: Int, yellow: Int, red: Int, total: Int) {
    if (total == 0) return
    val colors = MaterialTheme.colorScheme

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "barAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (green > 0) Box(
                modifier = Modifier
                    .weight(green.toFloat() * animProgress)
                    .fillMaxHeight()
                    .background(colors.primary)
            )
            if (yellow > 0) Box(
                modifier = Modifier
                    .weight(yellow.toFloat() * animProgress)
                    .fillMaxHeight()
                    .background(colors.secondary)
            )
            if (red > 0) Box(
                modifier = Modifier
                    .weight(red.toFloat() * animProgress)
                    .fillMaxHeight()
                    .background(colors.error)
            )
        }
    }
}

@Composable
private fun HitStatColumn(count: Int, label: String, sublabel: String, color: Color) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(label, color = colors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(sublabel, color = colors.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun TimingMeter(averageErrorMs: Double) {
    val colors = MaterialTheme.colorScheme
    val clampedError = averageErrorMs.coerceIn(-300.0, 300.0)
    val fraction = ((clampedError + 300.0) / 600.0).toFloat()

    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "meterAnim"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Early", color = colors.onSurfaceVariant, fontSize = 12.sp)
            Text(
                text = if (kotlin.math.abs(averageErrorMs) < 10) "On Time"
                else "${averageErrorMs.toInt()}ms",
                color = colors.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("Late", color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.4f),
                                colors.primary.copy(alpha = 0.6f),
                                Color(0xFFEF4444).copy(alpha = 0.4f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animFraction)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .align(Alignment.Center)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
private fun TendencyCard(
    title: String,
    description: String,
    tip: String,
    color: Color
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(description, color = colors.onBackground, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.08f))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Tip: $tip",
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun HitRow(hit: Hit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (color, label) = when (hit.accuracyCategory) {
        "GREEN" -> colors.primary to "Perfect"
        "YELLOW" -> colors.secondary to "Good"
        else -> colors.error to "Off Beat"
    }
    val errorText = when {
        hit.timingErrorMs > 10 -> "+${hit.timingErrorMs.toInt()}ms late"
        hit.timingErrorMs < -10 -> "${hit.timingErrorMs.toInt()}ms early"
        else -> "On time"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
            Text(label, color = colors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(errorText, color = colors.onSurfaceVariant, fontSize = 13.sp)
    }
}

// ── Analysis logic ────────────────────────────────────────────────────────────

private data class TendencyAnalysis(
    val title: String,
    val description: String,
    val tip: String,
    val color: Color
)

@Composable
private fun getTendencyAnalysis(
    avgError: Double,
    tendencyToRush: Boolean,
    tendencyToDrag: Boolean
): TendencyAnalysis {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val blue = Color(0xFF3B82F6)

    return when {
        tendencyToRush -> TendencyAnalysis(
            title = "Playing slightly early",
            description = "Your hits landed about ${kotlin.math.abs(avgError.toInt())}ms before the beat on average. " +
                    "This usually happens when you anticipate the click rather than react to it. " +
                    "It does not mean you are playing too fast overall — just catching the beat a fraction early.",
            tip = "Try letting the click land first, then hitting. Practising at a slower speed and consciously waiting a moment longer can help train this.",
            color = blue
        )
        tendencyToDrag -> TendencyAnalysis(
            title = "Playing slightly late",
            description = "Your hits landed about ${avgError.toInt()}ms after the beat on average. " +
                    "A small amount of this (under 30ms) is very common and natural. " +
                    "If it is more noticeable, it can come from being too relaxed or taking too long to react.",
            tip = "Try practising at a slightly faster speed than feels comfortable. This trains quicker reactions. Aim to hit at the very start of each click rather than waiting for it to finish.",
            color = secondaryColor
        )
        kotlin.math.abs(avgError) < 10 -> TendencyAnalysis(
            title = "Excellent timing",
            description = "Your average error was only ${avgError.toInt()}ms — that is right on the beat. " +
                    "This level of consistency is the goal for any drummer and takes real practice to achieve.",
            tip = "To keep improving, try increasing the speed gradually or switching to a harder surface type.",
            color = primaryColor
        )
        else -> TendencyAnalysis(
            title = "Mixed timing",
            description = "Your hits were spread across both early and late, averaging ${avgError.toInt()}ms. " +
                    "This is completely normal when starting out. " +
                    "Focus on listening to each click before hitting rather than trying to guess when it will come.",
            tip = "Start at 60 or 80 BPM and focus on matching each click as closely as possible. Getting accurate at slow speeds makes faster speeds easier.",
            color = errorColor
        )
    }
}

private fun getOverallVerdict(accuracy: Double) = when {
    accuracy >= 90 -> "Outstanding — keep it up"
    accuracy >= 75 -> "Great session"
    accuracy >= 60 -> "Good progress"
    accuracy >= 45 -> "Keep practising"
    else -> "Every session counts"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("EEEE, dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}