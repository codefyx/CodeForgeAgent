package com.codeforge.agent

import com.codeforge.agent.domain.model.Permission
import com.codeforge.agent.domain.permission.PermissionEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionEngineTest {
    @Test
    fun defaultPolicyDestructiveIsAsk() {
        val engine = PermissionEngine()
        assertEquals(Permission.ASK, engine.policyFor("bash"))
        assertEquals(Permission.ASK, engine.policyFor("write"))
    }

    @Test
    fun defaultPolicyReadOnlyIsAllow() {
        val engine = PermissionEngine()
        assertEquals(Permission.ALLOW, engine.policyFor("read"))
        assertEquals(Permission.ALLOW, engine.policyFor("grep"))
    }

    @Test
    fun setPolicyOverrides() {
        val engine = PermissionEngine()
        engine.setPolicy("bash", Permission.ALLOW)
        assertEquals(Permission.ALLOW, engine.policyFor("bash"))
    }

    @Test
    fun approveResolvesDeferred() = runTest {
        val engine = PermissionEngine()
        var result: Boolean? = null
        val deferred = async {
            engine.requestApproval("bash", "desc", "payload")
        }
        kotlinx.coroutines.delay(50)
        val pending = engine.pending.value
        assertTrue(pending != null)
        engine.approve(pending!!.id)
        assertEquals(true, deferred.await())
    }

    @Test
    fun denyResolvesDeferred() = runTest {
        val engine = PermissionEngine()
        var result: Boolean? = null
        val deferred = async {
            engine.requestApproval("bash", "desc", "payload")
        }
        kotlinx.coroutines.delay(50)
        val pending = engine.pending.value
        engine.deny(pending!!.id)
        assertEquals(false, deferred.await())
    }
}