package com.codeforge.agent.domain.permission

import com.codeforge.agent.domain.model.Permission
import com.codeforge.agent.domain.model.PermissionPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PendingApproval(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val description: String,
    val payload: String
)

class PermissionEngine(
    initialPolicies: List<PermissionPolicy> = emptyList()
) {
    private val policies = mutableMapOf<String, Permission>().apply {
        initialPolicies.forEach { put(it.toolName, it.permission) }
    }

    private val _pending = MutableStateFlow<PendingApproval?>(null)
    val pending: StateFlow<PendingApproval?> = _pending.asStateFlow()

    private var deferred: CompletableDeferred<Boolean>? = null

    fun policyFor(toolName: String): Permission =
        policies.getOrElse(toolName) {
            if (toolName in DESTRUCTIVE_TOOLS) Permission.ASK else Permission.ALLOW
        }

    fun setPolicy(toolName: String, permission: Permission) {
        policies[toolName] = permission
    }

    suspend fun requestApproval(toolName: String, description: String, payload: String): Boolean {
        return when (policyFor(toolName)) {
            Permission.ALLOW -> true
            Permission.DENY -> false
            Permission.ASK -> {
                val approval = PendingApproval(toolName = toolName, description = description, payload = payload)
                val d = CompletableDeferred<Boolean>()
                deferred = d
                _pending.value = approval
                val result = d.await()
                _pending.value = null
                deferred = null
                result
            }
        }
    }

    fun approve(id: String) {
        val cur = _pending.value
        if (cur?.id == id) deferred?.complete(true)
    }

    fun deny(id: String) {
        val cur = _pending.value
        if (cur?.id == id) deferred?.complete(false)
    }

    companion object {
        val DESTRUCTIVE_TOOLS = setOf("write", "edit", "bash", "webfetch")
    }
}