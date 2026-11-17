package com.codeforge.agent.ui

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.codeforge.agent.ui.chat.ChatScreen
import com.codeforge.agent.ui.terminal.TerminalScreen

@Composable
fun AppShell() {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Chat", "Terminal")
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, label = { Text(t) }, icon = {})
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.padding(padding)) {
            when (tab) {
                0 -> ChatScreen()
                1 -> TerminalScreen()
            }
        }
    }
}