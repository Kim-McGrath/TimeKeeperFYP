package com.d22127059.timekeeperproto.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.data.local.entities.Session
import com.d22127059.timekeeperproto.data.repository.UserStatistics
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    userStats: UserStatistics?,
    sessions: List<Session>,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToPractice: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onSessionClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val recentSessions = sessions.take(3)
    val streak = calculateStreak(sessions)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TimeKeeper",
                        color = colors.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text("Rhythm Training", color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Streak badge
                    if (streak > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFF6B35).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "$streak day streak",
                                    color = Color(0xFFFF6B35),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Hero accuracy card
            if (recentSessions.isNotEmpty()) {
                val last = recentSessions.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.primary.copy(alpha = 0.85f), colors.primary.copy(alpha = 0.5f))
                            )
                        )
                        .clickable { onSessionClick(last.id) }
                        .padding(28.dp)
                ) {
                    Column {
                        Text("Last Session", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${last.accuracyPercentage.toInt()}", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp)
                            Text("%", color = Color.White.copy(alpha = 0.8f), fontSize = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
                            Spacer(modifier = Modifier.weight(1f))
                            Text("›", color = Color.White.copy(alpha = 0.7f), fontSize = 32.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Text(getAccuracyMessage(last.accuracyPercentage), color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${last.bpm} BPM · ${formatDate(last.timestamp)}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceVariant).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Welcome to TimeKeeper", color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Start your first session to track your timing accuracy.",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stats row
            if (userStats != null && userStats.totalSessions > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip("Sessions", userStats.totalSessions.toString(), Modifier.weight(1f))
                    StatChip("Avg Accuracy", "${userStats.averageAccuracy.toInt()}%", Modifier.weight(1f))
                    StatChip("Rating", getRating(userStats.averageAccuracy), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Progress chart — shown when 3+ sessions exist
            if (sessions.size >= 3) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Progress", color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Last ${minOf(sessions.size, 10)} sessions", color = colors.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        AccuracyLineChart(
                            sessions = sessions.take(10).reversed(),
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Start practice button
            Button(
                onClick = onNavigateToPractice,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Start Practice", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            }

            // Recent sessions
            if (recentSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Sessions", color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (sessions.size > 3) {
                        TextButton(onClick = onNavigateToHistory) {
                            Text("See all", color = colors.primary, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentSessions.forEach { session ->
                        RecentSessionRow(session = session, onClick = { onSessionClick(session.id) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AccuracyLineChart(sessions: List<Session>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (sessions.size < 2) return

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "chartAnim"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padding = 8.dp.toPx()
        val minAcc = sessions.minOf { it.accuracyPercentage }.toFloat().coerceAtMost(50f)
        val maxAcc = sessions.maxOf { it.accuracyPercentage }.toFloat().coerceAtLeast(minAcc + 10f)
        val range = maxAcc - minAcc

        fun xAt(i: Int) = padding + (i.toFloat() / (sessions.size - 1)) * (w - padding * 2)
        fun yAt(acc: Double) = h - padding - ((acc.toFloat() - minAcc) / range) * (h - padding * 2)

        // Draw grid line at 80%
        val y80 = yAt(80.0)
        if (y80 in 0f..h) {
            drawLine(
                color = colors.primary.copy(alpha = 0.15f),
                start = Offset(0f, y80),
                end = Offset(w, y80),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw filled area under the line
        val fillPath = Path()
        val animatedCount = (sessions.size * animProgress).toInt().coerceAtLeast(2)
        fillPath.moveTo(xAt(0), yAt(sessions[0].accuracyPercentage))
        for (i in 1 until animatedCount) {
            fillPath.lineTo(xAt(i), yAt(sessions[i].accuracyPercentage))
        }
        fillPath.lineTo(xAt(animatedCount - 1), h)
        fillPath.lineTo(xAt(0), h)
        fillPath.close()
        drawPath(fillPath, brush = Brush.verticalGradient(
            listOf(colors.primary.copy(alpha = 0.3f), colors.primary.copy(alpha = 0.0f))
        ))

        // Draw line
        val linePath = Path()
        linePath.moveTo(xAt(0), yAt(sessions[0].accuracyPercentage))
        for (i in 1 until animatedCount) {
            linePath.lineTo(xAt(i), yAt(sessions[i].accuracyPercentage))
        }
        drawPath(linePath, color = colors.primary, style = Stroke(width = 2.5.dp.toPx()))

        // Draw dots
        for (i in 0 until animatedCount) {
            drawCircle(color = colors.primary, radius = 4.dp.toPx(), center = Offset(xAt(i), yAt(sessions[i].accuracyPercentage)))
            drawCircle(color = colors.surface, radius = 2.dp.toPx(), center = Offset(xAt(i), yAt(sessions[i].accuracyPercentage)))
        }
    }
}

@Composable
private fun RecentSessionRow(session: Session, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accuracyColor = when {
        session.accuracyPercentage >= 80 -> colors.primary
        session.accuracyPercentage >= 60 -> colors.secondary
        else -> colors.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accuracyColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${session.accuracyPercentage.toInt()}%", color = accuracyColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("${session.bpm} BPM · ${session.surfaceType.replace("_", " ")}", color = colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(formatDate(session.timestamp), color = colors.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Text("›", color = colors.onSurfaceVariant, fontSize = 20.sp)
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant).padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = colors.onSurfaceVariant, fontSize = 11.sp)
    }
}

// Returns the number of consecutive days (ending today) with at least one session
fun calculateStreak(sessions: List<Session>): Int {
    if (sessions.isEmpty()) return 0
    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)

    // Get unique days that have sessions
    val sessionDays = sessions.map { session ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = session.timestamp
        Pair(cal.get(Calendar.YEAR), cal.get(Calendar.DAY_OF_YEAR))
    }.toSet()

    // Check if there's a session today or yesterday (grace period)
    val todayPair = Pair(year, today)
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayPair = Pair(cal.get(Calendar.YEAR), cal.get(Calendar.DAY_OF_YEAR))

    if (todayPair !in sessionDays && yesterdayPair !in sessionDays) return 0

    // Count backwards
    var streak = 0
    val checkCal = Calendar.getInstance()
    if (todayPair !in sessionDays) checkCal.add(Calendar.DAY_OF_YEAR, -1) // start from yesterday

    while (true) {
        val checkPair = Pair(checkCal.get(Calendar.YEAR), checkCal.get(Calendar.DAY_OF_YEAR))
        if (checkPair in sessionDays) {
            streak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }
    return streak
}

private fun getAccuracyMessage(accuracy: Double) = when {
    accuracy >= 90 -> "Amazing — keep it up!"
    accuracy >= 75 -> "Good session!"
    accuracy >= 60 -> "Getting there!"
    else           -> "Keep practising!"
}

private fun getRating(accuracy: Double) = when {
    accuracy >= 90 -> "S"
    accuracy >= 75 -> "A"
    accuracy >= 60 -> "B"
    accuracy >= 45 -> "C"
    else -> "D"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(timestamp))