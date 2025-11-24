package com.d22127059.timekeeperproto


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.d22127059.timekeeperproto.audio.OnsetDetector
import com.d22127059.timekeeperproto.data.local.TimeKeeperDatabase
import com.d22127059.timekeeperproto.data.repository.SessionRepository
import com.d22127059.timekeeperproto.ui.screens.practice.PracticeScreen
import com.d22127059.timekeeperproto.ui.screens.practice.PracticeViewModel
import com.d22127059.timekeeperproto.ui.theme.TimeKeeperTheme

/**
 * Main activity for TimeKeeper app.
 * Handles microphone permission and initializes the practice screen.
 */
class MainActivity : ComponentActivity() {

    private lateinit var practiceViewModel: PracticeViewModel
    private var hasMicrophonePermission = false

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicrophonePermission = isGranted
        if (!isGranted) {
            // Handle permission denied - show explanation or close app
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request microphone permission
        checkMicrophonePermission()

        // Initialize dependencies
        val database = TimeKeeperDatabase.getDatabase(applicationContext)
        val repository = SessionRepository(
            sessionDao = database.sessionDao(),
            hitDao = database.hitDao()
        )
        val onsetDetector = OnsetDetector()

        // Create ViewModel (in production, use ViewModelProvider and dependency injection)
        practiceViewModel = PracticeViewModel(
            onsetDetector = onsetDetector,
            repository = repository
        )

        setContent {
            TimeKeeperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasMicrophonePermission) {
                        PracticeScreen(viewModel = practiceViewModel)
                    } else {
                        // Show permission request UI
                    }
                }
            }
        }
    }

    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasMicrophonePermission = true
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}