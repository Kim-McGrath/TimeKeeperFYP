package com.d22127059.timekeeperproto

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.unit.dp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.d22127059.timekeeperproto.audio.OnsetDetector
import com.d22127059.timekeeperproto.audio.MetronomeEngine
import com.d22127059.timekeeperproto.audio.SurfaceType
import com.d22127059.timekeeperproto.data.local.TimeKeeperDatabase
import com.d22127059.timekeeperproto.data.repository.SessionRepository
import com.d22127059.timekeeperproto.navigation.Screen
import com.d22127059.timekeeperproto.ui.screens.home.HomeScreen
import com.d22127059.timekeeperproto.ui.screens.history.HistoryScreen
import com.d22127059.timekeeperproto.ui.screens.practice.PracticeScreen
import com.d22127059.timekeeperproto.ui.screens.practice.PracticeViewModel
import com.d22127059.timekeeperproto.ui.theme.TimeKeeperTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var practiceViewModel: PracticeViewModel
    private lateinit var repository: SessionRepository
    private var hasMicrophonePermission = mutableStateOf(false)
    private var selectedSurfaceType = mutableStateOf(SurfaceType.DRUM_KIT)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicrophonePermission.value = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasMicrophonePermission.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Initialize dependencies
        val database = TimeKeeperDatabase.getDatabase(applicationContext)
        repository = SessionRepository(
            sessionDao = database.sessionDao(),
            hitDao = database.hitDao()
        )
        val onsetDetector = OnsetDetector(initialSurfaceType = selectedSurfaceType.value)
        val metronomeEngine = MetronomeEngine(context = applicationContext)

        practiceViewModel = PracticeViewModel(
            onsetDetector = onsetDetector,
            metronomeEngine = metronomeEngine,
            repository = repository
        )

        setContent {
            TimeKeeperTheme {
                val hasPermission by remember { hasMicrophonePermission }

                if (hasPermission) {
                    TimeKeeperApp(
                        practiceViewModel = practiceViewModel,
                        repository = repository,
                        currentSurfaceType = selectedSurfaceType.value,
                        onSurfaceTypeChanged = { newType ->
                            selectedSurfaceType.value = newType
                            practiceViewModel.updateSurfaceType(newType)
                        }
                    )
                } else {
                    PermissionRequestScreen(
                        onRequestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeKeeperApp(
    practiceViewModel: PracticeViewModel,
    repository: SessionRepository,
    currentSurfaceType: SurfaceType,
    onSurfaceTypeChanged: (SurfaceType) -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()

    // Collect sessions and stats
    val sessions by repository.getAllSessions().collectAsState(initial = emptyList())
    var userStats by remember { mutableStateOf<com.d22127059.timekeeperproto.data.repository.UserStatistics?>(null) }
    var lastSessionAccuracy by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(sessions) {
        scope.launch {
            userStats = repository.getUserStatistics()
            lastSessionAccuracy = sessions.firstOrNull()?.accuracyPercentage
        }
    }

    Scaffold(
        bottomBar = {
            if (currentScreen != Screen.Practice) {
                NavigationBar(
                    containerColor = Color(0xFF374151)
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentScreen == Screen.Home,
                        onClick = { currentScreen = Screen.Home },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF3B82F6),
                            selectedTextColor = Color(0xFF3B82F6),
                            indicatorColor = Color(0xFF1F2937),
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Practice") },
                        label = { Text("Practice") },
                        selected = currentScreen == Screen.Practice,
                        onClick = { currentScreen = Screen.Practice },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF3B82F6),
                            selectedTextColor = Color(0xFF3B82F6),
                            indicatorColor = Color(0xFF1F2937),
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") },
                        selected = currentScreen == Screen.History,
                        onClick = { currentScreen = Screen.History },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF3B82F6),
                            selectedTextColor = Color(0xFF3B82F6),
                            indicatorColor = Color(0xFF1F2937),
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.Home -> {
                    HomeScreen(
                        userStats = userStats,
                        lastSessionAccuracy = lastSessionAccuracy,
                        onNavigateToPractice = { currentScreen = Screen.Practice },
                        onNavigateToHistory = { currentScreen = Screen.History }
                    )
                }
                Screen.Practice -> {
                    PracticeScreen(
                        viewModel = practiceViewModel,
                        currentSurfaceType = currentSurfaceType,
                        onSurfaceTypeChanged = onSurfaceTypeChanged,
                        onNavigateBack = { currentScreen = Screen.Home }
                    )
                }
                Screen.History -> {
                    HistoryScreen(
                        sessions = sessions,
                        onNavigateBack = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Microphone Permission Required",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "This app needs microphone access to detect drum hits",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}