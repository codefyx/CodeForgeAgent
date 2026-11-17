package com.codeforge.agent.data.mcp

import com.codeforge.agent.domain.model.McpServerConfig
import com.codeforge.agent.domain.model.McpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class McpClient(private val config: McpServerConfig) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private var requestId = 0
    private fun nextId() = ++requestId

    suspend fun initialize(): String = withContext(Dispatchers.IO) {
        val req = jsonRpc("initialize", buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
            put("clientInfo", buildJsonObject {
                put("name", "CodeForgeAgent")
                put("version", "1.0.0")
            })
        })
        send(req)
    }

    suspend fun listTools(): String = withContext(Dispatchers.IO) {
        send(jsonRpc("tools/list", buildJsonObject {}))
    }

    suspend fun callTool(name: String, args: JsonObject): String = withContext(Dispatchers.IO) {
        send(jsonRpc("tools/call", buildJsonObject {
            put("name", name)
            put("arguments", args)
        }))
    }

    private fun jsonRpc(method: String, params: JsonObject): String {
        val obj = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", nextId())
            put("method", method)
            put("params", params)
        }
        return obj.toString()
    }

    private fun send(payload: String): String {
        return when (config.type) {
            McpTransport.STDIO -> sendStdio(payload)
            McpTransport.SSE, McpTransport.HTTP -> sendHttp(payload)
        }
    }

    private fun sendHttp(payload: String): String {
        val url = config.url ?: throw IllegalStateException("MCP url missing")
        val req = Request.Builder().url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            return resp.body?.string() ?: ""
        }
    }

    private fun sendStdio(payload: String): String {
        val cmd = config.command ?: throw IllegalStateException("MCP command missing")
        val pb = ProcessBuilder(listOf(cmd) + config.args)
        config.env.forEach { (k, v) -> pb.environment()[k] = v }
        val proc = pb.start()
        proc.outputStream.bufferedWriter().use { it.write(payload + "\n"); it.flush() }
        val result = proc.inputStream.bufferedReader().readLine() ?: ""
        proc.destroy()
        return result
    }
}