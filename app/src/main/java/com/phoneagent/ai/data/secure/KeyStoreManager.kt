package com.phoneagent.ai.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.phoneagent.ai.data.model.AccentColor
import com.phoneagent.ai.data.model.AiProvider
import com.phoneagent.ai.data.model.ChannelStatus
import com.phoneagent.ai.data.model.ComposioConfig
import com.phoneagent.ai.data.model.SmsConfig
import com.phoneagent.ai.data.model.TelegramConfig
import com.phoneagent.ai.data.model.ThemeMode
import com.phoneagent.ai.data.model.WhatsAppConfig

class KeyStoreManager(private val context: Context) {

    var keystoreError: String? = null
        private set

    init {
        // Security Defense: Purge any legacy plaintext fallback preferences that may have existed
        try {
            context.deleteSharedPreferences("phone_agent_fallback_prefs")
        } catch (e: Exception) {
            // Ignore if file doesn't exist
        }
    }

    // Strictly Encrypted SharedPreferences backed by Android Keystore (AES-256 GCM + SIV)
    // NEVER falls back to plaintext. If Keystore fails, securePrefs is null and key operations fail closed.
    private val securePrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "phone_agent_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            keystoreError = "Hardware Keystore unavailable (${e.javaClass.simpleName}: ${e.message}). Plaintext key storage is strictly disabled."
            null
        }
    }

    // Standard SharedPreferences for non-sensitive UI settings (theme, accent, voice language, model cache)
    private val uiPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("phone_agent_ui_prefs", Context.MODE_PRIVATE)
    }

    val isKeystoreAvailable: Boolean
        get() = (securePrefs != null)

    // Provider selection (non-sensitive UI preference)
    fun getSelectedProvider(): AiProvider {
        val name = uiPrefs.getString("selected_provider", AiProvider.GROQ.name) ?: AiProvider.GROQ.name
        return try {
            AiProvider.valueOf(name)
        } catch (e: Exception) {
            AiProvider.GROQ
        }
    }

    fun setSelectedProvider(provider: AiProvider) {
        uiPrefs.edit().putString("selected_provider", provider.name).apply()
    }

    // Provider Keys - STRICTLY KEPT IN SECURE PREFS. Fails closed if Keystore unavailable.
    fun getGroqKey(): String = securePrefs?.getString("groq_api_key", "") ?: ""
    fun setGroqKey(key: String): Boolean {
        val p = securePrefs ?: return false
        p.edit().putString("groq_api_key", key.trim()).apply()
        return true
    }

    fun getOpenRouterKey(): String = securePrefs?.getString("openrouter_api_key", "") ?: ""
    fun setOpenRouterKey(key: String): Boolean {
        val p = securePrefs ?: return false
        p.edit().putString("openrouter_api_key", key.trim()).apply()
        return true
    }

    fun getCustomKey(): String = securePrefs?.getString("custom_api_key", "") ?: ""
    fun setCustomKey(key: String): Boolean {
        val p = securePrefs ?: return false
        p.edit().putString("custom_api_key", key.trim()).apply()
        return true
    }

    fun getCustomBaseUrl(): String = uiPrefs.getString("custom_base_url", "http://10.0.2.2:8000/v1") ?: "http://10.0.2.2:8000/v1"
    fun setCustomBaseUrl(url: String) = uiPrefs.edit().putString("custom_base_url", url.trim()).apply()

    // Dynamic model lists (non-sensitive cached strings)
    fun getAvailableModels(provider: AiProvider): List<String> {
        val cached = uiPrefs.getString("cached_models_${provider.name}", "") ?: ""
        if (cached.isNotEmpty()) {
            try {
                val arr = org.json.JSONArray(cached)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // fallback
            }
        }
        return provider.defaultModels
    }

    fun setFetchedModels(provider: AiProvider, models: List<String>) {
        val arr = org.json.JSONArray()
        models.forEach { arr.put(it) }
        uiPrefs.edit().putString("cached_models_${provider.name}", arr.toString()).apply()
    }

    // Model selection
    fun getSelectedModel(provider: AiProvider): String {
        val key = "model_${provider.name}"
        val defaultModel = if (provider == AiProvider.OPENROUTER) "openrouter/free" else provider.defaultModels.first()
        return uiPrefs.getString(key, defaultModel) ?: defaultModel
    }

    fun setSelectedModel(provider: AiProvider, model: String) {
        val cleanModel = model.trim()
        uiPrefs.edit().putString("model_${provider.name}", cleanModel).apply()
    }

    // Current active API key based on selected provider (fails closed if keystore unavailable)
    fun getActiveApiKey(): String {
        if (!isKeystoreAvailable) return ""
        return when (getSelectedProvider()) {
            AiProvider.GROQ -> getGroqKey()
            AiProvider.OPENROUTER -> getOpenRouterKey()
            AiProvider.CUSTOM -> getCustomKey()
        }
    }

    // Active Base URL
    fun getActiveBaseUrl(): String {
        return when (val provider = getSelectedProvider()) {
            AiProvider.GROQ -> provider.defaultBaseUrl
            AiProvider.OPENROUTER -> provider.defaultBaseUrl
            AiProvider.CUSTOM -> getCustomBaseUrl().ifEmpty { provider.defaultBaseUrl }
        }
    }

    // Active model
    fun getActiveModel(): String {
        return getSelectedModel(getSelectedProvider())
    }

    // Tool loop settings
    fun getBoundedToolLoopMaxIterations(): Int = uiPrefs.getInt("bounded_tool_loop_max", 5)
    fun setBoundedToolLoopMaxIterations(max: Int) = uiPrefs.edit().putInt("bounded_tool_loop_max", max.coerceIn(1, 10)).apply()

    fun isToolLoopEnabled(): Boolean = uiPrefs.getBoolean("tool_loop_enabled", true)
    fun setToolLoopEnabled(enabled: Boolean) = uiPrefs.edit().putBoolean("tool_loop_enabled", enabled).apply()

    // Composio Settings: API key in securePrefs, others in uiPrefs
    fun getComposioConfig(): ComposioConfig {
        return ComposioConfig(
            apiKey = securePrefs?.getString("composio_api_key", "") ?: "",
            entityId = uiPrefs.getString("composio_entity_id", "default") ?: "default",
            isEnabled = uiPrefs.getBoolean("composio_enabled", false),
            connectedApps = listOf("GitHub", "Slack", "Google Calendar", "Notion", "Gmail")
        )
    }

    fun saveComposioConfig(config: ComposioConfig) {
        securePrefs?.edit()?.putString("composio_api_key", config.apiKey.trim())?.apply()
        uiPrefs.edit()
            .putString("composio_entity_id", config.entityId.trim())
            .putBoolean("composio_enabled", config.isEnabled)
            .apply()
    }

    // Telegram Bot Settings: Token in securePrefs, others in uiPrefs
    fun getTelegramConfig(): TelegramConfig {
        val token = securePrefs?.getString("telegram_bot_token", "") ?: ""
        val chatId = uiPrefs.getString("telegram_chat_id", "") ?: ""
        val statusStr = uiPrefs.getString("telegram_status", ChannelStatus.NOT_CONFIGURED.name) ?: ChannelStatus.NOT_CONFIGURED.name
        val status = try { ChannelStatus.valueOf(statusStr) } catch (e: Exception) { ChannelStatus.NOT_CONFIGURED }
        val msg = uiPrefs.getString("telegram_status_msg", "Not configured") ?: "Not configured"
        return TelegramConfig(token, chatId, status, msg)
    }

    fun saveTelegramConfig(config: TelegramConfig) {
        securePrefs?.edit()?.putString("telegram_bot_token", config.botToken.trim())?.apply()
        uiPrefs.edit()
            .putString("telegram_chat_id", config.chatId.trim())
            .putString("telegram_status", config.status.name)
            .putString("telegram_status_msg", config.statusMessage)
            .apply()
    }

    // WhatsApp Settings: Token in securePrefs, others in uiPrefs
    fun getWhatsAppConfig(): WhatsAppConfig {
        val phoneId = uiPrefs.getString("wa_phone_id", "") ?: ""
        val token = securePrefs?.getString("wa_token", "") ?: ""
        val verify = uiPrefs.getString("wa_verify", "") ?: ""
        val statusStr = uiPrefs.getString("wa_status", ChannelStatus.NOT_CONFIGURED.name) ?: ChannelStatus.NOT_CONFIGURED.name
        val status = try { ChannelStatus.valueOf(statusStr) } catch (e: Exception) { ChannelStatus.NOT_CONFIGURED }
        val msg = uiPrefs.getString("wa_status_msg", "Not configured") ?: "Not configured"
        return WhatsAppConfig(phoneId, token, verify, status, msg)
    }

    fun saveWhatsAppConfig(config: WhatsAppConfig) {
        securePrefs?.edit()?.putString("wa_token", config.accessToken.trim())?.apply()
        uiPrefs.edit()
            .putString("wa_phone_id", config.phoneNumberId.trim())
            .putString("wa_verify", config.verifyToken.trim())
            .putString("wa_status", config.status.name)
            .putString("wa_status_msg", config.statusMessage)
            .apply()
    }

    // SMS Settings: Twilio Auth in securePrefs, others in uiPrefs
    fun getSmsConfig(): SmsConfig {
        val enabled = uiPrefs.getBoolean("sms_enabled", false)
        val mode = uiPrefs.getString("sms_mode", "NATIVE_INTENT") ?: "NATIVE_INTENT"
        val sid = uiPrefs.getString("sms_twilio_sid", "") ?: ""
        val auth = securePrefs?.getString("sms_twilio_auth", "") ?: ""
        val fromNum = uiPrefs.getString("sms_twilio_from", "") ?: ""
        val statusStr = uiPrefs.getString("sms_status", ChannelStatus.NOT_CONFIGURED.name) ?: ChannelStatus.NOT_CONFIGURED.name
        val status = try { ChannelStatus.valueOf(statusStr) } catch (e: Exception) { ChannelStatus.NOT_CONFIGURED }
        val msg = uiPrefs.getString("sms_status_msg", "Not configured") ?: "Not configured"
        return SmsConfig(enabled, mode, sid, auth, fromNum, status, msg)
    }

    fun saveSmsConfig(config: SmsConfig) {
        securePrefs?.edit()?.putString("sms_twilio_auth", config.twilioAuthToken.trim())?.apply()
        uiPrefs.edit()
            .putBoolean("sms_enabled", config.enabled)
            .putString("sms_mode", config.mode)
            .putString("sms_twilio_sid", config.twilioAccountSid.trim())
            .putString("sms_twilio_from", config.twilioFromNumber.trim())
            .putString("sms_status", config.status.name)
            .putString("sms_status_msg", config.statusMessage)
            .apply()
    }

    // Theme & Accent (non-sensitive UI settings)
    fun getThemeMode(): ThemeMode {
        val name = uiPrefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        uiPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun getAccentColor(): AccentColor {
        val name = uiPrefs.getString("accent_color", AccentColor.CYAN.name) ?: AccentColor.CYAN.name
        return try {
            AccentColor.valueOf(name)
        } catch (e: Exception) {
            AccentColor.CYAN
        }
    }

    fun setAccentColor(accent: AccentColor) {
        uiPrefs.edit().putString("accent_color", accent.name).apply()
    }

    // Voice & Language preferences (non-sensitive UI settings)
    fun getVoiceLanguage(): com.phoneagent.ai.data.model.VoiceLanguage {
        val code = uiPrefs.getString("voice_language", com.phoneagent.ai.data.model.VoiceLanguage.AUTO.code)
            ?: com.phoneagent.ai.data.model.VoiceLanguage.AUTO.code
        return com.phoneagent.ai.data.model.VoiceLanguage.fromCode(code)
    }

    fun setVoiceLanguage(lang: com.phoneagent.ai.data.model.VoiceLanguage) {
        uiPrefs.edit().putString("voice_language", lang.code).apply()
    }

    fun isTtsAutoReadEnabled(): Boolean {
        return uiPrefs.getBoolean("tts_auto_read", false)
    }

    fun setTtsAutoReadEnabled(enabled: Boolean) {
        uiPrefs.edit().putBoolean("tts_auto_read", enabled).apply()
    }
}
