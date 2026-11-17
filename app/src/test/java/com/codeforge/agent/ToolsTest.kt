package com.codeforge.agent

import com.codeforge.agent.agent.tools.GlobTool
import com.codeforge.agent.agent.tools.GrepTool
import com.codeforge.agent.agent.tools.ReadTool
import com.codeforge.agent.agent.tools.ToolContext
import com.codeforge.agent.agent.tools.WriteTool
import com.codeforge.agent.domain.model.AgentMode
import com.codeforge.agent.domain.permission.PermissionEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolsTest {
    @Test
    fun writeAndReadFile() = runBlocking {
        val root = File(System.getProperty("java.io.tmpdir"), "ws_${System.nanoTime()}")
        root.mkdirs()
        val ctx = ToolContext(root.absolutePath, AgentMode.BUILD, PermissionEngine())
        WriteTool().execute(buildJsonObject { put("path", "a.txt"); put("content", "hello world") }, ctx)
        val r = ReadTool().execute(buildJsonObject { put("path", "a.txt") }, ctx)
        assertTrue(r.output.contains("hello world"))
        root.deleteRecursively()
        Unit
    }

    @Test
    fun grepFindsPattern() = runBlocking {
        val root = File(System.getProperty("java.io.tmpdir"), "ws_${System.nanoTime()}")
        root.mkdirs()
        File(root, "b.txt").writeText("foo bar\nbaz qux")
        val ctx = ToolContext(root.absolutePath, AgentMode.BUILD, PermissionEngine())
        val r = GrepTool().execute(buildJsonObject { put("pattern", "bar") }, ctx)
        assertTrue(r.output.contains("b.txt"))
        root.deleteRecursively()
        Unit
    }

    @Test
    fun globFindsFiles() = runBlocking {
        val root = File(System.getProperty("java.io.tmpdir"), "ws_${System.nanoTime()}")
        root.mkdirs()
        File(root, "x.kt").writeText("")
        File(root, "y.txt").writeText("")
        val ctx = ToolContext(root.absolutePath, AgentMode.BUILD, PermissionEngine())
        val r = GlobTool().execute(buildJsonObject { put("pattern", "*.kt") }, ctx)
        assertTrue(r.output.contains("x.kt"))
        root.deleteRecursively()
        Unit
    }
}