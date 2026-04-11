package com.d22127059.timekeeperproto.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d22127059.timekeeperproto.data.repository.UserStatistics
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    authViewModel: AuthViewModel,
    currentUser: FirebaseUser,
    localStats: UserStatistics?,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf("") }
    var cloudSessions by remember { mutableStateOf(0) }
    var cloudAvgAccuracy by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser.uid) {
        scope.launch {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.uid)
                    .get().await()
                displayName = doc.getString("displayName") ?: currentUser.email ?: "User"
                cloudSessions = doc.getLong("totalSessions")?.toInt() ?: 0
                cloudAvgAccuracy = doc.getDouble("averageAccuracy") ?: 0.0
            } catch (e: Exception) {
                displayName = currentUser.email ?: "User"
            }
            isLoading = false
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out? Your local sessions will remain on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    authViewModel.logout()
                    onLogout()
                }) {
                    Text("Sign Out", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TopAppBar(
            title = { Text("Account", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.onBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.primary, colors.primary.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (displayName.isNotEmpty()) displayName.first().uppercase() else "?",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = displayName,
                    color = colors.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentUser.email ?: "",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Cloud stats card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Online Statistics",
                        color = colors.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AccountStat(
                            value = cloudSessions.toString(),
                            label = "Sessions",
                            color = colors.primary
                        )
                        AccountStat(
                            value = "${cloudAvgAccuracy.toInt()}%",
                            label = "Avg Accuracy",
                            color = colors.primary
                        )
                        AccountStat(
                            value = getRating(cloudAvgAccuracy),
                            label = "Rating",
                            color = colors.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Local stats card
            if (localStats != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Device Statistics",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AccountStat(
                                value = localStats.totalSessions.toString(),
                                label = "Sessions",
                                color = colors.onSurfaceVariant
                            )
                            AccountStat(
                                value = "${localStats.averageAccuracy.toInt()}%",
                                label = "Avg Accuracy",
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Sign out button
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
            ) {
                Text("Sign Out", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AccountStat(value: String, label: String, color: Color) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = colors.onSurfaceVariant, fontSize = 12.sp)
    }
}

private fun getRating(accuracy: Double) = when {
    accuracy >= 90 -> "S"
    accuracy >= 75 -> "A"
    accuracy >= 60 -> "B"
    accuracy >= 45 -> "C"
    else -> "D"
}