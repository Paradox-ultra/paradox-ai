package com.phoneagent.ai.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneagent.ai.data.api.AiService
import com.phoneagent.ai.data.model.AccentColor
import com.phoneagent.ai.data.model.AiProvider
import com.phoneagent.ai.data.model.ChannelStatus
import com.phoneagent.ai.data.model.ComposioConfig
import com.phoneagent.ai.data.model.SmsConfig
import com.phoneagent.ai.data.model.TelegramConfig
import com.phoneagent.ai.data.model.ThemeMode
import com.phoneagent.ai.data.model.WhatsAppConfig
import com.phoneagent.ai.data.secure.KeyStoreManager
import com.phoneagent.ai.ui.components.SoftCard
import com.phoneagent.ai.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    keyStoreManager: KeyStoreManager,
    aiService: AiService,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTheme by remember { mutableStateOf(keyStoreManager.getThemeMode()) }
    var selectedAccent by remember { mutableStateOf(keyStoreManager.getAccentColor()) }

    var selectedVoiceLanguage by remember { mutableStateOf(keyStoreManager.getVoiceLanguage()) }
    var isTtsAutoRead by remember { mutableStateOf(keyStoreManager.isTtsAutoReadEnabled()) }

    var selectedProvider by remember { mutableStateOf(keyStoreManager.getSelectedProvider()) }
    var groqKey by remember { mutableStateOf(keyStoreManager.getGroqKey()) }
    var openRouterKey by remember { mutableStateOf(keyStoreManager.getOpenRouterKey()) }
    var customKey by remember { mutableStateOf(keyStoreManager.getCustomKey()) }
    var customBaseUrl by remember { mutableStateOf(keyStoreManager.getCustomBaseUrl()) }

    var availableModels by remember { mutableStateOf(keyStoreManager.getAvailableModels(selectedProvider)) }
    var selectedModel by remember { mutableStateOf(keyStoreManager.getSelectedModel(selectedProvider)) }
    var showApiKey by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var modelFetchStatus by remember { mutableStateOf<String?>(null) }

    var boundedToolLoopMax by remember { mutableIntStateOf(keyStoreManager.getBoundedToolLoopMaxIterations()) }
    var isToolLoopEnabled by remember { mutableStateOf(keyStoreManager.isToolLoopEnabled()) }

    // Composio
    var composioConfig by remember { mutableStateOf(keyStoreManager.getComposioConfig()) }

    // Telegram
    var telegramConfig by remember { mutableStateOf(keyStoreManager.getTelegramConfig()) }
    var isTestingTelegram by remember { mutableStateOf(false) }

    // WhatsApp
    var whatsAppConfig by remember { mutableStateOf(keyStoreManager.getWhatsAppConfig()) }

    // SMS
    var smsConfig by remember { mutableStateOf(keyStoreManager.getSmsConfig()) }

    fun pasteFromClipboard(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true || clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)) {
            val item = clipboard.primaryClip?.getItemAt(0)
            return item?.text?.toString()?.trim() ?: ""
        }
        return ""
    }

    fun openUrl(url: String) {
        if (url.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Agent Settings",
                    style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- 1. APPEARANCE & ACCENTS ---
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Appearance & Accents",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    )

                    // Theme Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.values().forEach { mode ->
                            val isSelected = selectedTheme == mode
                            val shape = RoundedCornerShape(12.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(if (isSelected) colors.accent else colors.surfaceVariant)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
                                    .clickable {
                                        selectedTheme = mode
                                        keyStoreManager.setThemeMode(mode)
                                        onThemeChanged()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.DARK -> "Dark (Default)"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.AMOLED -> "AMOLED"
                                    },
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colors.onAccent else colors.textPrimary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Free Accent Colors",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                    )

                    // Accent Colors Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AccentColor.values().forEach { acc ->
                            val isSelected = selectedAccent == acc
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(acc.hex))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) colors.textPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedAccent = acc
                                        keyStoreManager.setAccentColor(acc)
                                        onThemeChanged()
                                    }
                            )
                        }
                    }
                }
            }

            // --- 2. VOICE & LANGUAGE PREFERENCES ---
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Voice & Spoken Replies",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                    Text(
                        text = "Configure voice recognition and speech synthesis language for English and Hindi commands and spoken replies.",
                        style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                    )

                    Text(
                        text = "Voice Recognition & TTS Language",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                    )

                    // Language Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.phoneagent.ai.data.model.VoiceLanguage.entries.forEach { lang ->
                            val isSelected = selectedVoiceLanguage == lang
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(if (isSelected) colors.surfaceVariant else Color.Transparent)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
                                    .clickable {
                                        selectedVoiceLanguage = lang
                                        keyStoreManager.setVoiceLanguage(lang)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) colors.accent else Color.Transparent)
                                        .border(2.dp, if (isSelected) colors.accent else colors.textMuted, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = lang.displayName,
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) colors.textPrimary else colors.textSecondary
                                        )
                                    )
                                    Text(
                                        text = when (lang) {
                                            com.phoneagent.ai.data.model.VoiceLanguage.EN_IN -> "Optimized for Indian English accent & phrasing"
                                            com.phoneagent.ai.data.model.VoiceLanguage.HI_IN -> "शुद्ध हिंदी व देवनागरी लिपि के लिए अनुकूलित"
                                            com.phoneagent.ai.data.model.VoiceLanguage.AUTO -> "Detects Devanagari script or falls back to system locale"
                                        },
                                        style = TextStyle(fontSize = 11.sp, color = colors.textMuted)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // TTS Opt-in Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Read Responses Aloud",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                            )
                            Text(
                                text = "Automatically speak assistant completions (Audio focus & secret redaction enforced)",
                                style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                            )
                        }
                        Switch(
                            checked = isTtsAutoRead,
                            onCheckedChange = {
                                isTtsAutoRead = it
                                keyStoreManager.setTtsAutoReadEnabled(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent
                            )
                        )
                    }

                    // Bilingual code-switch disclaimer
                    Text(
                        text = "⚠️ Note: Mixed Hindi/English code-switching depends on the installed recognition engine (e.g. Google Speech Services). Guaranteed bilingual auto-detection is not claimed across third-party engines.",
                        style = TextStyle(fontSize = 11.sp, color = colors.textMuted, lineHeight = 16.sp)
                    )

                    // System TTS Settings helper
                    OutlinedButton(
                        onClick = {
                            try {
                                val ttsIntent = Intent("com.android.settings.TTS_SETTINGS")
                                context.startActivity(ttsIntent)
                            } catch (e: Exception) {
                                openUrl("market://details?id=com.google.android.tts")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Android Text-to-Speech Settings (Voice Packs)", fontSize = 12.sp)
                    }
                }
            }

            // --- 3. AI PROVIDER & ENCRYPTED KEYS ---
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LLM Provider & Keystore Encryption",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        )
                    }

                    Text(
                        text = "Keys are encrypted in Android Keystore / AES-256. Never sent to any 3rd party or telemetry server.",
                        style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                    )

                    // Provider Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiProvider.values().forEach { prov ->
                            val isSelected = selectedProvider == prov
                            val shape = RoundedCornerShape(12.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(if (isSelected) colors.accent else colors.surfaceVariant)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
                                    .clickable {
                                        selectedProvider = prov
                                        keyStoreManager.setSelectedProvider(prov)
                                        availableModels = keyStoreManager.getAvailableModels(prov)
                                        selectedModel = keyStoreManager.getSelectedModel(prov)
                                        modelFetchStatus = null
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (prov) {
                                        AiProvider.GROQ -> "Groq (Default)"
                                        AiProvider.OPENROUTER -> "OpenRouter"
                                        AiProvider.CUSTOM -> "FreeLLMAPI"
                                    },
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colors.onAccent else colors.textPrimary
                                    )
                                )
                            }
                        }
                    }

                    // Fail-closed Keystore warning if hardware keystore is unavailable
                    if (!keyStoreManager.isKeystoreAvailable) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(colors.error.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(1.dp, colors.error.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "⚠️ Hardware Keystore Unavailable",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.error,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = keyStoreManager.keystoreError ?: "Android Keystore hardware encryption is unavailable on this device. For your security, plaintext key storage is strictly disabled. API keys cannot be saved.",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Key Input & Paste Key
                    val activeKey = when (selectedProvider) {
                        AiProvider.GROQ -> groqKey
                        AiProvider.OPENROUTER -> openRouterKey
                        AiProvider.CUSTOM -> customKey
                    }

                    OutlinedTextField(
                        value = activeKey,
                        onValueChange = { newKey ->
                            when (selectedProvider) {
                                AiProvider.GROQ -> {
                                    groqKey = newKey
                                    keyStoreManager.setGroqKey(newKey)
                                }
                                AiProvider.OPENROUTER -> {
                                    openRouterKey = newKey
                                    keyStoreManager.setOpenRouterKey(newKey)
                                }
                                AiProvider.CUSTOM -> {
                                    customKey = newKey
                                    keyStoreManager.setCustomKey(newKey)
                                }
                            }
                        },
                        label = { Text("${selectedProvider.displayName} API Key") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = colors.textMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedLabelColor = colors.accent,
                            unfocusedLabelColor = colors.textMuted
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Explicit Paste Key and Provider Key-Page Link Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pasted = pasteFromClipboard()
                                if (pasted.isNotEmpty()) {
                                    when (selectedProvider) {
                                        AiProvider.GROQ -> {
                                            groqKey = pasted
                                            keyStoreManager.setGroqKey(pasted)
                                        }
                                        AiProvider.OPENROUTER -> {
                                            openRouterKey = pasted
                                            keyStoreManager.setOpenRouterKey(pasted)
                                        }
                                        AiProvider.CUSTOM -> {
                                            customKey = pasted
                                            keyStoreManager.setCustomKey(pasted)
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Key", fontSize = 13.sp)
                        }

                        if (selectedProvider.keyManagementUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = { openUrl(selectedProvider.keyManagementUrl) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Get API Key", fontSize = 13.sp)
                            }
                        } else if (selectedProvider == AiProvider.CUSTOM) {
                            OutlinedButton(
                                onClick = { openUrl("https://github.com/Paradox-ultra/freellmapi") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("FreeLLMAPI GitHub", fontSize = 12.sp)
                            }
                        }
                    }

                    // Provider details / policy notice
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = when (selectedProvider) {
                                AiProvider.GROQ -> "⚡ Groq: Ultra-fast LPU inference (keys: console.groq.com/keys/). Model list can be dynamically fetched below."
                                AiProvider.OPENROUTER -> "🛡️ OpenRouter Policy: Strict free models only (never paid fallback). Default: openrouter/free (keys: openrouter.ai/keys)."
                                AiProvider.CUSTOM -> "💻 FreeLLMAPI: User-hosted private local endpoint (not a shared service). Runs locally via paradox-ultra/freellmapi."
                            },
                            style = TextStyle(fontSize = 12.sp, color = colors.textSecondary)
                        )
                    }

                    // Custom Base URL field (if Custom selected)
                    if (selectedProvider == AiProvider.CUSTOM) {
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = {
                                customBaseUrl = it
                                keyStoreManager.setCustomBaseUrl(it)
                            },
                            label = { Text("Base URL (e.g. http://10.0.2.2:8000/v1)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Model Fetch Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                isFetchingModels = true
                                modelFetchStatus = "Fetching models from ${selectedProvider.displayName}..."
                                scope.launch {
                                    val result = aiService.fetchModels(selectedProvider)
                                    isFetchingModels = false
                                    result.onSuccess { models ->
                                        availableModels = models
                                        modelFetchStatus = "Successfully fetched ${models.size} models."
                                    }.onFailure { err ->
                                        modelFetchStatus = "Fetch failed: ${err.message}"
                                    }
                                }
                            },
                            enabled = !isFetchingModels,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accent
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (selectedProvider) {
                                    AiProvider.GROQ -> "Fetch Groq Models"
                                    AiProvider.OPENROUTER -> "Fetch Free Models"
                                    AiProvider.CUSTOM -> "Fetch Endpoint Models"
                                },
                                fontSize = 13.sp
                            )
                        }

                        if (modelFetchStatus != null) {
                            Text(
                                text = modelFetchStatus!!,
                                style = TextStyle(fontSize = 11.sp, color = if (modelFetchStatus!!.startsWith("Error") || modelFetchStatus!!.startsWith("Fetch failed")) colors.error else colors.accent),
                                modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                            )
                        }
                    }

                    // Model selection
                    Text(
                        text = "Selected Model",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                    )

                    val candidateModels = remember(availableModels, selectedModel, selectedProvider) {
                        val baseList = (if (availableModels.isNotEmpty()) availableModels else selectedProvider.defaultModels).toMutableList()
                        if (selectedModel.isNotBlank() && !baseList.contains(selectedModel)) {
                            baseList.add(0, selectedModel)
                        }
                        baseList
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        candidateModels.take(8).forEach { modelName ->
                            val isSelected = selectedModel == modelName
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(if (isSelected) colors.surfaceVariant else Color.Transparent)
                                    .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
                                    .clickable {
                                        selectedModel = modelName
                                        keyStoreManager.setSelectedModel(selectedProvider, modelName)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) colors.accent else Color.Transparent)
                                        .border(2.dp, if (isSelected) colors.accent else colors.textMuted, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = modelName,
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary
                                    )
                                )
                            }
                        }

                        // Custom Model Input Field
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {
                                selectedModel = it
                                keyStoreManager.setSelectedModel(selectedProvider, it)
                            },
                            label = { Text("Custom Model ID") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.border
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // --- 3. NATIVE BOUNDED TOOL LOOP & MEMORY ---
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Native Bounded Tool Loop",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Native Tools",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                            )
                            Text(
                                text = "Calculator, Datetime, Device Info, Web Fetch, Memory",
                                style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                            )
                        }
                        Switch(
                            checked = isToolLoopEnabled,
                            onCheckedChange = {
                                isToolLoopEnabled = it
                                keyStoreManager.setToolLoopEnabled(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent
                            )
                        )
                    }

                    if (isToolLoopEnabled) {
                        Text(
                            text = "Max Bounded Loop Iterations: $boundedToolLoopMax",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                        )
                        Slider(
                            value = boundedToolLoopMax.toFloat(),
                            onValueChange = {
                                boundedToolLoopMax = it.toInt()
                                keyStoreManager.setBoundedToolLoopMaxIterations(boundedToolLoopMax)
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent
                            )
                        )
                    }
                }
            }

            // --- 4. COMPOSIO SETTINGS ---
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Composio Integration",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                    Text(
                        text = "Honest setup: Connect external tools (GitHub, Slack, Calendar) via your personal Composio API key.",
                        style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Composio Tools",
                            style = TextStyle(fontSize = 14.sp, color = colors.textPrimary)
                        )
                        Switch(
                            checked = composioConfig.isEnabled,
                            onCheckedChange = {
                                composioConfig = composioConfig.copy(isEnabled = it)
                                keyStoreManager.saveComposioConfig(composioConfig)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent
                            )
                        )
                    }

                    OutlinedTextField(
                        value = composioConfig.apiKey,
                        onValueChange = {
                            composioConfig = composioConfig.copy(apiKey = it)
                            keyStoreManager.saveComposioConfig(composioConfig)
                        },
                        label = { Text("Composio API Key") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pasted = pasteFromClipboard()
                                if (pasted.isNotEmpty()) {
                                    composioConfig = composioConfig.copy(apiKey = pasted)
                                    keyStoreManager.saveComposioConfig(composioConfig)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { openUrl("https://app.composio.dev/settings") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dashboard", fontSize = 13.sp)
                        }
                    }
                }
            }

            // --- 5. OPTIONAL CHANNELS SETUP (TELEGRAM / WHATSAPP / SMS) ---
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Messaging Channels (Honest Setup)",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                    Text(
                        text = "Real channel links with genuine credential verification. No fake 'Connected' toggles.",
                        style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                    )

                    // Telegram Bot
                    Text(
                        text = "• Telegram Bot Gateway",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    )
                    OutlinedTextField(
                        value = telegramConfig.botToken,
                        onValueChange = {
                            telegramConfig = telegramConfig.copy(
                                botToken = it,
                                status = if (it.isBlank()) ChannelStatus.NOT_CONFIGURED else ChannelStatus.CONFIGURED_UNTESTED,
                                statusMessage = if (it.isBlank()) "Not configured" else "Token set (untested)"
                            )
                            keyStoreManager.saveTelegramConfig(telegramConfig)
                        },
                        label = { Text("Telegram Bot Token (from @BotFather)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Status and Test Ping Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ${telegramConfig.statusMessage}",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = when (telegramConfig.status) {
                                    ChannelStatus.VERIFIED_ONLINE -> colors.success
                                    ChannelStatus.ERROR -> colors.error
                                    else -> colors.textMuted
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = {
                                if (telegramConfig.botToken.isBlank()) return@OutlinedButton
                                isTestingTelegram = true
                                scope.launch(Dispatchers.IO) {
                                    val token = telegramConfig.botToken.trim()
                                    try {
                                        val testClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
                                        val req = Request.Builder().url("https://api.telegram.org/bot$token/getMe").build()
                                        testClient.newCall(req).execute().use { resp ->
                                            val body = resp.body?.string() ?: ""
                                            val json = JSONObject(body)
                                            val ok = json.optBoolean("ok", false)
                                            if (ok) {
                                                val botUser = json.getJSONObject("result").optString("username", "Bot")
                                                telegramConfig = telegramConfig.copy(
                                                    status = ChannelStatus.VERIFIED_ONLINE,
                                                    statusMessage = "Verified Online (@$botUser)"
                                                )
                                            } else {
                                                telegramConfig = telegramConfig.copy(
                                                    status = ChannelStatus.ERROR,
                                                    statusMessage = "Auth failed: ${json.optString("description")}"
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        telegramConfig = telegramConfig.copy(
                                            status = ChannelStatus.ERROR,
                                            statusMessage = "Connection failed: ${e.message}"
                                        )
                                    }
                                    keyStoreManager.saveTelegramConfig(telegramConfig)
                                    isTestingTelegram = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isTestingTelegram) "Testing..." else "Test Token", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // WhatsApp Cloud API
                    Text(
                        text = "• WhatsApp Cloud API",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    )
                    OutlinedTextField(
                        value = whatsAppConfig.phoneNumberId,
                        onValueChange = {
                            whatsAppConfig = whatsAppConfig.copy(
                                phoneNumberId = it,
                                status = if (it.isBlank()) ChannelStatus.NOT_CONFIGURED else ChannelStatus.CONFIGURED_UNTESTED,
                                statusMessage = if (it.isBlank()) "Not configured" else "Configured"
                            )
                            keyStoreManager.saveWhatsAppConfig(whatsAppConfig)
                        },
                        label = { Text("Phone Number ID") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // SMS Setup
                    Text(
                        text = "• SMS Gateway (Twilio / Native)",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable SMS Gateway",
                            style = TextStyle(fontSize = 13.sp, color = colors.textPrimary)
                        )
                        Switch(
                            checked = smsConfig.enabled,
                            onCheckedChange = {
                                smsConfig = smsConfig.copy(enabled = it)
                                keyStoreManager.saveSmsConfig(smsConfig)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccent,
                                checkedTrackColor = colors.accent
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
