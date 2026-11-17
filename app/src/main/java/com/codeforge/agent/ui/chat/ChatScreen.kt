package com.codeforge.agent.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode.name == "PLAN",
                onClick = { viewModel.setMode(com.codeforge.agent.domain.model.AgentMode.PLAN) },
                label = { Text("Plan") }
            )
            FilterChip(
                selected = state.mode.name == "BUILD",
                onClick = { viewModel.setMode(com.codeforge.agent.domain.model.AgentMode.BUILD) },
                label = { Text("Build") }
            )
            Text("Model: ${state.model}", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(state.messages) { msg ->
                Text(
                    text = "${msg.role.name}: ${msg.content}",
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.isStreaming) {
                item {
                    Text(
                        text = "Assistant: ${state.streamingText}",
                        modifier = Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            state.error?.let { err ->
                item { Text("Error: $err", color = MaterialTheme.colorScheme.error) }
            }
        }

        // Permission approval dialog
        state.pendingApproval?.let { pa ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Approve ${pa.toolName}?", modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.approve(pa.id) }) { Text("Allow") }
                Button(onClick = { viewModel.deny(pa.id) }) { Text("Deny") }
            }
        }

        // Accessory keyboard bar
        AccessoryKeyboardBar(onKey = { k -> input += k })

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message... (@file #symbol !cmd)") },
                maxLines = 6
            )
            Button(onClick = {
                viewModel.sendMessage(input)
                input = ""
            }, enabled = !state.isStreaming) {
                Text("Send")
            }
        }
    }
}

@Composable
fun AccessoryKeyboardBar(onKey: (String) -> Unit) {
    val keys = listOf("ESC", "TAB", "CTRL", "ALT", "/", "@", "!", "|", "-", "↑", "↓", "←", "→")
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { k ->
            androidx.compose.material3.AssistChip(onClick = { onKey("$k ") }, label = { Text(k, style = MaterialTheme.typography.labelSmall) })
        }
    }
}