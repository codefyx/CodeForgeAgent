package com.codeforge.agent.ui.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    val output by viewModel.output.collectAsState()
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("Terminal", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(output, fontFamily = FontFamily.Monospace, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.send(input); input = "" }) { Text("Send") }
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            Button(onClick = { viewModel.start() }) { Text("Start") }
            Button(onClick = { viewModel.stop() }) { Text("Stop") }
        }
    }
}