package com.codeforge.agent.agent.tools

import com.codeforge.agent.domain.model.ToolResult
import kotlinx.serialization.json.JsonObject

interface Tool {
    val name: String
    val description: String
    val parametersJson: String
    suspend fun execute(args: JsonObject, context: ToolContext): ToolResult
}

data class ToolContext(
    val workspaceRoot: String,
    val mode: com.codeforge.agent.domain.model.AgentMode,
    val permissionEngine: com.codeforge.agent.domain.permission.PermissionEngine
)