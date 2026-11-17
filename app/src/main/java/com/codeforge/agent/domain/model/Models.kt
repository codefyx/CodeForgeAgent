package com.codeforge.agent.domain.model

import kotlinx.serialization.Serializable

enum class AgentMode { PLAN, BUILD }

enum class Permission { ALLOW, ASK, DENY }

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val costUsd: Double = 0.0
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

@Serializable
data class ToolResult(
    val toolCallId: String,
    val output: String,
    val isError: Boolean = false
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val type: ProviderType = ProviderType.OPENAI_COMPATIBLE
)

enum class ProviderType {
    OPENAI_COMPATIBLE, ANTHROPIC, GEMINI, OLLAMA, BEDROCK, GROQ, OPENROUTER, COPILOT
}

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String,
    val providerId: String,
    val contextWindow: Int,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val inputCostPer1M: Double = 0.0,
    val outputCostPer1M: Double = 0.0
)

@Serializable
data class PermissionPolicy(
    val toolName: String,
    val permission: Permission = Permission.ASK
)

@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val workspaceUri: String? = null
)

@Serializable
data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val tools: List<LlmTool> = emptyList(),
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
    val stream: Boolean = true,
    val thinkingBudget: ThinkingBudget = ThinkingBudget.MEDIUM
)

enum class ThinkingBudget { LOW, MEDIUM, HIGH }

@Serializable
data class LlmMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
)

@Serializable
data class LlmTool(
    val name: String,
    val description: String,
    val parametersJson: String
)

@Serializable
data class LlmStreamChunk(
    val delta: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)

@Serializable
data class McpServerConfig(
    val name: String,
    val type: McpTransport = McpTransport.HTTP,
    val url: String? = null,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

enum class McpTransport { STDIO, SSE, HTTP }

@Serializable
data class LspServerConfig(
    val language: String,
    val command: String,
    val args: List<String> = emptyList()
)

@Serializable
data class Skill(
    val name: String,
    val description: String,
    val content: String,
    val path: String
)

@Serializable
data class SlashCommand(
    val name: String,
    val description: String,
    val template: String,
    val arguments: String = ""
)