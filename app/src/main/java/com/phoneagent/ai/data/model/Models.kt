package com.phoneagent.ai.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MessageRole {
    USER, ASSISTANT, SYSTEM, TOOL
}

enum class MessageStatus {
    SENT, STREAMING, COMPLETED, ERROR
}

data class AttachmentItem(
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long = 0L,
    val textContent: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("fileSize", fileSize)
        put("textContent", textContent ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): AttachmentItem {
            return AttachmentItem(
                uri = json.optString("uri", ""),
                fileName = json.optString("fileName", ""),
                mimeType = json.optString("mimeType", ""),
                fileSize = json.optLong("fileSize", 0L),
                textContent = json.optString("textContent").takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val thought: String? = null,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val attachments: List<AttachmentItem> = emptyList(),
    val status: MessageStatus = MessageStatus.COMPLETED,
    val errorMessage: String? = null,
    val isLiveInference: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role.name)
        put("content", content)
        put("timestamp", timestamp)
        put("thought", thought ?: "")
        put("toolCallsJson", toolCallsJson ?: "")
        put("toolCallId", toolCallId ?: "")
        put("toolName", toolName ?: "")
        put("status", status.name)
        put("errorMessage", errorMessage ?: "")
        put("isLiveInference", isLiveInference)

        val arr = JSONArray()
        attachments.forEach { arr.put(it.toJson()) }
        put("attachments", arr)
    }

    companion object {
        fun fromJson(json: JSONObject): ChatMessage {
            val attArr = json.optJSONArray("attachments")
            val attList = mutableListOf<AttachmentItem>()
            if (attArr != null) {
                for (i in 0 until attArr.length()) {
                    attList.add(AttachmentItem.fromJson(attArr.getJSONObject(i)))
                }
            }
            return ChatMessage(
                id = json.optString("id", UUID.randomUUID().toString()),
                role = MessageRole.valueOf(json.optString("role", MessageRole.USER.name)),
                content = json.optString("content", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                thought = json.optString("thought").takeIf { it.isNotEmpty() },
                toolCallsJson = json.optString("toolCallsJson").takeIf { it.isNotEmpty() },
                toolCallId = json.optString("toolCallId").takeIf { it.isNotEmpty() },
                toolName = json.optString("toolName").takeIf { it.isNotEmpty() },
                attachments = attList,
                status = MessageStatus.valueOf(json.optString("status", MessageStatus.COMPLETED.name)),
                errorMessage = json.optString("errorMessage").takeIf { it.isNotEmpty() },
                isLiveInference = json.optBoolean("isLiveInference", true)
            )
        }
    }
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): ChatSession {
            return ChatSession(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Conversation"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val keyManagementUrl: String,
    val documentationUrl: String,
    val defaultModels: List<String>
) {
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        keyManagementUrl = "https://console.groq.com/keys/",
        documentationUrl = "https://groq.com",
        defaultModels = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        keyManagementUrl = "https://openrouter.ai/keys",
        documentationUrl = "https://openrouter.ai",
        defaultModels = listOf(
            "openrouter/free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "mistralai/mistral-7b-instruct:free",
            "google/gemma-2-9b-it:free",
            "qwen/qwen-2.5-coder-32b-instruct:free"
        )
    ),
    CUSTOM(
        displayName = "FreeLLMAPI (Self-Hosted)",
        defaultBaseUrl = "http://10.0.2.2:8000/v1",
        keyManagementUrl = "",
        documentationUrl = "https://github.com/Paradox-ultra/freellmapi",
        defaultModels = listOf(
            "default",
            "llama-3.3",
            "custom"
        )
    )
}

data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val content: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("key", key)
        put("content", content)
        put("category", category)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): MemoryItem {
            return MemoryItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                key = json.optString("key", ""),
                content = json.optString("content", ""),
                category = json.optString("category", "general"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

data class SkillItem(
    val id: String,
    val name: String,
    val description: String,
    val triggers: List<String>,
    val content: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = true
)

enum class ChannelStatus {
    NOT_CONFIGURED,
    CONFIGURED_UNTESTED,
    VERIFIED_ONLINE,
    ERROR
}

data class TelegramConfig(
    val botToken: String = "",
    val chatId: String = "",
    val status: ChannelStatus = ChannelStatus.NOT_CONFIGURED,
    val statusMessage: String = "Not configured"
)

data class WhatsAppConfig(
    val phoneNumberId: String = "",
    val accessToken: String = "",
    val verifyToken: String = "",
    val status: ChannelStatus = ChannelStatus.NOT_CONFIGURED,
    val statusMessage: String = "Not configured"
)

data class SmsConfig(
    val enabled: Boolean = false,
    val mode: String = "NATIVE_INTENT", // NATIVE_INTENT or TWILIO
    val twilioAccountSid: String = "",
    val twilioAuthToken: String = "",
    val twilioFromNumber: String = "",
    val status: ChannelStatus = ChannelStatus.NOT_CONFIGURED,
    val statusMessage: String = "Not configured"
)

data class ComposioConfig(
    val apiKey: String = "",
    val entityId: String = "default",
    val isEnabled: Boolean = false,
    val connectedApps: List<String> = emptyList(),
    val statusMessage: String = "Composio integration configured directly with user keys"
)

enum class ThemeMode {
    DARK,
    LIGHT,
    AMOLED
}

enum class AccentColor(val displayName: String, val hex: Long) {
    CYAN("Cyan Aqua", 0xFF00D2C4),
    VIOLET("Electric Violet", 0xFF8B5CF6),
    ORANGE("Sunset Orange", 0xFFFF6B4A),
    EMERALD("Emerald Green", 0xFF10B981),
    BLUE("Royal Blue", 0xFF3B82F6),
    ROSE("Rose Pink", 0xFFEC4899)
}

enum class VoiceLanguage(val code: String, val displayName: String, val shortTag: String) {
    EN_IN("en-IN", "English (India) - en-IN", "EN"),
    HI_IN("hi-IN", "Hindi (हिन्दी) - hi-IN", "HI"),
    AUTO("auto", "Auto-Detect / Multilingual", "AUTO");

    companion object {
        fun fromCode(code: String): VoiceLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
        }
    }
}
