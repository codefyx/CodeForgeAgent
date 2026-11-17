package com.codeforge.agent

import com.codeforge.agent.ext.Extensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExtensionsTest {
    @Test
    fun parseCommandWithFrontmatter() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "cmd_${System.nanoTime()}.md")
        tmp.writeText("---\ndescription: Run tests\ncmd: test\n---\nRun the test suite for \$ARGUMENTS")
        val cmd = Extensions.parseCommandFile(tmp)
        assertTrue(cmd != null)
        assertEquals(tmp.nameWithoutExtension, cmd!!.name)
        assertEquals("Run tests", cmd.description)
        assertTrue(cmd.template.contains("Run the test suite"))
        tmp.delete()
    }

    @Test
    fun expandCommandSubstitutesArgs() {
        val cmd = com.codeforge.agent.domain.model.SlashCommand("t", "d", "Run \$ARGUMENTS now", "")
        val expanded = Extensions.expandCommand(cmd, "foo")
        assertEquals("Run foo now", expanded)
    }

    @Test
    fun loadSkillsFindsSkillMd() {
        val root = File(System.getProperty("java.io.tmpdir"), "skills_${System.nanoTime()}")
        val dir = File(root, "myskill")
        dir.mkdirs()
        File(dir, "SKILL.md").writeText("---\nname: myskill\ndescription: d\n---\nbody")
        val skills = Extensions.loadSkills(root)
        assertEquals(1, skills.size)
        assertEquals("myskill", skills[0].name)
        root.deleteRecursively()
    }
}