package com.codeforge.agent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codeforge.agent.ui.chat.ChatScreen

@Composable
fun CodeForgeNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") { ChatScreen() }
    }
}