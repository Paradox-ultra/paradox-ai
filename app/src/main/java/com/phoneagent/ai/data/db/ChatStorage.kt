package com.phoneagent.ai.data.db

import android.content.Context
import com.phoneagent.ai.data.model.ChatMessage
import com.phoneagent.ai.data.model.ChatSession
import com.phoneagent.ai.data.model.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatStorage(private val context: Context) {

    private val sessionsFile by lazy { File(context.filesDir, "chat_sessions.json") }
    private val memoriesFile by lazy { File(context.filesDir, "agent_memories.json") }

    private fun getMessagesFile(sessionId: String): File {
        val dir = File(context.filesDir, "messages").apply { if (!exists()) mkdirs() }
        return File(dir, "session_${sessionId}.json")
    }

    suspend fun getSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        if (!sessionsFile.exists()) return@withContext emptyList()
        try {
            val content = sessionsFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ChatSession>()
            for (i in 0 until array.length()) {
                list.add(ChatSession.fromJson(array.getJSONObject(i)))
            }
            list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveSession(session: ChatSession) = withContext(Dispatchers.IO) {
        val sessions = getSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            sessions[index] = session
        } else {
            sessions.add(0, session)
        }
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        sessionsFile.writeText(array.toString())
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val sessions = getSessions().filter { it.id != sessionId }
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        sessionsFile.writeText(array.toString())

        val msgFile = getMessagesFile(sessionId)
        if (msgFile.exists()) msgFile.delete()
    }

    suspend fun getMessages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val file = getMessagesFile(sessionId)
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                list.add(ChatMessage.fromJson(array.getJSONObject(i)))
            }
            list.sortedBy { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveMessages(sessionId: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val file = getMessagesFile(sessionId)
        val array = JSONArray()
        messages.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    // Memories
    suspend fun getMemories(): List<MemoryItem> = withContext(Dispatchers.IO) {
        if (!memoriesFile.exists()) return@withContext emptyList()
        try {
            val content = memoriesFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<MemoryItem>()
            for (i in 0 until array.length()) {
                list.add(MemoryItem.fromJson(array.getJSONObject(i)))
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveMemory(memory: MemoryItem) = withContext(Dispatchers.IO) {
        val memories = getMemories().toMutableList()
        val index = memories.indexOfFirst { it.key.equals(memory.key, ignoreCase = true) }
        if (index >= 0) {
            memories[index] = memory
        } else {
            memories.add(0, memory)
        }
        val array = JSONArray()
        memories.forEach { array.put(it.toJson()) }
        memoriesFile.writeText(array.toString())
    }

    suspend fun deleteMemory(memoryId: String) = withContext(Dispatchers.IO) {
        val memories = getMemories().filter { it.id != memoryId }
        val array = JSONArray()
        memories.forEach { array.put(it.toJson()) }
        memoriesFile.writeText(array.toString())
    }

    suspend fun clearMemories() = withContext(Dispatchers.IO) {
        if (memoriesFile.exists()) memoriesFile.delete()
    }
}
