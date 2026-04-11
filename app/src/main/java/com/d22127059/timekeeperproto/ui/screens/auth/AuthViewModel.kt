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

    // Update leaderboard and user doc after a session
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

                // Recalculate user stats from all their sessions
                val sessionsSnap = db.collection("sessions")
                    .whereEqualTo("uid", user.uid)
                    .get().await()

                val allAccuracies = sessionsSnap.documents.mapNotNull {
                    it.getDouble("accuracyPercentage")
                }
                val newAvg = if (allAccuracies.isEmpty()) 0.0 else allAccuracies.average()
                val newTotal = allAccuracies.size

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
                // Silently fail — local data is already saved to Room
            }
        }
    }

    private fun friendlyError(message: String?): String = when {
        message == null -> "Something went wrong"
        message.contains("no user record") -> "No account found with that email"
        message.contains("password is invalid") -> "Incorrect password"
        message.contains("email address is already") -> "An account with this email already exists"
        message.contains("badly formatted") -> "Please enter a valid email address"
        message.contains("network") -> "Network error — check your connection"
        else -> "Something went wrong. Please try again"
    }
}