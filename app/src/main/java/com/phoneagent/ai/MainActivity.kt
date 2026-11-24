package com.phoneagent.ai

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.phoneagent.ai.data.api.AiService
import com.phoneagent.ai.data.api.StreamEvent
import com.phoneagent.ai.data.browser.AgenticBrowserController
import com.phoneagent.ai.data.browser.BrowserConfirmationResult
import com.phoneagent.ai.data.db.ChatStorage
import com.phoneagent.ai.data.document.DocumentTextExtractor
import com.phoneagent.ai.data.model.AttachmentItem
import com.phoneagent.ai.data.model.ChatMessage
import com.phoneagent.ai.data.model.ChatSession
import com.phoneagent.ai.data.model.MessageRole
import com.phoneagent.ai.data.model.MessageStatus
import com.phoneagent.ai.data.model.SkillItem
import com.phoneagent.ai.data.model.VoiceLanguage
import com.phoneagent.ai.data.secure.KeyStoreManager
import com.phoneagent.ai.data.skills.SkillsRepository
import com.phoneagent.ai.data.tools.NativeToolExecutor
import com.phoneagent.ai.data.voice.VoiceActionOutcome
import com.phoneagent.ai.data.voice.VoiceCommandDispatcher
import com.phoneagent.ai.data.voice.VoiceManager
import com.phoneagent.ai.ui.screens.BrowserScreen
import com.phoneagent.ai.ui.screens.ChatScreen
import com.phoneagent.ai.ui.screens.MemoryScreen
import com.phoneagent.ai.ui.screens.SettingsScreen
import com.phoneagent.ai.ui.screens.SkillsScreen
import com.phoneagent.ai.ui.theme.PhoneAgentTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

enum class CurrentScreen {
    CHAT, SETTINGS, SKILLS, MEMORY, BROWSER
}

class MainActivity : ComponentActivity() {

    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var chatStorage: ChatStorage
    private lateinit var toolExecutor: NativeToolExecutor
    private lateinit var aiService: AiService
    private lateinit var skillsRepository: SkillsRepository
    private lateinit var voiceCommandDispatcher: VoiceCommandDispatcher
    private lateinit var voiceManager: VoiceManager

    private var activeSpeechCallback: ((String) -> Unit)? = null
    private var cameraTempUri: Uri? = null
    private var pendingCameraAttachmentCallback: ((AttachmentItem) -> Unit)? = null
    private var pendingMicAction: (() -> Unit)? = null

