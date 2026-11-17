package com.codeforge.agent.agent.tools

import com.codeforge.agent.domain.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

fun strArg(args: JsonObject, key: String): String =
    (args[key] as? JsonPrimitive)?.content ?: ""

fun intArg(args: JsonObject, key: String, default: Int = 0): Int =
    (args[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: default

class ReadTool : Tool {
    override val name = "read"
    override val description = "Read a file from the workspace."
    override val parametersJson = """{"type":"object","properties":{"path":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"}},"required":["path"]}"""

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val path = strArg(args, "path")
        val offset = intArg(args, "offset", 0)
        val limit = intArg(args, "limit", 2000)
        return withContext(Dispatchers.IO) {
            try {
                val file = resolve(path, context.workspaceRoot)
                if (!file.exists()) return@withContext ToolResult("", "File not found: $path", true)
                val lines = file.readLines()
                val slice = lines.drop(offset).take(limit)
                ToolResult("", slice.joinToString("\n"))
            } catch (e: Exception) {
                ToolResult("", "Error reading file: ${e.message}", true)
            }
        }
    }
}

class WriteTool : Tool {
    override val name = "write"
    override val description = "Write content to a file (creates or overwrites)."
    override val parametersJson = """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val path = strArg(args, "path")
        val content = strArg(args, "content")
        return withContext(Dispatchers.IO) {
            try {
                val file = resolve(path, context.workspaceRoot)
                file.parentFile?.mkdirs()
                file.writeText(content)
                ToolResult("", "Wrote ${content.length} bytes to $path")
            } catch (e: Exception) {
                ToolResult("", "Error writing file: ${e.message}", true)
            }
        }
    }
}

class EditTool : Tool {
    override val name = "edit"
    override val description = "Apply a search/replace edit to a file."
    override val parametersJson = """{"type":"object","properties":{"path":{"type":"string"},"oldString":{"type":"string"},"newString":{"type":"string"}},"required":["path","oldString","newString"]}"""

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val path = strArg(args, "path")
        val oldStr = strArg(args, "oldString")
        val newStr = strArg(args, "newString")
        return withContext(Dispatchers.IO) {
            try {
                val file = resolve(path, context.workspaceRoot)
                if (!file.exists()) return@withContext ToolResult("", "File not found: $path", true)
                val text = file.readText()
                if (!text.contains(oldStr)) return@withContext ToolResult("", "oldString not found in file", true)
                val updated = text.replace(oldStr, newStr)
                file.writeText(updated)
                ToolResult("", "Edited $path")
            } catch (e: Exception) {
                ToolResult("", "Error editing file: ${e.message}", true)
            }
        }
    }
}

class GrepTool : Tool {
    override val name = "grep"
    override val description = "Search file contents with a regex."
    override val parametersJson = """{"type":"object","properties":{"pattern":{"type":"string"},"path":{"type":"string"},"include":{"type":"string"}},"required":["pattern"]}"""

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val pattern = strArg(args, "pattern")
        val path = strArg(args, "path").ifBlank { "." }
        val include = strArg(args, "include")
        return withContext(Dispatchers.IO) {
            try {
                val root = resolve(path, context.workspaceRoot)
                val regex = Regex(pattern)
                val results = StringBuilder()
                val files = if (root.isFile) listOf(root) else root.walkTopDown().filter { it.isFile }.toList()
                var count = 0
                for (f in files) {
                    if (include.isNotBlank() && !f.name.matches(globToRegex(include))) continue
                    val lines = f.readLines()
                    lines.forEachIndexed { idx, line ->
                        if (regex.containsMatchIn(line)) {
                            results.append("${f.relativeTo(File(context.workspaceRoot))}:${idx + 1}: $line\n")
                            count++
                            if (count > 200) return@withContext ToolResult("", results.toString() + "\n... truncated")
                        }
                    }
                }
                ToolResult("", if (results.isEmpty()) "No matches" else results.toString())
            } catch (e: Exception) {
                ToolResult("", "Error grepping: ${e.message}", true)
            }
        }
    }
}

class GlobTool : Tool {
    override val name = "glob"
    override val description = "Find files matching a glob pattern."
    override val parametersJson = """{"type":"object","properties":{"pattern":{"type":"string"},"path":{"type":"string"}},"required":["pattern"]}"""

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val pattern = strArg(args, "pattern")
        val path = strArg(args, "path").ifBlank { "." }
        return withContext(Dispatchers.IO) {
            try {
                val root = resolve(path, context.workspaceRoot)
                val regex = globToRegex(pattern)
                val matches = root.walkTopDown().filter { it.isFile && it.relativeTo(root).path.replace('\\', '/').matches(regex) }
                    .map { it.relativeTo(File(context.workspaceRoot)).path.replace('\\', '/') }
                    .take(500).toList()
                ToolResult("", matches.joinToString("\n").ifBlank { "No matches" })
            } catch (e: Exception) {
                ToolResult("", "Error globbing: ${e.message}", true)
            }
        }
    }
}

class BashTool : Tool {
    override val name = "bash"
    override val description = "Run a shell command in the workspace."
    override val parametersJson = """{"type":"object","properties":{"command":{"type":"string"},"timeout":{"type":"integer"}},"required":["command"]}"""

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val command = strArg(args, "command")
        val timeout = intArg(args, "timeout", 60000)
        return withContext(Dispatchers.IO) {
            try {
                val approved = context.permissionEngine.requestApproval("bash", "Run shell command", command)
                if (!approved) return@withContext ToolResult("", "Permission denied", true)
                val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                pb.directory(File(context.workspaceRoot))
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val output = proc.inputStream.bufferedReader().readText()
                val finished = proc.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    return@withContext ToolResult("", "$output\n[timeout]", true)
                }
                ToolResult("", output.ifBlank { "(no output)" } + "\n[exit ${proc.exitValue()}]")
            } catch (e: Exception) {
                ToolResult("", "Error running command: ${e.message}", true)
            }
        }
    }
}

class WebFetchTool : Tool {
    override val name = "webfetch"
    override val description = "Fetch content from a URL."
    override val parametersJson = """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}"""

    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val url = strArg(args, "url")
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    ToolResult("", body.take(20000))
                }
            } catch (e: Exception) {
                ToolResult("", "Error fetching: ${e.message}", true)
            }
        }
    }
}

class TodoWriteTool : Tool {
    override val name = "todowrite"
    override val description = "Write a todo list."
    override val parametersJson = """{"type":"object","properties":{"items":{"type":"array","items":{"type":"string"}}},"required":["items"]}"""

    companion object {
        val items = mutableListOf<String>()
    }

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val arr = args["items"] as? kotlinx.serialization.json.JsonArray
        items.clear()
        arr?.forEach { items.add((it as? JsonPrimitive)?.content ?: it.toString()) }
        return ToolResult("", "Todo list updated: ${items.size} items")
    }
}

class TodoReadTool : Tool {
    override val name = "todoread"
    override val description = "Read the todo list."
    override val parametersJson = """{"type":"object","properties":{}}"""

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        return ToolResult("", TodoWriteTool.items.joinToString("\n").ifBlank { "(empty)" })
    }
}

fun resolve(path: String, root: String): File {
    val f = File(path)
    return if (f.isAbsolute) f else File(root, path)
}

fun globToRegex(glob: String): Regex {
    val sb = StringBuilder()
    for (c in glob) {
        when (c) {
            '*' -> sb.append(".*")
            '?' -> sb.append(".")
            '.', '(', ')', '+', '|', '^', '$', '@', '%' -> sb.append("\\").append(c)
            '\\' -> sb.append("\\\\")
            else -> sb.append(c)
        }
    }
    return Regex(sb.toString())
}