package com.d22127059.timekeeperproto.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val colors = MaterialTheme.colorScheme

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading = authState is AuthState.Loading

    // Clear error when user starts typing
    LaunchedEffect(email, password) {
        if (authState is AuthState.Error) authViewModel.clearError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            Text(
                text = "TimeKeeper",
                color = colors.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Sign in to track your progress",
                color = colors.onSurfaceVariant,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Error banner
            if (authState is AuthState.Error) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = (authState as AuthState.Error).message,
                        color = colors.error,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            color = colors.primary,
                            fontSize = 13.sp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { authViewModel.login(email, password) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Don't have an account?",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp
                )
                TextButton(onClick = onNavigateToRegister) {
                    Text("Create one", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurfaceVariant)
            ) {
                Text("Continue as Guest", fontSize = 15.sp)
            }

            Text(
                text = "Guest mode saves sessions locally only.\nSign in to access the leaderboard.",
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val colors = MaterialTheme.colorScheme

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading = authState is AuthState.Loading

    LaunchedEffect(email, password, displayName) {
        if (authState is AuthState.Error) authViewModel.clearError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            Text(
                text = "Create Account",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Join the leaderboard and track your progress",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (authState is AuthState.Error) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = (authState as AuthState.Error).message,
                        color = colors.error,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            color = colors.primary,
                            fontSize = 13.sp
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Minimum 6 characters",
                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { authViewModel.register(email, password, displayName) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Already have an account?", color = colors.onSurfaceVariant, fontSize = 14.sp)
                TextButton(onClick = onNavigateToLogin) {
                    Text("Sign in", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    cursorColor = MaterialTheme.colorScheme.primary
)