package com.d22127059.timekeeperproto.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Practice : Screen("practice")
    object History : Screen("history")
    object Login : Screen("login")
    object Register : Screen("register")
    object Account : Screen("account")
    object Leaderboard : Screen("leaderboard")
    data class SessionDetail(val sessionId: Long) : Screen("session_detail")
}