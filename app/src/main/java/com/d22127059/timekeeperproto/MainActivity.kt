package com.d22127059.timekeeperproto


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.d22127059.timekeeperproto.audio.OnsetDetector
import com.d22127059.timekeeperproto.audio.MetronomeEngine
import com.d22127059.timekeeperproto.data.local.TimeKeeperDatabase
import com.d22127059.timekeeperproto.data.repository.SessionRepository
import com.d22127059.timekeeperproto.ui.screens.practice.PracticeScreen
import com.d22127059.timekeeperproto.ui.screens.practice.PracticeViewModel
import com.d22127059.timekeeperproto.ui.theme.TimeKeeperTheme

// Handles microphone permission and initializes the practice screen.
class MainActivity : ComponentActivity() {

    private lateinit var practiceViewModel: PracticeViewModel
    private var hasMicrophonePermission = mutableStateOf(false)

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicrophonePermission.value = isGranted
        if (!isGranted) {
            // Handle permission denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check microphone permission
        hasMicrophonePermission.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Initialize dependencies
        val database = TimeKeeperDatabase.getDatabase(applicationContext)
        val repository = SessionRepository(
            sessionDao = database.sessionDao(),
            hitDao = database.hitDao()
        )
        val onsetDetector = OnsetDetector()
        val metronomeEngine = MetronomeEngine()

        // Create ViewModel (in production, use ViewModelProvider and dependency injection)
        practiceViewModel = PracticeViewModel(
            onsetDetector = onsetDetector,
            metronomeEngine = metronomeEngine,
            repository = repository
        )

        setContent {
            TimeKeeperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission by remember { hasMicrophonePermission }

                    if (hasPermission) {
                        PracticeScreen(viewModel = practiceViewModel)
                    } else {
                        // Show permission request UI
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