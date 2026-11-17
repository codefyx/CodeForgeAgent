package com.codeforge.agent.ext

import com.codeforge.agent.domain.model.Skill
import com.codeforge.agent.domain.model.SlashCommand
import java.io.File

object Extensions {
    fun parseCommandFile(file: File): SlashCommand? {
        val text = file.readText()
        val (frontmatter, body) = splitFrontmatter(text)
        val name = file.nameWithoutExtension
        val desc = frontmatter["description"] ?: ""
        return SlashCommand(name = name, description = desc, template = body.trim())
    }

    fun parseSkillFile(file: File): Skill? {
        val text = file.readText()
        val (frontmatter, body) = splitFrontmatter(text)
        val name = frontmatter["name"] ?: file.parentFile?.name ?: file.nameWithoutExtension
        val desc = frontmatter["description"] ?: ""
        return Skill(name = name, description = desc, content = body.trim(), path = file.absolutePath)
    }

    fun loadCommands(dir: File): List<SlashCommand> {
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == "md" }
            .mapNotNull { parseCommandFile(it) }.toList()
    }

    fun loadSkills(dir: File): List<Skill> {
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.name.equals("SKILL.md", true) }
            .mapNotNull { parseSkillFile(it) }.toList()
    }

    fun expandCommand(cmd: SlashCommand, args: String): String {
        return cmd.template.replace("\$ARGUMENTS", args)
    }

    private fun splitFrontmatter(text: String): Pair<Map<String, String>, String> {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap<String, String>() to text
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return emptyMap<String, String>() to text
        val fmLines = lines.subList(1, end + 1)
        val body = lines.drop(end + 2).joinToString("\n")
        val map = mutableMapOf<String, String>()
        fmLines.forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim().trim('"')
            }
        }
        return map to body
    }
}