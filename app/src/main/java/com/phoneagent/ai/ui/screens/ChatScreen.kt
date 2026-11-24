package com.phoneagent.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.phoneagent.ai.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneagent.ai.data.model.AttachmentItem
import com.phoneagent.ai.data.model.ChatMessage
import com.phoneagent.ai.data.model.ChatSession
import com.phoneagent.ai.data.model.SkillItem
import com.phoneagent.ai.ui.components.ChatInputBar
import com.phoneagent.ai.ui.components.ChatMessageBubble
import com.phoneagent.ai.ui.components.SkillQuickChips
import com.phoneagent.ai.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentSession: ChatSession?,
    sessions: List<ChatSession>,
    messages: List<ChatMessage>,
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    isStreaming: Boolean,
    skills: List<SkillItem>,
    selectedSkill: SkillItem?,
    onSelectSkill: (SkillItem?) -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenBrowser: () -> Unit = {},
    onAttachFile: () -> Unit,
    onAttachCamera: () -> Unit,
    onStartMic: () -> Unit,
    attachments: List<AttachmentItem>,
    onRemoveAttachment: (AttachmentItem) -> Unit,
    onCopyText: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    activeProviderName: String,
    activeModelName: String,
    hasApiKey: Boolean,
    voiceLanguage: com.phoneagent.ai.data.model.VoiceLanguage = com.phoneagent.ai.data.model.VoiceLanguage.AUTO,
    onCycleVoiceLanguage: () -> Unit = {},
    isSpeaking: Boolean = false,
    isVoiceRecording: Boolean = false,
    rmsLevel: Float = 0f,
    voiceTranscript: String = "",
    speechError: String? = null,
    onCancelVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onSendVoice: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto scroll on new messages
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.surface,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Conversations",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            onNewSession()
                            scope.launch { drawerState.close() }
                        }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "New Chat", tint = colors.accent)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = colors.border)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sessions, key = { it.id }) { sess ->
                            val isSelected = sess.id == currentSession?.id
                            val shape = RoundedCornerShape(14.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(if (isSelected) colors.surfaceVariant else colors.surface)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
                                    .clickable {
                                        onSelectSession(sess)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sess.title,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary
                                    ),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteSession(sess.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Paradox AI",
                                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (hasApiKey) colors.success else colors.warning)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "$activeProviderName • $activeModelName",
                                    style = TextStyle(fontSize = 11.sp, color = colors.textSecondary)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Drawer", tint = colors.textPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenBrowser) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = "Browser", tint = colors.accent)
                        }
                        IconButton(onClick = onOpenMemory) {
                            Icon(imageVector = Icons.Default.Psychology, contentDescription = "Memory", tint = colors.textSecondary)
                        }
                        IconButton(onClick = onOpenSkills) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Skills", tint = colors.textSecondary)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = colors.textSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
                )
            },
            containerColor = colors.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {

                // Active Skill Banner (if selected)
                if (selectedSkill != null) {
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(shape)
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.accent, shape)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Active Skill: ${selectedSkill.name}",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accent),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Clear",
                            style = TextStyle(fontSize = 11.sp, color = colors.textMuted),
                            modifier = Modifier.clickable { onSelectSkill(null) }
                        )
                    }
                }

                // Chat Message List
                if (messages.isEmpty()) {
                    EmptyChatGreeting(
                        hasApiKey = hasApiKey,
                        activeProviderName = activeProviderName,
                        onOpenSettings = onOpenSettings,
                        onSelectPrompt = { onInputTextChanged(it) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                onCopyText = onCopyText,
                                onSpeakText = onSpeakText,
                                isSpeaking = isSpeaking
                            )
                        }
                    }
                }

                // Quick Skills Selector row
                SkillQuickChips(
                    skills = skills,
                    selectedSkill = selectedSkill,
                    onSelectSkill = onSelectSkill
                )

                // Bottom Input Bar
                ChatInputBar(
                    text = inputText,
                    onTextChanged = onInputTextChanged,
                    onSend = onSendMessage,
                    onStop = onStopGeneration,
                    isStreaming = isStreaming,
                    onAttachFile = onAttachFile,
                    onAttachCamera = onAttachCamera,
                    onStartMic = onStartMic,
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    voiceLanguage = voiceLanguage,
                    onCycleVoiceLanguage = onCycleVoiceLanguage,
                    isVoiceRecording = isVoiceRecording,
                    rmsLevel = rmsLevel,
                    voiceTranscript = voiceTranscript,
                    speechError = speechError,
                    onCancelVoice = onCancelVoice,
                    onStopVoice = onStopVoice,
                    onSendVoice = onSendVoice
                )
            }
        }
    }
}

@Composable
fun EmptyChatGreeting(
    hasApiKey: Boolean,
    activeProviderName: String,
    onOpenSettings: () -> Unit,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val iconShape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(iconShape)
                .background(colors.surface)
                .border(1.dp, colors.border, iconShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_paradox_logo),
                contentDescription = "Paradox AI Icon",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(iconShape)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to Paradox AI",
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your private, on-device autonomous AI agent with bounded tool calling, native browser tabs, and phone tools.",
            style = TextStyle(fontSize = 13.sp, color = colors.textSecondary, lineHeight = 18.sp),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasApiKey) {
            val warnShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(warnShape)
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.warning, warnShape)
                    .clickable { onOpenSettings() }
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "⚙️ Configure $activeProviderName API Key",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.warning)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap here to paste your API key in Settings. PhoneAgent communicates directly with your provider with zero intermediaries.",
                        style = TextStyle(fontSize = 12.sp, color = colors.textSecondary)
                    )
                }
            }
        } else {
            // Suggestion chips
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Check device info and battery status",
                    "Calculate (1024 * 768) / (30 * 60)",
                    "Run an autoplan review for a new mobile feature",
                    "Remember that my favorite programming language is Kotlin"
                ).forEach { suggestion ->
                    val sShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(sShape)
                            .background(colors.surface)
                            .border(1.dp, colors.border, sShape)
                            .clickable { onSelectPrompt(suggestion) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = suggestion,
                            style = TextStyle(fontSize = 13.sp, color = colors.textSecondary)
                        )
                    }
                }
            }
        }
    }
}
