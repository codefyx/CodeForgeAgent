package com.codeforge.agent.agent.tools

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) { tools[tool.name] = tool }
    fun get(name: String): Tool? = tools[name]
    fun all(): List<Tool> = tools.values.toList()
    fun names(): List<String> = tools.keys.toList()

    fun defaultRegistry(): ToolRegistry = this.apply {
        register(ReadTool())
        register(WriteTool())
        register(EditTool())
        register(GrepTool())
        register(GlobTool())
        register(BashTool())
        register(WebFetchTool())
        register(TodoWriteTool())
        register(TodoReadTool())
    }
}