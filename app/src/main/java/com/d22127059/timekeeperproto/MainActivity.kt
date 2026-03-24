package com.d22127059.timekeeperproto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.d22127059.timekeeperproto.ui.screens.practice.PracticeUiState
import com.d22127059.timekeeperproto.ui.screens.sessiondetail.SessionDetailScreen
import com.d22127059.timekeeperproto.ui.theme.TimeKeeperTheme
import com.d22127059.timekeeperproto.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var practiceViewModel: PracticeViewModel
    private lateinit var themeViewModel: ThemeViewModel
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
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

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
        themeViewModel = ThemeViewModel()

        setContent {
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()
            val hasPermission by remember { hasMicrophonePermission }

            TimeKeeperTheme(isDarkTheme = isDarkMode) {
                if (hasPermission) {
                    TimeKeeperApp(
                        practiceViewModel = practiceViewModel,
                        themeViewModel = themeViewModel,
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
    themeViewModel: ThemeViewModel,
    repository: SessionRepository,
    currentSurfaceType: SurfaceType,
    onSurfaceTypeChanged: (SurfaceType) -> Unit
) {
    // Navigation back stack — Home is always the root
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = backStack.last()

    val scope = rememberCoroutineScope()
    val isDarkMode by themeViewModel.isDarkMode.collectAsState()
    val practiceUiState by practiceViewModel.uiState.collectAsState()

    val sessions by repository.getAllSessions().collectAsState(initial = emptyList())
    var userStats by remember { mutableStateOf<com.d22127059.timekeeperproto.data.repository.UserStatistics?>(null) }

    LaunchedEffect(sessions) {
        scope.launch { userStats = repository.getUserStatistics() }
    }

    // Navigate to a new screen, pushing onto the stack
    fun navigateTo(screen: Screen) {
        backStack.add(screen)
    }

    // Pop back to previous screen; if at root, do nothing (let system handle exit)
    fun navigateBack() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    // Handle device back button
    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    val colors = MaterialTheme.colorScheme

    val hideNavBar = (currentScreen == Screen.Practice &&
            (practiceUiState is PracticeUiState.Active ||
                    practiceUiState is PracticeUiState.Countdown ||
                    practiceUiState is PracticeUiState.Completed)) ||
            currentScreen is Screen.SessionDetail

    Scaffold(
        bottomBar = {
            if (!hideNavBar) {
                NavigationBar(
                    containerColor = colors.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 12.sp) },
                        selected = currentScreen == Screen.Home,
                        onClick = {
                            // Pop to root rather than adding another Home
                            while (backStack.size > 1) backStack.removeLastOrNull()
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            indicatorColor = colors.primary.copy(alpha = 0.12f),
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Practice") },
                        label = { Text("Practice", fontSize = 12.sp) },
                        selected = currentScreen == Screen.Practice,
                        onClick = { navigateTo(Screen.Practice) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            indicatorColor = colors.primary.copy(alpha = 0.12f),
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History", fontSize = 12.sp) },
                        selected = currentScreen == Screen.History,
                        onClick = { navigateTo(Screen.History) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            indicatorColor = colors.primary.copy(alpha = 0.12f),
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val screen = currentScreen) {
                Screen.Home -> HomeScreen(
                    userStats = userStats,
                    sessions = sessions,
                    isDarkMode = isDarkMode,
                    onToggleTheme = { themeViewModel.toggleTheme() },
                    onNavigateToPractice = { navigateTo(Screen.Practice) },
                    onNavigateToHistory = { navigateTo(Screen.History) },
                    onSessionClick = { sessionId -> navigateTo(Screen.SessionDetail(sessionId)) }
                )
                Screen.Practice -> PracticeScreen(
                    viewModel = practiceViewModel,
                    currentSurfaceType = currentSurfaceType,
                    onSurfaceTypeChanged = onSurfaceTypeChanged,
                    onNavigateBack = { navigateBack() }
                )
                Screen.History -> HistoryScreen(
                    sessions = sessions,
                    onSessionClick = { sessionId -> navigateTo(Screen.SessionDetail(sessionId)) },
                    onNavigateBack = { navigateBack() }
                )
                is Screen.SessionDetail -> SessionDetailScreen(
                    sessionId = screen.sessionId,
                    repository = repository,
                    onNavigateBack = { navigateBack() }
                )
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🎙️", fontSize = 64.sp)
            Text(
                text = "Microphone Access Needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )
            Text(
                text = "TimeKeeper uses your microphone to detect drum hits and analyse your timing accuracy.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Grant Permission", fontWeight = FontWeight.Bold)
            }
        }
    }
}