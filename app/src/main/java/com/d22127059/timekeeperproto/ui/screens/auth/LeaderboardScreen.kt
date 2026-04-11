package com.d22127059.timekeeperproto.ui.screens.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LeaderboardEntry(
    val uid: String,
    val displayName: String,
    val averageAccuracy: Double,
    val totalSessions: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val snap = FirebaseFirestore.getInstance()
                    .collection("leaderboard")
                    .orderBy("averageAccuracy", Query.Direction.DESCENDING)
                    .limit(20)
                    .get().await()

                entries = snap.documents.mapNotNull { doc ->
                    val name = doc.getString("displayName") ?: return@mapNotNull null
                    val acc = doc.getDouble("averageAccuracy") ?: 0.0
                    val sessions = doc.getLong("totalSessions")?.toInt() ?: 0
                    // Only show users with at least 1 session
                    if (sessions == 0) return@mapNotNull null
                    LeaderboardEntry(doc.id, name, acc, sessions)
                }
            } catch (e: Exception) {
                error = "Couldn't load leaderboard - check your connection"
            }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TopAppBar(
            title = { Text("Leaderboard", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
        )

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Could not load the leaderboard",
                            color = colors.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Check your internet connection and try again.",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            entries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No entries yet",
                            color = colors.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Complete a practice session to appear on the leaderboard.",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                // Find current user's position
                val userRank = entries.indexOfFirst { it.uid == currentUid }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top 3 podium
                    if (entries.size >= 3) {
                        item {
                            PodiumRow(
                                first = entries[0],
                                second = entries[1],
                                third = entries.getOrNull(2)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Rest of list starting from position 4
                    itemsIndexed(if (entries.size > 3) entries.drop(3) else entries) { index, entry ->
                        val rank = if (entries.size > 3) index + 4 else index + 1
                        LeaderboardRow(
                            rank = rank,
                            entry = entry,
                            isCurrentUser = entry.uid == currentUid
                        )
                    }

                    // Show user's position if not in top 20
                    if (userRank == -1 && currentUid != null) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "You are not yet ranked — complete more sessions",
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PodiumRow(
    first: LeaderboardEntry,
    second: LeaderboardEntry,
    third: LeaderboardEntry?
) {
    val colors = MaterialTheme.colorScheme

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
            Text(
                "Top Players",
                color = colors.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd place
                PodiumSlot(entry = second, rank = 2, height = 70)
                // 1st place — taller
                PodiumSlot(entry = first, rank = 1, height = 90)
                // 3rd place
                if (third != null) {
                    PodiumSlot(entry = third, rank = 3, height = 55)
                }
            }
        }
    }
}

@Composable
private fun PodiumSlot(
    entry: LeaderboardEntry,
    rank: Int,
    height: Int
) {
    val colors = MaterialTheme.colorScheme
    val podiumColor = when (rank) {
        1 -> Color(0xFFFFD700).copy(alpha = 0.15f)
        2 -> Color(0xFFC0C0C0).copy(alpha = 0.15f)
        else -> Color(0xFFCD7F32).copy(alpha = 0.15f)
    }
    val rankLabel = when (rank) {
        1 -> "1st"
        2 -> "2nd"
        else -> "3rd"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(rankLabel, color = colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.displayName,
            color = colors.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = "${entry.averageAccuracy.toInt()}%",
            color = colors.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(podiumColor)
        )
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val bgColor = if (isCurrentUser) colors.primary.copy(alpha = 0.08f) else colors.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            text = "#$rank",
            color = colors.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )

        // Avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isCurrentUser) colors.primary
                    else colors.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.displayName.first().uppercase(),
                color = if (isCurrentUser) Color.White else colors.onSurfaceVariant,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.displayName,
                    color = colors.onBackground,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Medium
                )
                if (isCurrentUser) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "You",
                            color = colors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "${entry.totalSessions} sessions",
                color = colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Text(
            text = "${entry.averageAccuracy.toInt()}%",
            color = colors.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}