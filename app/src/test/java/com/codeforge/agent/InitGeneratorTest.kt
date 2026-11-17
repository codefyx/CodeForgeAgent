package com.codeforge.agent

import com.codeforge.agent.ext.InitGenerator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InitGeneratorTest {
    @Test
    fun generatesAgentsMd() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "wf_${System.nanoTime()}")
        tmp.mkdirs()
        File(tmp, "src").mkdirs()
        File(tmp, "src/Main.kt").writeText("fun main() {}")
        val content = InitGenerator.generate(tmp.absolutePath)
        assertTrue(content.contains("# AGENTS.md"))
        assertTrue(content.contains("Main.kt"))
        tmp.deleteRecursively()
    }
}