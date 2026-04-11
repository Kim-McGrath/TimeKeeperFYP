package com.d22127059.timekeeperproto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.EmojiEvents
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
import androidx.core.content.ContextCompat
import com.d22127059.timekeeperproto.audio.OnsetDetector
import com.d22127059.timekeeperproto.audio.MetronomeEngine
import com.d22127059.timekeeperproto.audio.SurfaceType
import com.d22127059.timekeeperproto.data.local.TimeKeeperDatabase
import com.d22127059.timekeeperproto.data.repository.SessionRepository
import com.d22127059.timekeeperproto.navigation.Screen
import com.d22127059.timekeeperproto.ui.screens.auth.AccountScreen
import com.d22127059.timekeeperproto.ui.screens.auth.AuthViewModel
import com.d22127059.timekeeperproto.ui.screens.auth.AuthState
import com.d22127059.timekeeperproto.ui.screens.auth.LoginScreen
import com.d22127059.timekeeperproto.ui.screens.auth.RegisterScreen
import com.d22127059.timekeeperproto.ui.screens.home.HomeScreen
import com.d22127059.timekeeperproto.ui.screens.history.HistoryScreen
import com.d22127059.timekeeperproto.ui.screens.leaderboard.LeaderboardScreen
import com.d22127059.timekeeperproto.ui.screens.onboarding.OnboardingScreen
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
    private lateinit var authViewModel: AuthViewModel
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
        authViewModel = AuthViewModel()

        // Check if onboarding has been shown before
        val prefs = getSharedPreferences("timekeeper_prefs", Context.MODE_PRIVATE)
        val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)

        setContent {
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()
            val hasPermission by remember { hasMicrophonePermission }

            TimeKeeperTheme(isDarkTheme = isDarkMode) {
                if (hasPermission) {
                    TimeKeeperApp(
                        practiceViewModel = practiceViewModel,
                        themeViewModel = themeViewModel,
                        authViewModel = authViewModel,
                        repository = repository,
                        showOnboarding = !hasSeenOnboarding,
                        onOnboardingComplete = {
                            prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                        },
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
    authViewModel: AuthViewModel,
    repository: SessionRepository,
    showOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    currentSurfaceType: SurfaceType,
    onSurfaceTypeChanged: (SurfaceType) -> Unit
) {
    val initialScreen: Screen = if (showOnboarding) Screen.Onboarding else Screen.Home
    val backStack = remember { mutableStateListOf<Screen>(initialScreen) }
    val currentScreen = backStack.last()

    val scope = rememberCoroutineScope()
    val isDarkMode by themeViewModel.isDarkMode.collectAsState()
    val practiceUiState by practiceViewModel.uiState.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val authState by authViewModel.authState.collectAsState()

    val sessions by repository.getAllSessions().collectAsState(initial = emptyList())
    var userStats by remember { mutableStateOf<com.d22127059.timekeeperproto.data.repository.UserStatistics?>(null) }

    LaunchedEffect(sessions) {
        scope.launch { userStats = repository.getUserStatistics() }
    }

    // Sync completed sessions to Firestore when logged in
    LaunchedEffect(practiceUiState) {
        if (practiceUiState is PracticeUiState.Completed && currentUser != null) {
            val stats = (practiceUiState as PracticeUiState.Completed).stats
            val lastSession = sessions.firstOrNull()
            if (lastSession != null) {
                authViewModel.syncSessionToFirestore(
                    accuracyPercentage = stats.accuracyPercentage,
                    bpm = lastSession.bpm,
                    totalHits = stats.totalHits,
                    greenHits = stats.greenHits,
                    yellowHits = stats.yellowHits,
                    redHits = stats.redHits,
                    durationMs = lastSession.actualDurationMs,
                    surfaceType = lastSession.surfaceType
                )
            }
        }
    }

    // Navigate after successful auth
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            while (backStack.lastOrNull() == Screen.Login || backStack.lastOrNull() == Screen.Register) {
                backStack.removeLastOrNull()
            }
            backStack.add(Screen.Account)
        }
    }

    fun navigateTo(screen: Screen) { backStack.add(screen) }
    fun navigateBack() { if (backStack.size > 1) backStack.removeLastOrNull() }

    BackHandler(enabled = backStack.size > 1) { navigateBack() }

    val colors = MaterialTheme.colorScheme

    val isFullScreen = currentScreen == Screen.Onboarding ||
            (currentScreen == Screen.Practice &&
                    (practiceUiState is PracticeUiState.Active ||
                            practiceUiState is PracticeUiState.Countdown ||
                            practiceUiState is PracticeUiState.Completed)) ||
            currentScreen is Screen.SessionDetail ||
            currentScreen == Screen.Login ||
            currentScreen == Screen.Register

    Scaffold(
        bottomBar = {
            if (!isFullScreen) {
                NavigationBar(
                    containerColor = colors.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(22.dp)) },
                        label = { Text("Home", fontSize = 11.sp) },
                        selected = currentScreen == Screen.Home,
                        onClick = { while (backStack.size > 1) backStack.removeLastOrNull() },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(22.dp)) },
                        label = { Text("History", fontSize = 11.sp) },
                        selected = currentScreen == Screen.History,
                        onClick = { navigateTo(Screen.History) },
                        colors = navItemColors()
                    )
                    // Centre practice pill button
                    NavigationBarItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(width = 52.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = "Practice", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        },
                        label = { Text("Practice", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == Screen.Practice,
                        onClick = { navigateTo(Screen.Practice) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = Color.White,
                            selectedTextColor = colors.primary,
                            unselectedTextColor = colors.primary,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Leaderboard", modifier = Modifier.size(22.dp)) },
                        label = { Text("Ranks", fontSize = 11.sp) },
                        selected = currentScreen == Screen.Leaderboard,
                        onClick = {
                            if (currentUser != null) navigateTo(Screen.Leaderboard)
                            else navigateTo(Screen.Login)
                        },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Account",
                                modifier = Modifier.size(22.dp),
                                tint = if (currentUser != null) colors.primary else colors.onSurfaceVariant
                            )
                        },
                        label = { Text("Account", fontSize = 11.sp) },
                        selected = currentScreen == Screen.Account,
                        onClick = {
                            if (currentUser != null) navigateTo(Screen.Account)
                            else navigateTo(Screen.Login)
                        },
                        colors = navItemColors()
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val screen = currentScreen) {
                Screen.Onboarding -> OnboardingScreen(
                    onComplete = {
                        onOnboardingComplete()
                        // Replace onboarding with home in the stack
                        backStack.clear()
                        backStack.add(Screen.Home)
                    }
                )
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
                    onNavigateBack = { navigateBack() },
                    onDeleteSession = { session ->
                        scope.launch { repository.deleteSession(session) }
                    }
                )
                is Screen.SessionDetail -> SessionDetailScreen(
                    sessionId = screen.sessionId,
                    repository = repository,
                    onNavigateBack = { navigateBack() }
                )
                Screen.Login -> LoginScreen(
                    authViewModel = authViewModel,
                    onNavigateToRegister = { navigateTo(Screen.Register) },
                    onContinueAsGuest = { navigateBack() }
                )
                Screen.Register -> RegisterScreen(
                    authViewModel = authViewModel,
                    onNavigateToLogin = { navigateBack() }
                )
                Screen.Account -> {
                    val user = currentUser
                    if (user != null) {
                        AccountScreen(
                            authViewModel = authViewModel,
                            currentUser = user,
                            localStats = userStats,
                            onNavigateBack = { navigateBack() },
                            onLogout = { while (backStack.size > 1) backStack.removeLastOrNull() }
                        )
                    } else {
                        LoginScreen(
                            authViewModel = authViewModel,
                            onNavigateToRegister = { navigateTo(Screen.Register) },
                            onContinueAsGuest = { navigateBack() }
                        )
                    }
                }
                Screen.Leaderboard -> {
                    if (currentUser != null) {
                        LeaderboardScreen(onNavigateBack = { navigateBack() })
                    } else {
                        LoginScreen(
                            authViewModel = authViewModel,
                            onNavigateToRegister = { navigateTo(Screen.Register) },
                            onContinueAsGuest = { navigateBack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Microphone access needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "TimeKeeper uses your microphone to detect audio and analyse your timing accuracy. Without this, the app cannot work.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Allow microphone access", fontWeight = FontWeight.Bold)
            }
        }
    }
}