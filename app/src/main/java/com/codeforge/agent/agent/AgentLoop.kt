package com.codeforge.agent.agent

import com.codeforge.agent.data.llm.LlmClient
import com.codeforge.agent.data.llm.OkHttpLlmClient
import com.codeforge.agent.domain.model.AgentMode
import com.codeforge.agent.domain.model.ChatMessage
import com.codeforge.agent.domain.model.LlmMessage
import com.codeforge.agent.domain.model.LlmRequest
import com.codeforge.agent.domain.model.LlmStreamChunk
import com.codeforge.agent.domain.model.LlmTool
import com.codeforge.agent.domain.model.MessageRole
import com.codeforge.agent.domain.model.PermissionPolicy
import com.codeforge.agent.domain.model.ProviderConfig
import com.codeforge.agent.domain.model.ToolCall
import com.codeforge.agent.domain.model.ToolResult
import com.codeforge.agent.domain.permission.PermissionEngine
import com.codeforge.agent.agent.tools.ToolContext
import com.codeforge.agent.agent.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class AgentLoop(
    private val llmClient: LlmClient = OkHttpLlmClient(),
    private val toolRegistry: ToolRegistry = ToolRegistry().defaultRegistry(),
    private val permissionEngine: PermissionEngine = PermissionEngine()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun run(
        messages: List<ChatMessage>,
        mode: AgentMode,
        provider: ProviderConfig,
        model: String,
        workspaceRoot: String,
        systemPrompt: String
    ): Flow<AgentEvent> = flow {
        val llmMessages = mutableListOf<LlmMessage>()
        llmMessages.add(LlmMessage(role = "system", content = systemPrompt))
        messages.forEach { m ->
            llmMessages.add(LlmMessage(role = m.role.name.lowercase(), content = m.content))
        }

        val tools = if (mode == AgentMode.BUILD) {
            toolRegistry.all().map { LlmTool(it.name, it.description, it.parametersJson) }
        } else emptyList()

        var iterations = 0
        val maxIterations = 25
        while (iterations < maxIterations) {
            iterations++
            val request = LlmRequest(model = model, messages = llmMessages.toList(), tools = tools)
            val chunks = mutableListOf<LlmStreamChunk>()
            var accumulated = StringBuilder()
            val pendingToolCalls = mutableListOf<ToolCall>()

            llmClient.stream(request, provider.baseUrl, provider.apiKey, provider.type.name.lowercase()).collect { chunk ->
                accumulated.append(chunk.delta)
                if (chunk.toolCalls.isNotEmpty()) pendingToolCalls.addAll(chunk.toolCalls)
                emit(AgentEvent.Token(chunk.delta))
                chunks.add(chunk)
            }

            val finalToolCalls = pendingToolCalls
            if (finalToolCalls.isEmpty()) {
                emit(AgentEvent.Complete(accumulated.toString()))
                break
            }

            // Execute tools
            llmMessages.add(LlmMessage(role = "assistant", content = accumulated.toString(), toolCalls = finalToolCalls))
            for (tc in finalToolCalls) {
                emit(AgentEvent.ToolCallStarted(tc))
                val tool = toolRegistry.get(tc.name)
                val result = if (tool == null) {
                    ToolResult(tc.id, "Unknown tool: ${tc.name}", true)
                } else {
                    try {
                        val args = json.parseToJsonElement(tc.arguments).jsonObject
                        val ctx = ToolContext(workspaceRoot, mode, permissionEngine)
                        if (mode == AgentMode.PLAN && tc.name in setOf("write", "edit", "bash")) {
                            ToolResult(tc.id, "Tool not allowed in Plan mode", true)
                        } else {
                            tool.execute(args, ctx)
                        }
                    } catch (e: Exception) {
                        ToolResult(tc.id, "Tool error: ${e.message}", true)
                    }
                }
                emit(AgentEvent.ToolResult(tc, result))
                llmMessages.add(LlmMessage(role = "tool", content = result.output, toolCallId = tc.id))
            }
        }
        if (iterations >= maxIterations) emit(AgentEvent.Error("Max iterations reached"))
    }
}

sealed class AgentEvent {
    data class Token(val text: String) : AgentEvent()
    data class ToolCallStarted(val toolCall: ToolCall) : AgentEvent()
    data class ToolResult(val toolCall: ToolCall, val result: com.codeforge.agent.domain.model.ToolResult) : AgentEvent()
    data class Complete(val text: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}