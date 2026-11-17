package com.codeforge.agent.data.llm

import com.codeforge.agent.domain.model.LlmRequest
import com.codeforge.agent.domain.model.LlmStreamChunk
import kotlinx.coroutines.flow.Flow

interface LlmClient {
    fun stream(request: LlmRequest, baseUrl: String, apiKey: String, providerType: String): Flow<LlmStreamChunk>
    suspend fun complete(request: LlmRequest, baseUrl: String, apiKey: String, providerType: String): String
}