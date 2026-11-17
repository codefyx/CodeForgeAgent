package com.codeforge.agent.data.llm

import com.codeforge.agent.domain.model.LlmMessage
import com.codeforge.agent.domain.model.LlmRequest
import com.codeforge.agent.domain.model.LlmStreamChunk
import com.codeforge.agent.domain.model.LlmTool
import com.codeforge.agent.domain.model.ThinkingBudget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OkHttpLlmClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : LlmClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun stream(
        request: LlmRequest,
        baseUrl: String,
        apiKey: String,
        providerType: String
    ): Flow<LlmStreamChunk> = flow {
        val url = buildUrl(baseUrl, providerType)
        val body = buildRequestBody(request, providerType)
        val httpRequest = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .apply {
                when (providerType.lowercase()) {
                    "anthropic" -> header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                    "gemini" -> { /* key in query */ }
                    else -> header("Authorization", "Bearer $apiKey")
                }
            }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: "unknown error"
            throw IllegalStateException("LLM request failed ${response.code}: $err")
        }
        val source = response.body?.source() ?: throw IllegalStateException("empty body")
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue
            if (line.startsWith("data:")) {
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                parseChunk(data, providerType)?.let { emit(it) }
            }
        }
        response.close()
    }

    private fun buildUrl(baseUrl: String, providerType: String): String {
        val base = baseUrl.trimEnd('/')
        return when (providerType.lowercase()) {
            "anthropic" -> "$base/v1/messages"
            "gemini" -> "$base/v1beta/models:streamGenerateContent?alt=sse"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun buildRequestBody(request: LlmRequest, providerType: String): String {
        return when (providerType.lowercase()) {
            "anthropic" -> buildAnthropicBody(request)
            "gemini" -> buildGeminiBody(request)
            else -> buildOpenAiBody(request)
        }
    }

    private fun buildOpenAiBody(request: LlmRequest): String {
        val obj = buildJsonObject {
            put("model", request.model)
            put("stream", request.stream)
            put("temperature", request.temperature)
            request.maxTokens?.let { put("max_tokens", it) }
            put("messages", buildJsonArray {
                request.messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                        if (m.toolCalls.isNotEmpty()) {
                            put("tool_calls", buildJsonArray {
                                m.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        put("function", buildJsonObject {
                                            put("name", tc.name)
                                            put("arguments", tc.arguments)
                                        })
                                    })
                                }
                            })
                        }
                        m.toolCallId?.let { put("tool_call_id", it) }
                    })
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", kotlinx.serialization.json.Json.parseToJsonElement(t.parametersJson))
                            })
                        })
                    }
                })
            }
        }
        return obj.toString()
    }

    private fun buildAnthropicBody(request: LlmRequest): String {
        val systemMsg = request.messages.firstOrNull { it.role == "system" }?.content
        val obj = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens ?: 4096)
            put("stream", request.stream)
            systemMsg?.let { put("system", it) }
            put("messages", buildJsonArray {
                request.messages.filter { it.role != "system" }.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == "assistant") "assistant" else "user")
                        put("content", m.content)
                    })
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { t ->
                        add(buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", kotlinx.serialization.json.Json.parseToJsonElement(t.parametersJson))
                        })
                    }
                })
            }
        }
        return obj.toString()
    }

    private fun buildGeminiBody(request: LlmRequest): String {
        val obj = buildJsonObject {
            put("contents", buildJsonArray {
                request.messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == "assistant") "model" else "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", m.content) })
                        })
                    })
                }
            })
            put("generationConfig", buildJsonObject {
                put("temperature", request.temperature)
                request.maxTokens?.let { put("maxOutputTokens", it) }
            })
        }
        return obj.toString()
    }

    private fun parseChunk(data: String, providerType: String): LlmStreamChunk? {
        return try {
            when (providerType.lowercase()) {
                "anthropic" -> parseAnthropicChunk(data)
                "gemini" -> parseGeminiChunk(data)
                else -> parseOpenAiChunk(data)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOpenAiChunk(data: String): LlmStreamChunk {
        val obj = json.parseToJsonElement(data) as JsonObject
        val choices = obj["choices"] as? JsonArray
        val choice = choices?.getOrNull(0) as? JsonObject
        val delta = choice?.get("delta") as? JsonObject
        val content = (delta?.get("content") as? JsonPrimitive)?.content ?: ""
        val finish = (choice?.get("finish_reason") as? JsonPrimitive)?.content
        val usage = obj["usage"] as? JsonObject
        val prompt = (usage?.get("prompt_tokens") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val completion = (usage?.get("completion_tokens") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val toolCalls = mutableListOf<com.codeforge.agent.domain.model.ToolCall>()
        val tcArr = delta?.get("tool_calls") as? JsonArray
        tcArr?.forEach { t ->
            val to = t as JsonObject
            val fn = to["function"] as? JsonObject
            toolCalls.add(com.codeforge.agent.domain.model.ToolCall(
                id = (to["id"] as? JsonPrimitive)?.content ?: "",
                name = (fn?.get("name") as? JsonPrimitive)?.content ?: "",
                arguments = (fn?.get("arguments") as? JsonPrimitive)?.content ?: ""
            ))
        }
        return LlmStreamChunk(delta = content, toolCalls = toolCalls, finishReason = finish,
            promptTokens = prompt, completionTokens = completion)
    }

    private fun parseAnthropicChunk(data: String): LlmStreamChunk {
        val obj = json.parseToJsonElement(data) as JsonObject
        val type = (obj["type"] as? JsonPrimitive)?.content ?: ""
        return when (type) {
            "content_block_delta" -> {
                val delta = obj["delta"] as? JsonObject
                LlmStreamChunk(delta = (delta?.get("text") as? JsonPrimitive)?.content ?: "")
            }
            "message_stop" -> LlmStreamChunk(finishReason = "stop")
            else -> LlmStreamChunk()
        }
    }

    private fun parseGeminiChunk(data: String): LlmStreamChunk {
        val obj = json.parseToJsonElement(data) as JsonObject
        val candidates = obj["candidates"] as? JsonArray
        val cand = candidates?.getOrNull(0) as? JsonObject
        val content = cand?.get("content") as? JsonObject
        val parts = content?.get("parts") as? JsonArray
        val text = (parts?.getOrNull(0) as? JsonObject)?.get("text") as? JsonPrimitive
        return LlmStreamChunk(delta = text?.content ?: "")
    }

    override suspend fun complete(request: LlmRequest, baseUrl: String, apiKey: String, providerType: String): String {
        val sb = StringBuilder()
        stream(request.copy(stream = false), baseUrl, apiKey, providerType).collect { sb.append(it.delta) }
        return sb.toString()
    }
}