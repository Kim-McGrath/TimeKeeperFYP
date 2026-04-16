package com.d22127059.timekeeperproto.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    // Keep currentUser in sync with Firebase's auth state
    // This handles external sign-out events (e.g. token expiry) without requiring an explicit logout call
    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                result.user?.let { _authState.value = AuthState.Success(it) }
                    ?: run { _authState.value = AuthState.Error("Login failed") }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(friendlyError(e.message))
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
            _authState.value = AuthState.Error("Please fill in all fields")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                result.user?.let { user ->
                    // Create user document in Firestore
                    db.collection("users").document(user.uid).set(
                        mapOf(
                            "displayName" to displayName.trim(),
                            "email" to email.trim(),
                            "totalSessions" to 0,
                            "averageAccuracy" to 0.0,
                            "createdAt" to System.currentTimeMillis()
                        )
                    ).await()
                    // Create leaderboard entry
                    db.collection("leaderboard").document(user.uid).set(
                        mapOf(
                            "displayName" to displayName.trim(),
                            "averageAccuracy" to 0.0,
                            "totalSessions" to 0,
                            "updatedAt" to System.currentTimeMillis()
                        )
                    ).await()
                    _authState.value = AuthState.Success(user)
                } ?: run { _authState.value = AuthState.Error("Registration failed") }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(friendlyError(e.message))
            }
        }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    fun clearError() {
        _authState.value = AuthState.Idle
    }

    // Saves a completed session to Firestore and recalculates the user's running average accuracy for the leaderboard
    // Called automatically after each session if the user is logged in
    // Failures are silently ignored - local data is always written to Room first and is not dependent on this succeeding
    fun syncSessionToFirestore(
        accuracyPercentage: Double,
        bpm: Int,
        totalHits: Int,
        greenHits: Int,
        yellowHits: Int,
        redHits: Int,
        durationMs: Long,
        surfaceType: String
    ) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                // Save session document
                db.collection("sessions").add(
                    mapOf(
                        "uid" to user.uid,
                        "accuracyPercentage" to accuracyPercentage,
                        "bpm" to bpm,
                        "totalHits" to totalHits,
                        "greenHits" to greenHits,
                        "yellowHits" to yellowHits,
                        "redHits" to redHits,
                        "durationMs" to durationMs,
                        "surfaceType" to surfaceType,
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()

                // Incrementally recalculate the running average using the stored totals
                // This avoids reading the entire sessions collection on every sync
                // one user doc read instead of potentially hundreds of session reads
                val userDoc = db.collection("users").document(user.uid).get().await()
                val currentTotal = userDoc.getLong("totalSessions")?.toInt() ?: 0
                val currentAvg = userDoc.getDouble("averageAccuracy") ?: 0.0

                val newTotal = currentTotal + 1
                val newAvg = ((currentAvg * currentTotal) + accuracyPercentage) / newTotal

                // Update user doc
                db.collection("users").document(user.uid).update(
                    mapOf(
                        "totalSessions" to newTotal,
                        "averageAccuracy" to newAvg
                    )
                ).await()

                // Update leaderboard entry
                db.collection("leaderboard").document(user.uid).update(
                    mapOf(
                        "averageAccuracy" to newAvg,
                        "totalSessions" to newTotal,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()

            } catch (e: Exception) {
                // Silently fail - local data is already saved to Room
            }
        }
    }

    private fun friendlyError(message: String?): String = when {
        message == null -> "Something went wrong"
        message.contains("no user record") -> "No account found with that email"
        message.contains("password is invalid") -> "Incorrect password"
        message.contains("email address is already") -> "An account with this email already exists"
        message.contains("badly formatted") -> "Please enter a valid email address"
        message.contains("network") -> "Network error - check your connection"
        else -> "Something went wrong. Please try again"
    }
}