    // Runtime microphone permission launcher
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingMicAction?.invoke()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice commands", Toast.LENGTH_LONG).show()
            voiceManager.reportPermissionDenied()
        }
        pendingMicAction = null
    }

    // Speech to text launcher
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            activeSpeechCallback?.invoke(spokenText)
        }
    }

    // Document / File picker launcher
    private var pendingFileAttachmentCallback: ((AttachmentItem) -> Unit)? = null
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not persistable
            }

            var fileName = "attachment"
            var fileSize = 0L
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }

            // Extract text from text files, Markdown, JSON, PDF, PPTX
            lifecycleScope.launch {
                val extractedText = DocumentTextExtractor.extractText(
                    contentResolver = contentResolver,
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType
                )
                val item = AttachmentItem(
                    uri = uri.toString(),
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    textContent = extractedText
                )
                pendingFileAttachmentCallback?.invoke(item)
            }
        }
    }

    // Camera photo launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraTempUri != null) {
            val uri = cameraTempUri!!
            val item = AttachmentItem(
                uri = uri.toString(),
                fileName = "photo_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                fileSize = 0L,
                textContent = "[User attached a freshly taken photo]"
            )
            pendingCameraAttachmentCallback?.invoke(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        keyStoreManager = KeyStoreManager(this)
        chatStorage = ChatStorage(this)
        toolExecutor = NativeToolExecutor(this, chatStorage)
        aiService = AiService(keyStoreManager, toolExecutor)
        skillsRepository = SkillsRepository(this)
        voiceCommandDispatcher = VoiceCommandDispatcher(toolExecutor.hardwareController, toolExecutor.browserController)
        voiceManager = VoiceManager(this)

        setContent {
            MainAppContent()
        }
    }

    override fun onDestroy() {
        voiceManager.shutdown()
        super.onDestroy()
    }

    fun speak(text: String) {
        if (voiceManager.isSpeaking.value) {
            voiceManager.stopSpeaking()
            return
        }
        val lang = keyStoreManager.getVoiceLanguage()
        voiceManager.speak(text, lang) { msg, intent ->
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Ignore if settings intent can't be resolved
            }
        }
    }

    fun startInComposerRecording(onStart: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMicAction = onStart
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            onStart()
        }
    }

    fun pickFile(onResult: (AttachmentItem) -> Unit) {
        pendingFileAttachmentCallback = onResult
        try {
            filePickerLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(this, "File picker error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun takeCameraPhoto(onResult: (AttachmentItem) -> Unit) {
        pendingCameraAttachmentCallback = onResult
        try {
            val imgDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            val tempFile = File(imgDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    private fun MainAppContent() {
        var themeMode by remember { mutableStateOf(keyStoreManager.getThemeMode()) }
        var accentColor by remember { mutableStateOf(keyStoreManager.getAccentColor()) }
        var voiceLanguage by remember { mutableStateOf(keyStoreManager.getVoiceLanguage()) }
        val isSpeaking by voiceManager.isSpeaking.collectAsState()
        val isListening by voiceManager.isListening.collectAsState()
        val rmsLevel by voiceManager.rmsLevel.collectAsState()
        val voiceTranscript by voiceManager.liveTranscript.collectAsState()
        val speechError by voiceManager.speechError.collectAsState()
        var priorDraftText by remember { mutableStateOf("") }

        var currentScreen by remember { mutableStateOf(CurrentScreen.CHAT) }
        val scope = rememberCoroutineScope()

        val sessions = remember { mutableStateListOf<ChatSession>() }
        var currentSession by remember { mutableStateOf<ChatSession?>(null) }
        val messages = remember { mutableStateListOf<ChatMessage>() }

        var inputText by remember { mutableStateOf("") }
        var isStreaming by remember { mutableStateOf(false) }
        var streamingJob by remember { mutableStateOf<Job?>(null) }

        val skills = remember { mutableStateListOf<SkillItem>() }
        var selectedSkill by remember { mutableStateOf<SkillItem?>(null) }

        val memories = remember { mutableStateListOf<com.phoneagent.ai.data.model.MemoryItem>() }
        val attachments = remember { mutableStateListOf<AttachmentItem>() }

        // Load initial data
        LaunchedEffect(Unit) {
            val loadedSkills = skillsRepository.getSkills()
            skills.clear()
            skills.addAll(loadedSkills)

            val loadedMemories = chatStorage.getMemories()
            memories.clear()
            memories.addAll(loadedMemories)

            val loadedSessions = chatStorage.getSessions()
            sessions.clear()
            sessions.addAll(loadedSessions)

            if (sessions.isNotEmpty()) {
                currentSession = sessions.first()
                val loadedMessages = chatStorage.getMessages(sessions.first().id)
                messages.clear()
                messages.addAll(loadedMessages)
            } else {
                val newSess = ChatSession(title = "New Conversation")
                chatStorage.saveSession(newSess)
                sessions.add(newSess)
                currentSession = newSess
            }
        }

        fun switchSession(session: ChatSession) {
            currentSession = session
            scope.launch {
                val loadedMessages = chatStorage.getMessages(session.id)
                messages.clear()
                messages.addAll(loadedMessages)
            }
        }

        fun createNewSession() {
            val newSess = ChatSession(title = "New Conversation")
            scope.launch {
                chatStorage.saveSession(newSess)
                sessions.add(0, newSess)
                switchSession(newSess)
            }
        }

        fun deleteSession(sessionId: String) {
            scope.launch {
                chatStorage.deleteSession(sessionId)
                sessions.removeAll { it.id == sessionId }
                if (currentSession?.id == sessionId) {
                    if (sessions.isNotEmpty()) {
                        switchSession(sessions.first())
                    } else {
                        createNewSession()
                    }
                }
            }
        }

        fun sendMessage() {
            val prompt = inputText.trim()
            if (prompt.isEmpty() && attachments.isEmpty()) return
            if (isStreaming) return

            val activeAttachments = attachments.toList()
            attachments.clear()
            inputText = ""

            val sess = currentSession ?: return
            val userMsg = ChatMessage(
                role = MessageRole.USER,
                content = prompt,
                attachments = activeAttachments,
                status = MessageStatus.COMPLETED
            )
            messages.add(userMsg)

            // Update session title if first message
            if (messages.size <= 2 && sess.title == "New Conversation") {
                val updatedTitle = if (prompt.length > 25) prompt.take(25) + "..." else prompt
                val updatedSession = sess.copy(title = updatedTitle, updatedAt = System.currentTimeMillis())
                currentSession = updatedSession
                val idx = sessions.indexOfFirst { it.id == sess.id }
                if (idx >= 0) sessions[idx] = updatedSession
                scope.launch { chatStorage.saveSession(updatedSession) }
            }

            scope.launch {
                chatStorage.saveMessages(sess.id, messages.toList())
            }

            // Assistant message placeholder
            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING
            )
            messages.add(assistantMsg)
            isStreaming = true

            streamingJob = scope.launch {
                try {
                    // Prepare system prompt with active skill and memory
                    val sb = StringBuilder()
                    sb.append("You are PhoneAgent, an autonomous, privacy-first AI agent running on Android. ")
                    sb.append("You have access to native on-device tools (calculator, datetime, device_info, web_fetch, memory_save, memory_recall). ")
                    sb.append("Always provide clear, direct, and actionable responses.\n\n")

                    // Inject active skill methodology if chosen
                    if (selectedSkill != null) {
                        sb.append("--- ACTIVE SKILL: ${selectedSkill!!.name} ---\n")
                        sb.append(selectedSkill!!.content)
                        sb.append("\n--- END OF SKILL INSTRUCTIONS ---\n\n")
                    }

                    // Inject current memories
                    val currentMems = chatStorage.getMemories()
                    if (currentMems.isNotEmpty()) {
                        sb.append("--- SAVED USER MEMORIES ---\n")
                        currentMems.forEach { m ->
                            sb.append("• [${m.key}] (${m.category}): ${m.content}\n")
                        }
                        sb.append("--- END OF MEMORIES ---\n\n")
                    }

                    val systemPrompt = sb.toString()
                    val accumulatedContent = StringBuilder()
                    val accumulatedThought = StringBuilder()

                    aiService.streamChat(
                        messages = messages.filter { it.id != assistantMsgId },
                        systemPrompt = systemPrompt
                    ) { event ->
                        when (event) {
                            is StreamEvent.ContentDelta -> {
                                accumulatedContent.append(event.text)
                                val idx = messages.indexOfFirst { it.id == assistantMsgId }
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        content = accumulatedContent.toString(),
                                        thought = accumulatedThought.toString().takeIf { it.isNotEmpty() }
                                    )
                                }
                            }
                            is StreamEvent.ThoughtDelta -> {
                                accumulatedThought.append(event.text)
                                val idx = messages.indexOfFirst { it.id == assistantMsgId }
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        thought = accumulatedThought.toString()
                                    )
                                }
                            }
                            is StreamEvent.ToolCallStarted -> {
                                // Add tool invocation message
                                val toolMsg = ChatMessage(
                                    role = MessageRole.TOOL,
                                    content = "Executing ${event.toolName}...",
                                    toolName = event.toolName,
                                    toolCallId = event.toolCallId,
                                    status = MessageStatus.STREAMING
                                )
                                messages.add(messages.size - 1, toolMsg)
                            }
                            is StreamEvent.ToolCallFinished -> {
                                val idx = messages.indexOfLast { it.toolCallId == null && it.toolName == event.toolName }
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        content = event.result,
                                        status = MessageStatus.COMPLETED
                                    )
                                }
                            }
                            is StreamEvent.Completed -> {
                                val finalContent = accumulatedContent.toString().ifEmpty { event.fullContent }
                                val idx = messages.indexOfFirst { it.id == assistantMsgId }
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        content = finalContent,
                                        thought = accumulatedThought.toString().takeIf { it.isNotEmpty() },
                                        status = MessageStatus.COMPLETED
                                    )
                                }
                                chatStorage.saveMessages(sess.id, messages.toList())
                                isStreaming = false

                                if (keyStoreManager.isTtsAutoReadEnabled() && finalContent.isNotBlank()) {
                                    speak(finalContent)
                                }
                            }
                            is StreamEvent.Error -> {
                                val idx = messages.indexOfFirst { it.id == assistantMsgId }
                                if (idx >= 0) {
                                    messages[idx] = messages[idx].copy(
                                        status = MessageStatus.ERROR,
                                        errorMessage = event.message
                                    )
                                }
                                chatStorage.saveMessages(sess.id, messages.toList())
                                isStreaming = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    val idx = messages.indexOfFirst { it.id == assistantMsgId }
                    if (idx >= 0) {
                        messages[idx] = messages[idx].copy(
                            status = MessageStatus.ERROR,
                            errorMessage = e.message ?: "Unknown error occurred"
                        )
                    }
                    isStreaming = false
                }
            }
        }

        fun stopGeneration() {
            streamingJob?.cancel()
            aiService.cancelOngoingRequest()
            isStreaming = false
            val idx = messages.indexOfLast { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.STREAMING }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(
                    status = MessageStatus.COMPLETED,
                    content = messages[idx].content + "\n\n[Stopped by user]"
                )
            }
        }

        fun copyToClipboard(text: String) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("PhoneAgent Chat", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        PhoneAgentTheme(
            themeMode = themeMode,
            accentColor = accentColor
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                val pendingBrowserAction by toolExecutor.browserController.pendingActionRequest.collectAsState()
                if (pendingBrowserAction != null) {
                    val req = pendingBrowserAction!!
                    AlertDialog(
                        onDismissRequest = {
                            req.respond(BrowserConfirmationResult.DISMISSED)
                        },
                        title = {
                            Text(
                                text = if (req.isSensitive) "Security Alert: Action Blocked" else "Confirm Browser Action",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("An automated browser action was requested:")
                                Text("• Site: ${req.site}", style = MaterialTheme.typography.bodySmall)
                                Text("• Action: ${req.action.uppercase()}", style = MaterialTheme.typography.bodySmall)
                                Text("• Target: ${req.target}", style = MaterialTheme.typography.bodySmall)
                                if (req.proposedValue.isNotBlank()) {
                                    Text("• Proposed Value: ${req.proposedValue}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (req.isSensitive) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "⚠️ SENSITIVE ACTION BLOCKED: ${req.sensitiveReason ?: "Password, OTP, or payment interaction detected"}. Automated interaction is disabled for credentials and payments. You may only deny this request.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            // Never offer Allow for password, OTP or payment fields identified by DOM type/autocomplete, or for payment submission
                            if (!req.isSensitive) {
                                Button(
                                    onClick = { req.respond(BrowserConfirmationResult.ALLOWED) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Allow")
                                }
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { req.respond(BrowserConfirmationResult.DENIED) }
                            ) {
                                Text(if (req.isSensitive) "Dismiss" else "Deny")
                            }
                        }
                    )
                }

                Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                    when (screen) {
                        CurrentScreen.CHAT -> {
                            val activeProv = keyStoreManager.getSelectedProvider()
                            val hasKey = keyStoreManager.getActiveApiKey().isNotBlank() || activeProv == com.phoneagent.ai.data.model.AiProvider.CUSTOM
                            ChatScreen(
                                currentSession = currentSession,
                                sessions = sessions,
                                messages = messages,
                                inputText = inputText,
                                onInputTextChanged = { inputText = it },
                                onSendMessage = { sendMessage() },
                                onStopGeneration = { stopGeneration() },
                                isStreaming = isStreaming,
                                skills = skills,
                                selectedSkill = selectedSkill,
                                onSelectSkill = { selectedSkill = it },
                                onNewSession = { createNewSession() },
                                onSelectSession = { switchSession(it) },
                                onDeleteSession = { deleteSession(it) },
                                onOpenSettings = { currentScreen = CurrentScreen.SETTINGS },
                                onOpenSkills = { currentScreen = CurrentScreen.SKILLS },
                                onOpenMemory = { currentScreen = CurrentScreen.MEMORY },
                                onOpenBrowser = { currentScreen = CurrentScreen.BROWSER },
                                onAttachFile = {
                                    pickFile { item -> attachments.add(item) }
                                },
                                onAttachCamera = {
                                    takeCameraPhoto { item -> attachments.add(item) }
                                },
                                onStartMic = {
                                    startInComposerRecording {
                                        priorDraftText = inputText
                                        voiceManager.startListening(
                                            voiceLang = voiceLanguage,
                                            onFinalResult = { recognized ->
                                                val combined = VoiceManager.combineDraftWithSpokenText(priorDraftText, recognized)
                                                inputText = combined
                                            },
                                            onError = { err ->
                                                Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                isVoiceRecording = isListening,
                                rmsLevel = rmsLevel,
                                voiceTranscript = voiceTranscript,
                                speechError = speechError,
                                onCancelVoice = {
                                    voiceManager.cancelListening()
                                    inputText = priorDraftText
                                },
                                onStopVoice = {
                                    val liveText = voiceManager.liveTranscript.value
                                    voiceManager.stopListening { finalText ->
                                        val textToKeep = finalText.ifBlank { liveText }
                                        val combined = VoiceManager.combineDraftWithSpokenText(priorDraftText, textToKeep)
                                        inputText = combined
                                    }
                                },
                                onSendVoice = {
                                    val liveText = voiceManager.liveTranscript.value
                                    voiceManager.stopListening { finalText ->
                                        val textToSend = finalText.ifBlank { liveText }
                                        val combined = VoiceManager.combineDraftWithSpokenText(priorDraftText, textToSend)
                                        if (combined.isNotBlank() || attachments.isNotEmpty()) {
                                            inputText = combined
                                            scope.launch {
                                                val outcome = voiceCommandDispatcher.dispatchVoiceTranscript(combined)
                                                when (outcome) {
                                                    is VoiceActionOutcome.Executed -> {
                                                        Toast.makeText(this@MainActivity, "${outcome.actionName}: ${outcome.resultMessage}", Toast.LENGTH_LONG).show()
                                                        if (outcome.actionName == "Browse" || outcome.actionName == "Web Search") {
                                                            currentScreen = CurrentScreen.BROWSER
                                                        }
                                                        inputText = ""
                                                    }
                                                    is VoiceActionOutcome.RouteToAgent -> {
                                                        inputText = outcome.prompt
                                                        sendMessage()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                attachments = attachments,
                                onRemoveAttachment = { attachments.remove(it) },
                                onCopyText = { copyToClipboard(it) },
                                onSpeakText = { speak(it) },
                                activeProviderName = activeProv.displayName,
                                activeModelName = keyStoreManager.getActiveModel(),
                                hasApiKey = hasKey,
                                voiceLanguage = voiceLanguage,
                                onCycleVoiceLanguage = {
                                    val next = when (voiceLanguage) {
                                        VoiceLanguage.AUTO -> VoiceLanguage.EN_IN
                                        VoiceLanguage.EN_IN -> VoiceLanguage.HI_IN
                                        VoiceLanguage.HI_IN -> VoiceLanguage.AUTO
                                    }
                                    voiceLanguage = next
                                    keyStoreManager.setVoiceLanguage(next)
                                },
                                isSpeaking = isSpeaking
                            )
                        }
                        CurrentScreen.SETTINGS -> {
                            SettingsScreen(
                                keyStoreManager = keyStoreManager,
                                aiService = aiService,
                                onBack = { currentScreen = CurrentScreen.CHAT },
                                onThemeChanged = {
                                    themeMode = keyStoreManager.getThemeMode()
                                    accentColor = keyStoreManager.getAccentColor()
                                }
                            )
                        }
                        CurrentScreen.SKILLS -> {
                            SkillsScreen(
                                skills = skills,
                                selectedSkill = selectedSkill,
                                onSelectSkill = {
                                    selectedSkill = it
                                    currentScreen = CurrentScreen.CHAT
                                },
                                onBack = { currentScreen = CurrentScreen.CHAT }
                            )
                        }
                        CurrentScreen.MEMORY -> {
                            MemoryScreen(
                                memories = memories,
                                onAddMemory = { k, c, cat ->
                                    scope.launch {
                                        val m = com.phoneagent.ai.data.model.MemoryItem(key = k, content = c, category = cat)
                                        chatStorage.saveMemory(m)
                                        memories.clear()
                                        memories.addAll(chatStorage.getMemories())
                                    }
                                },
                                onDeleteMemory = { id ->
                                    scope.launch {
                                        chatStorage.deleteMemory(id)
                                        memories.clear()
                                        memories.addAll(chatStorage.getMemories())
                                    }
                                },
                                onClearAllMemories = {
                                    scope.launch {
                                        chatStorage.clearMemories()
                                        memories.clear()
                                    }
                                },
                                onBack = { currentScreen = CurrentScreen.CHAT }
                            )
                        }
                        CurrentScreen.BROWSER -> {
                            BrowserScreen(
                                browserController = toolExecutor.browserController,
                                onBack = { currentScreen = CurrentScreen.CHAT },
                                onSendPromptToAgent = { prompt ->
                                    inputText = prompt
                                    currentScreen = CurrentScreen.CHAT
                                    sendMessage()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
