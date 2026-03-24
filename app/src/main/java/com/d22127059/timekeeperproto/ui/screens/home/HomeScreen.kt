package com.d22127059.timekeeperproto.ui.screens.home

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                        text = "TimeKeeper",
                        color = colors.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Rhythm Training",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceVariant)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Hero card — last session or welcome
            if (recentSessions.isNotEmpty()) {
                val last = recentSessions.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = 0.85f),
                                    colors.primary.copy(alpha = 0.5f)
                                )
                            )
                        )
                        .clickable { onSessionClick(last.id) }
                        .padding(28.dp)
                ) {
                    Column {
                        Text(
                            text = "Last Session",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${last.accuracyPercentage.toInt()}",
                                color = Color.White,
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 72.sp
                            )
                            Text(
                                text = "%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "›",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 32.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Text(
                            text = getAccuracyMessage(last.accuracyPercentage),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${last.bpm} BPM · ${formatDate(last.timestamp)}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceVariant)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🥁", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Welcome to TimeKeeper",
                            color = colors.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start your first session to track your timing accuracy and improve your rhythm.",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatChip("Sessions", userStats.totalSessions.toString(), Modifier.weight(1f))
                    StatChip("Avg Accuracy", "${userStats.averageAccuracy.toInt()}%", Modifier.weight(1f))
                    StatChip("Rating", getRating(userStats.averageAccuracy), Modifier.weight(1f))
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
                Text(
                    text = "Start Practice",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }

            // Recent sessions
            if (recentSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Sessions",
                        color = colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (sessions.size > 3) {
                        TextButton(onClick = onNavigateToHistory) {
                            Text("See all", color = colors.primary, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentSessions.forEach { session ->
                        RecentSessionRow(
                            session = session,
                            onClick = { onSessionClick(session.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accuracyColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${session.accuracyPercentage.toInt()}%",
                    color = accuracyColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = "${session.bpm} BPM · ${session.surfaceType.replace("_", " ")}",
                    color = colors.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDate(session.timestamp),
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Text("›", color = colors.onSurfaceVariant, fontSize = 20.sp)
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = colors.onSurfaceVariant, fontSize = 11.sp)
    }
}

private fun getAccuracyMessage(accuracy: Double) = when {
    accuracy >= 90 -> "Excellent timing — keep it up!"
    accuracy >= 75 -> "Good session — room to push higher"
    accuracy >= 60 -> "Decent — focus on consistency"
    else -> "Keep practising — it gets easier"
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