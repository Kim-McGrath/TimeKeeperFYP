package com.d22127059.timekeeperproto.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Practice : Screen("practice")
    object History : Screen("history")
    data class SessionDetail(val sessionId: Long) : Screen("session_detail")
}