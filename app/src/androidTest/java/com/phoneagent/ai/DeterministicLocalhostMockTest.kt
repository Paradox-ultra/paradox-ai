package com.phoneagent.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phoneagent.ai.data.api.AiService
import com.phoneagent.ai.data.api.StreamEvent
import com.phoneagent.ai.data.db.ChatStorage
import com.phoneagent.ai.data.model.AiProvider
import com.phoneagent.ai.data.model.ChatMessage
import com.phoneagent.ai.data.model.MessageRole
import com.phoneagent.ai.data.secure.KeyStoreManager
import com.phoneagent.ai.data.tools.NativeToolExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeterministicLocalhostMockTest {

    private lateinit var context: Context
    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var chatStorage: ChatStorage
    private lateinit var toolExecutor: NativeToolExecutor
    private lateinit var aiService: AiService
    private lateinit var mockServer: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyStoreManager = KeyStoreManager(context)
        chatStorage = ChatStorage(context)
        toolExecutor = NativeToolExecutor(context, chatStorage)
        aiService = AiService(keyStoreManager, toolExecutor)

        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun tearDown() {
        try {
            mockServer.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
    }

    @Test
    fun testSseStreaming() = runBlocking {
        val sseBody = """
            data: {"choices":[{"delta":{"content":"Hello"}}]}
            
            data: {"choices":[{"delta":{"content":" from PhoneAgent"}}]}
            
            data: [DONE]
            
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody)
        )

        val serverUrl = mockServer.url("/v1").toString()
        keyStoreManager.setSelectedProvider(AiProvider.CUSTOM)
        keyStoreManager.setCustomBaseUrl(serverUrl)
        keyStoreManager.setCustomKey("mock-test-key")

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Say hello"))

        aiService.streamChat(messages, "System Prompt") { event ->
            events.add(event)
        }

        val contentDeltas = events.filterIsInstance<StreamEvent.ContentDelta>().map { it.text }
        assertTrue("Should receive 'Hello' delta", contentDeltas.contains("Hello"))
        assertTrue("Should receive ' from PhoneAgent' delta", contentDeltas.contains(" from PhoneAgent"))
        assertTrue("Should receive Completed event", events.any { it is StreamEvent.Completed })
    }

    @Test
    fun testBoundedToolLoopExecution() = runBlocking {
        // 1. Prepare a test file in app files directory
        val testFile = File(context.filesDir, "phoenix_specs.txt")
        testFile.writeText("Phoenix Project: Local bounded Android AI Agent beta with zero cloud spending.")

        // 2. Mock response 1: LLM requests tool call `local_file_search`
        val toolCallResponse = """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_123","function":{"name":"local_file_search","arguments":"{\"query\":\"Phoenix\"}"}}]}}]}
            
            data: [DONE]
            
        """.trimIndent()

        // 3. Mock response 2: LLM receives tool result and completes
        val finalResponse = """
            data: {"choices":[{"delta":{"content":"I verified the phoenix_specs file on your device."}}]}
            
            data: [DONE]
            
        """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(toolCallResponse))
        mockServer.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(finalResponse))

        val serverUrl = mockServer.url("/v1").toString()
        keyStoreManager.setSelectedProvider(AiProvider.CUSTOM)
        keyStoreManager.setCustomBaseUrl(serverUrl)
        keyStoreManager.setCustomKey("mock-test-key")
        keyStoreManager.setToolLoopEnabled(true)
        keyStoreManager.setBoundedToolLoopMaxIterations(5)

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Search local files for Phoenix"))

        aiService.streamChat(messages, "System prompt") { event ->
            events.add(event)
        }

        // Verify tool call started
        val toolStarted = events.filterIsInstance<StreamEvent.ToolCallStarted>()
        assertEquals("Should start 1 tool call", 1, toolStarted.size)
        assertEquals("local_file_search", toolStarted.first().toolName)

        // Verify tool call finished with real disk contents
        val toolFinished = events.filterIsInstance<StreamEvent.ToolCallFinished>()
        assertEquals("Should finish 1 tool call", 1, toolFinished.size)
        assertTrue("Tool result should contain phoenix_specs.txt", toolFinished.first().result.contains("phoenix_specs.txt"))

        // Verify final synthesis
        val contentDeltas = events.filterIsInstance<StreamEvent.ContentDelta>().map { it.text }
        assertTrue("Should synthesize answer", contentDeltas.any { it.contains("phoenix_specs") })
        assertTrue("Should receive Completed", events.any { it is StreamEvent.Completed })
    }

    @Test
    fun testBoundedFileReadAndTraversalSafety() = runBlocking {
        // Test bounded read limit (8000 chars)
        val largeContent = "A".repeat(12000)
        val largeFile = File(context.filesDir, "large_sample.txt")
        largeFile.writeText(largeContent)

        val readResult = toolExecutor.executeTool("local_file_read", """{"file_name":"large_sample.txt"}""")
        assertTrue("Read output should truncate at 8000 characters", readResult.contains("...[truncated at 8000 characters]"))
        assertTrue("Read output length should be bounded", readResult.length < 9000)

        // Test path traversal sanitization
        val traversalResult = toolExecutor.executeTool("local_file_read", """{"file_name":"../../../../etc/passwd"}""")
        assertTrue("Should safely reject or restrict to basename without escaping", traversalResult.contains("File not found: 'passwd'"))
    }

    @Test
    fun testMemoryPersistenceAndRecall() = runBlocking {
        // Save to memory
        val saveResult = toolExecutor.executeTool(
            "memory_save",
            """{"key":"test_preferred_theme","content":"AMOLED mode for maximum contrast","category":"preference"}"""
        )
        assertTrue("Save result should confirm persistence", saveResult.contains("Successfully saved to memory"))

        // Recall from memory
        val recallResult = toolExecutor.executeTool("memory_recall", """{"query":"contrast"}""")
        assertTrue("Recall result should return matching memory item", recallResult.contains("AMOLED mode for maximum contrast"))

        // Verify persistence in ChatStorage
        val stored = chatStorage.getMemories()
        assertTrue("ChatStorage must contain saved memory", stored.any { it.key == "test_preferred_theme" })
    }

    @Test
    fun testStepLimitEnforcement() = runBlocking {
        // Enqueue 5 continuous tool call responses
        val recursiveToolCall = """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"loop_call","function":{"name":"datetime","arguments":"{}"}}]}}]}
            
            data: [DONE]
            
        """.trimIndent()

        for (i in 0 until 5) {
            mockServer.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(recursiveToolCall))
        }

        val serverUrl = mockServer.url("/v1").toString()
        keyStoreManager.setSelectedProvider(AiProvider.CUSTOM)
        keyStoreManager.setCustomBaseUrl(serverUrl)
        keyStoreManager.setCustomKey("mock-test-key")
        keyStoreManager.setToolLoopEnabled(true)
        keyStoreManager.setBoundedToolLoopMaxIterations(2) // Max 2 iterations

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Keep checking time"))

        aiService.streamChat(messages, "System prompt") { event ->
            events.add(event)
        }

        val toolStarts = events.filterIsInstance<StreamEvent.ToolCallStarted>()
        assertEquals("Should execute exactly 2 tool iterations", 2, toolStarts.size)

        val deltas = events.filterIsInstance<StreamEvent.ContentDelta>().map { it.text }
        assertTrue(
            "Should output max iteration bound limit notice",
            deltas.any { it.contains("Bounded tool loop reached max iterations (2)") }
        )
    }

    @Test
    fun testCancellation() = runBlocking {
        // Enqueue response with 5 second delay
        val delayedSse = """
            data: {"choices":[{"delta":{"content":"This should be canceled"}}]}
            
            data: [DONE]
            
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBodyDelay(3, TimeUnit.SECONDS)
                .setBody(delayedSse)
        )

        val serverUrl = mockServer.url("/v1").toString()
        keyStoreManager.setSelectedProvider(AiProvider.CUSTOM)
        keyStoreManager.setCustomBaseUrl(serverUrl)
        keyStoreManager.setCustomKey("mock-test-key")

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Cancel me"))

        val job = launch {
            aiService.streamChat(messages, "System prompt") { event ->
                events.add(event)
            }
        }

        delay(150)
        aiService.cancelOngoingRequest()
        job.join()

        val errors = events.filterIsInstance<StreamEvent.Error>()
        assertTrue("Cancellation should emit user stop notice", errors.any { it.message.contains("stopped by user") || it.message.contains("canceled") })
    }

    @Test
    fun testOfflineHandling() = runBlocking {
        // Point to offline address where no port is bound
        keyStoreManager.setSelectedProvider(AiProvider.CUSTOM)
        keyStoreManager.setCustomBaseUrl("http://127.0.0.1:59123/v1")
        keyStoreManager.setCustomKey("mock-test-key")

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Are you online?"))

        aiService.streamChat(messages, "System prompt") { event ->
            events.add(event)
        }

        val errors = events.filterIsInstance<StreamEvent.Error>()
        assertTrue("Offline endpoint should produce honest StreamEvent.Error", errors.isNotEmpty())
        assertTrue("Error message should describe connection failure", errors.first().message.contains("Failed to connect") || errors.first().message.contains("Request failed"))
    }

    @Test
    fun testNoKeyHandling() = runBlocking {
        // Provider GROQ with empty key
        keyStoreManager.setSelectedProvider(AiProvider.GROQ)
        keyStoreManager.setGroqKey("")

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Hello without key"))

        aiService.streamChat(messages, "System prompt") { event ->
            events.add(event)
        }

        val errors = events.filterIsInstance<StreamEvent.Error>()
        assertEquals("Missing key should emit 1 error event", 1, errors.size)
        assertTrue("Error must instruct user how to configure key in Settings", errors.first().message.contains("No API key configured for Groq"))
        assertEquals("Mock server should have received 0 requests", 0, mockServer.requestCount)
    }

    @Test
    fun testBadJsonHandling() = runBlocking {
        val corruptedSse = """
            data: {not_valid_json_at_all!!
            
            data: {"choices":[{"delta":{"content":"Recovered from bad chunk"}}]}
            
            data: [DONE]
            
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(corruptedSse)
        )

        val serverUrl = mockServer.url("/v1").toString()
        keyStoreManager.setSelectedProvider(AiProvider.CUSTOM)
        keyStoreManager.setCustomBaseUrl(serverUrl)
        keyStoreManager.setCustomKey("mock-test-key")

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Test bad json"))

        aiService.streamChat(messages, "System prompt") { event ->
            events.add(event)
        }

        val deltas = events.filterIsInstance<StreamEvent.ContentDelta>().map { it.text }
        assertTrue("Should ignore corrupt chunk and successfully emit valid delta", deltas.contains("Recovered from bad chunk"))
        assertTrue("Should receive Completed", events.any { it is StreamEvent.Completed })
    }

    @Test
    fun testOpenRouterPaidModelBlockedWithZeroRequests() = runBlocking {
        // Enforce provider = OPENROUTER
        keyStoreManager.setSelectedProvider(AiProvider.OPENROUTER)
        keyStoreManager.setOpenRouterKey("mock-openrouter-key")
        keyStoreManager.setSelectedModel(AiProvider.OPENROUTER, "openai/gpt-4o")

        val events = mutableListOf<StreamEvent>()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = "Hello"))

        aiService.streamChat(messages, "System prompt") { event ->
            events.add(event)
        }

        val errors = events.filterIsInstance<StreamEvent.Error>()
        assertEquals("Must emit an error event", 1, errors.size)
        assertTrue(
            "Error must state that OpenRouter paid model was blocked: ${errors.first().message}",
            errors.first().message.contains("OpenRouter paid model blocked")
        )
        // Assert ZERO requests were sent to the network / mock server!
        assertEquals("Zero HTTP requests must be sent when paid model is blocked", 0, mockServer.requestCount)
    }
}
