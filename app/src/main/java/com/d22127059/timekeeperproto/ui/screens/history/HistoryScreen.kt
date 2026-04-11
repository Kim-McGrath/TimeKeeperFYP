package com.d22127059.timekeeperproto.ui.screens.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.data.local.entities.Session
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<Session>,
    onSessionClick: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onDeleteSession: ((Session) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TopAppBar(
            title = { Text("History", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
        )

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("No sessions yet", color = colors.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Complete a practice session to see your history here.",
                        color = colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Trend graph
                if (sessions.size >= 2) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Accuracy Trend", color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(16.dp))
                                AccuracyGraph(sessions = sessions.takeLast(10), modifier = Modifier.fillMaxWidth().height(100.dp))
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${sessions.size} Sessions", color = colors.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                        if (onDeleteSession != null) {
                            Text("Swipe to delete", color = colors.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                }

                items(sessions, key = { it.id }) { session ->
                    if (onDeleteSession != null) {
                        SwipeToDeleteCard(
                            session = session,
                            onClick = { onSessionClick(session.id) },
                            onDelete = { onDeleteSession(session) }
                        )
                    } else {
                        SessionCard(session = session, onClick = { onSessionClick(session.id) })
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteCard(
    session: Session,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showConfirmDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showConfirmDialog = true
            }
            // Always return false here — we never let the swipe complete automatically. Deletion only happens via dialog confirmation.
            false
        }
    )

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Delete Session?") },
            text = {
                Text(
                    "This session and all its hit data will be permanently deleted. This cannot be undone.",
                    color = colors.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val bgColor by animateColorAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    colors.error else Color.Transparent,
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
        content = {
            SessionCard(session = session, onClick = onClick)
        }
    )
}

@Composable
private fun AccuracyGraph(sessions: List<Session>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (sessions.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        sessions.forEach { session ->
            val fraction = (session.accuracyPercentage / 100.0).toFloat().coerceIn(0.05f, 1f)
            val barColor = when {
                session.accuracyPercentage >= 80 -> colors.primary
                session.accuracyPercentage >= 60 -> colors.secondary
                else -> colors.error
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(barColor.copy(alpha = 0.85f))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("${session.accuracyPercentage.toInt()}", color = colors.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accuracyColor = when {
        session.accuracyPercentage >= 80 -> colors.primary
        session.accuracyPercentage >= 60 -> colors.secondary
        else -> colors.error
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(accuracyColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${session.accuracyPercentage.toInt()}%", color = accuracyColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(session.timestamp), color = colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${session.bpm} BPM · ${formatDuration(session.actualDurationMs)} · ${session.surfaceType.replace("_", " ")}",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                HitBreakdownBar(green = session.greenHits, yellow = session.yellowHits, red = session.redHits, total = session.totalHits)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("›", color = colors.onSurfaceVariant, fontSize = 22.sp)
        }
    }
}

@Composable
private fun HitBreakdownBar(green: Int, yellow: Int, red: Int, total: Int) {
    if (total == 0) return
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
        if (green > 0) Box(modifier = Modifier.weight(green.toFloat()).fillMaxHeight().background(colors.primary))
        if (yellow > 0) Box(modifier = Modifier.weight(yellow.toFloat()).fillMaxHeight().background(colors.secondary))
        if (red > 0) Box(modifier = Modifier.weight(red.toFloat()).fillMaxHeight().background(colors.error))
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}