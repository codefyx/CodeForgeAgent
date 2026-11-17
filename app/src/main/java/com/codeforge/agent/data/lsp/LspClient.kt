package com.codeforge.agent.data.lsp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class LspClient(private val command: String, private val args: List<String> = emptyList()) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var requestId = 0

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(listOf(command) + args)
            process = pb.start()
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendRequest(method: String, params: JsonObject): String = withContext(Dispatchers.IO) {
        val id = ++requestId
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }.toString()
        val writer = this@LspClient.writer ?: return@withContext ""
        writer.write("Content-Length: ${body.toByteArray().size}\r\n\r\n$body")
        writer.flush()
        // Read response
        readMessage()
    }

    private fun readMessage(): String {
        val r = reader ?: return ""
        // Read headers
        var contentLength = 0
        while (true) {
            val line = r.readLine() ?: return ""
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:")) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        if (contentLength <= 0) return ""
        val buf = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = r.read(buf, read, contentLength - read)
            if (n < 0) break
            read += n
        }
        return String(buf, 0, read)
    }

    fun stop() {
        process?.destroy()
        process = null
    }
}