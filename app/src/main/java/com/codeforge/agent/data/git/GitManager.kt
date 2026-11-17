package com.codeforge.agent.data.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

class GitManager(private val workspaceRoot: String) {
    private fun repo(): Repository? {
        val gitDir = File(workspaceRoot, ".git")
        return if (gitDir.exists()) {
            FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().findGitDir().build()
        } else null
    }

    fun status(): String {
        val r = repo() ?: return "Not a git repository"
        Git(r).use { git ->
            val st = git.status().call()
            return buildString {
                appendLine("Changed: ${st.changed.size}")
                st.changed.forEach { appendLine("  M $it") }
                appendLine("Added: ${st.added.size}")
                st.added.forEach { appendLine("  A $it") }
                appendLine("Removed: ${st.removed.size}")
                st.removed.forEach { appendLine("  D $it") }
                appendLine("Untracked: ${st.untracked.size}")
                st.untracked.forEach { appendLine("  ? $it") }
            }
        }
    }

    fun diff(): String {
        val r = repo() ?: return "Not a git repository"
        Git(r).use { git ->
            val diffs = git.diff().call()
            return diffs.joinToString("\n") { d ->
                "${d.changeType} ${d.oldPath} -> ${d.newPath}"
            }.ifBlank { "No changes" }
        }
    }

    fun log(limit: Int = 20): String {
        val r = repo() ?: return "Not a git repository"
        Git(r).use { git ->
            return git.log().setMaxCount(limit).call().map { "${it.name.take(7)} ${it.shortMessage}" }.joinToString("\n")
        }
    }

    fun commit(message: String): String {
        val r = repo() ?: return "Not a git repository"
        Git(r).use { git ->
            git.add().addFilepattern(".").call()
            val rev = git.commit().setMessage(message).call()
            return "Committed ${rev.name.take(7)}: $message"
        }
    }

    fun branch(): String {
        val r = repo() ?: return "Not a git repository"
        Git(r).use { git ->
            return git.branchList().call().joinToString("\n") { it.name }
        }
    }

    fun checkout(branch: String): String {
        val r = repo() ?: return "Not a git repository"
        Git(r).use { git ->
            git.checkout().setName(branch).call()
            return "Checked out $branch"
        }
    }

    fun createSnapshot(label: String): String {
        val r = repo() ?: return "Not a git repository"
        Git(r).use { git ->
            git.stashCreate().setIncludeUntracked(true).call()
            return "Snapshot created: $label"
        }
    }
}