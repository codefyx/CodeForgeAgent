package com.codeforge.agent

import com.codeforge.agent.agent.AgentEvent
import com.codeforge.agent.agent.AgentLoop
import com.codeforge.agent.domain.model.AgentMode
import com.codeforge.agent.domain.model.ChatMessage
import com.codeforge.agent.domain.model.MessageRole
import com.codeforge.agent.domain.model.ProviderConfig
import com.codeforge.agent.domain.model.ProviderType
import com.codeforge.agent.domain.permission.PermissionEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    @Test
    fun streamsTokensAndCompletes() = runTest {
        val server = MockWebServer()
        val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: [DONE]\n\n"
        server.enqueue(MockResponse().setBody(sse).setHeader("Content-Type", "text/event-stream"))
        server.start()
        val provider = ProviderConfig("openai", "OpenAI", server.url("/").toString().trimEnd('/'), type = ProviderType.OPENAI_COMPATIBLE)
        val loop = AgentLoop(permissionEngine = PermissionEngine())
        val msgs = listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "hi"))
        val events = loop.run(msgs, AgentMode.BUILD, provider, "gpt-4o-mini", System.getProperty("java.io.tmpdir"), "system").toList()
        server.shutdown()
        assertTrue(events.any { it is AgentEvent.Token })
        assertTrue(events.any { it is AgentEvent.Complete })
        val complete = events.filterIsInstance<AgentEvent.Complete>().first()
        assertTrue(complete.text.contains("Hello"))
    }
}