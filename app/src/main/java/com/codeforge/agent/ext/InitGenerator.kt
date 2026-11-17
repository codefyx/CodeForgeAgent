package com.codeforge.agent.ext

import java.io.File

object InitGenerator {
    fun generate(workspaceRoot: String): String {
        val root = File(workspaceRoot)
        val files = root.walkTopDown().filter { it.isFile }.take(200).map { it.relativeTo(root).path }.toList()
        val dirs = root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        return buildString {
            appendLine("# AGENTS.md")
            appendLine()
            appendLine("## Project")
            appendLine("Root: ${root.name}")
            appendLine()
            appendLine("## Top-level directories")
            dirs.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## File inventory (sample)")
            files.take(80).forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Conventions")
            appendLine("- Follow existing code style and patterns.")
            appendLine("- Run tests before committing.")
        }
    }
}