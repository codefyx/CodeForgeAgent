package com.codeforge.agent.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.agent.agent.AgentEvent
import com.codeforge.agent.agent.AgentLoop
import com.codeforge.agent.data.secure.SecureStorage
import com.codeforge.agent.domain.model.AgentMode
import com.codeforge.agent.domain.model.ChatMessage
import com.codeforge.agent.domain.model.MessageRole
import com.codeforge.agent.domain.model.ProviderConfig
import com.codeforge.agent.domain.permission.PermissionEngine
import com.codeforge.agent.ext.Extensions
import com.codeforge.agent.ext.InitGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val mode: AgentMode = AgentMode.BUILD,
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val workspaceRoot: String = "",
    val model: String = "gpt-4o-mini",
    val provider: ProviderConfig = ProviderConfig("openai", "OpenAI", "https://api.openai.com"),
    val pendingApproval: com.codeforge.agent.domain.permission.PendingApproval? = null,
    val error: String? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val secureStorage = SecureStorage(app)
    private val permissionEngine = PermissionEngine()
    private val agentLoop = AgentLoop(permissionEngine = permissionEngine)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        permissionEngine.pending.let { flow ->
            viewModelScope.launch {
                flow.collect { pa ->
                    _state.value = _state.value.copy(pendingApproval = pa)
                }
            }
        }
    }

    fun setMode(mode: AgentMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setWorkspace(root: String) {
        _state.value = _state.value.copy(workspaceRoot = root)
    }

    fun setModel(model: String) {
        _state.value = _state.value.copy(model = model)
    }

    fun setProvider(provider: ProviderConfig) {
        _state.value = _state.value.copy(provider = provider)
    }

    fun approve(id: String) = permissionEngine.approve(id)
    fun deny(id: String) = permissionEngine.deny(id)

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(id = UUID.randomUUID().toString(), role = MessageRole.USER, content = text)
        val current = _state.value
        val newMessages = current.messages + userMsg
        _state.value = current.copy(messages = newMessages, isStreaming = true, streamingText = "", error = null)

        // Handle slash commands
        if (text.startsWith("/")) {
            handleSlashCommand(text, newMessages)
            return
        }

        viewModelScope.launch {
            val sb = StringBuilder()
            try {
                agentLoop.run(
                    messages = newMessages,
                    mode = current.mode,
                    provider = current.provider,
                    model = current.model,
                    workspaceRoot = current.workspaceRoot.ifBlank { getApplication<android.app.Application>().filesDir.absolutePath },
                    systemPrompt = buildSystemPrompt(current.mode)
                ).collect { event ->
                    when (event) {
                        is AgentEvent.Token -> {
                            sb.append(event.text)
                            _state.value = _state.value.copy(streamingText = sb.toString())
                        }
                        is AgentEvent.ToolCallStarted -> { /* UI can show card */ }
                        is AgentEvent.ToolResult -> { /* UI can show result */ }
                        is AgentEvent.Complete -> {
                            val assistantMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.ASSISTANT,
                                content = event.text
                            )
                            _state.value = _state.value.copy(
                                messages = _state.value.messages + assistantMsg,
                                isStreaming = false,
                                streamingText = ""
                            )
                        }
                        is AgentEvent.Error -> {
                            _state.value = _state.value.copy(isStreaming = false, error = event.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isStreaming = false, error = e.message)
            }
        }
    }

    private fun handleSlashCommand(text: String, messages: List<ChatMessage>) {
        val parts = text.trim().split(Regex("\\s+"), 2)
        val cmd = parts.getOrNull(0) ?: ""
        val args = parts.getOrNull(1) ?: ""
        when (cmd) {
            "/init" -> {
                val root = _state.value.workspaceRoot.ifBlank { getApplication<Application>().filesDir.absolutePath }
                val content = InitGenerator.generate(root)
                val file = java.io.File(root, "AGENTS.md")
                file.writeText(content)
                appendAssistant("Generated AGENTS.md:\n\n$content")
            }
            "/undo" -> appendAssistant("Undo requested (shadow commit rollback).")
            "/redo" -> appendAssistant("Redo requested.")
            "/compact" -> appendAssistant("Context compaction triggered.")
            "/models" -> appendAssistant("Model picker: ${_state.value.model}")
            "/connect" -> appendAssistant("Use settings to connect a provider.")
            else -> {
                // Try custom command
                val cmds = Extensions.loadCommands(java.io.File(_state.value.workspaceRoot, ".codeforge/commands"))
                val match = cmds.find { it.name == cmd.removePrefix("/") }
                if (match != null) {
                    val expanded = Extensions.expandCommand(match, args)
                    // Send expanded as user message
                    sendMessage(expanded)
                } else {
                    appendAssistant("Unknown command: $cmd")
                }
            }
        }
        _state.value = _state.value.copy(isStreaming = false)
    }

    private fun appendAssistant(text: String) {
        val msg = ChatMessage(id = UUID.randomUUID().toString(), role = MessageRole.ASSISTANT, content = text)
        _state.value = _state.value.copy(messages = _state.value.messages + msg)
    }

    private fun buildSystemPrompt(mode: AgentMode): String {
        val base = "You are CodeForge Agent, an AI coding assistant. Mode: ${mode.name}."
        val agentsMd = _state.value.workspaceRoot.let { root ->
            val f = java.io.File(root, "AGENTS.md")
            if (f.exists()) "\n\nProject context (AGENTS.md):\n${f.readText().take(8000)}" else ""
        }
        return base + agentsMd
    }
}