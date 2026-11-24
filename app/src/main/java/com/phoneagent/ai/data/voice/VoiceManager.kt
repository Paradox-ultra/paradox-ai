package com.phoneagent.ai.data.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.phoneagent.ai.data.model.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TtsLanguageStatus {
    AVAILABLE,
    MISSING_DATA,
    NOT_SUPPORTED
}

data class SpeechServiceStatus(
    val isRecognitionAvailable: Boolean,
    val missingServiceMessage: String? = null,
    val settingsIntent: Intent? = null
)

class VoiceManager(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    var isTtsReady: Boolean = false
        private set

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Real-time in-composer recognition state flows
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            setupUtteranceListener()
            onInitComplete?.invoke(true)
        } else {
            isTtsReady = false
            onInitComplete?.invoke(false)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                releaseAudioFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                releaseAudioFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                releaseAudioFocus()
            }
        })
    }

    // --- 1. SPEECH RECOGNITION CONFIGURATION ---

    fun checkSpeechRecognitionAvailable(): SpeechServiceStatus {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        return if (available) {
            SpeechServiceStatus(isRecognitionAvailable = true)
        } else {
            SpeechServiceStatus(
                isRecognitionAvailable = false,
                missingServiceMessage = "Speech recognition service is not available on this device. Install or enable 'Google Speech Recognition & Synthesis' in Android Settings.",
                settingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            )
        }
    }

    fun buildSpeechRecognizerIntent(voiceLang: VoiceLanguage): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            when (voiceLang) {
                VoiceLanguage.EN_IN -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in English (India)...")
                }
                VoiceLanguage.HI_IN -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "हिंदी में बोलें (Speak in Hindi)...")
                }
                VoiceLanguage.AUTO -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in English or Hindi (बोलें)...")
                }
            }
        }
    }

    fun startListening(
        voiceLang: VoiceLanguage,
        onFinalResult: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            _liveTranscript.value = ""
            _speechError.value = null
            _rmsLevel.value = 0.35f
            _isListening.value = true

            val status = checkSpeechRecognitionAvailable()
            if (!status.isRecognitionAvailable) {
                val msg = "Speech recognition service unavailable on device"
                _speechError.value = msg
                onError?.invoke(msg)
                return@post
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsLevel.value = normalizeRms(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val msg = formatSpeechError(error)
                        _speechError.value = msg
                        onError?.invoke(msg)
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: _liveTranscript.value
                        if (text.isNotBlank()) {
                            _liveTranscript.value = text
                            onFinalResult?.invoke(text)
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            _liveTranscript.value = text
                        }
                    }

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })

                val intent = buildSpeechRecognizerIntent(voiceLang).apply {
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _isListening.value = false
                _speechError.value = e.message ?: "Failed to start speech recognition"
                onError?.invoke(e.message ?: "Unknown speech error")
            }
        }
    }

    fun stopListening(onResult: ((String) -> Unit)? = null) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // Ignore
            }
            _isListening.value = false
            _rmsLevel.value = 0f
            onResult?.invoke(_liveTranscript.value)
        }
    }

    fun cancelListening() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                // Ignore
            }
            _isListening.value = false
            _rmsLevel.value = 0f
            _liveTranscript.value = ""
            _speechError.value = null
        }
    }

    fun reportPermissionDenied() {
        _isListening.value = true
        _speechError.value = "Microphone permission required"
    }

    // --- 2. TEXT-TO-SPEECH LANGUAGE CHECKS & SYNTHESIS ---

    fun checkTtsLanguageSupport(locale: Locale): TtsLanguageStatus {
        val engine = tts ?: return TtsLanguageStatus.NOT_SUPPORTED
        val availability = engine.isLanguageAvailable(locale)
        return when {
            availability >= TextToSpeech.LANG_AVAILABLE -> TtsLanguageStatus.AVAILABLE
            availability == TextToSpeech.LANG_MISSING_DATA -> TtsLanguageStatus.MISSING_DATA
            else -> TtsLanguageStatus.NOT_SUPPORTED
        }
    }

    fun resolveTargetLocale(text: String, voiceLang: VoiceLanguage): Locale {
        val hasHindiUnicode = text.any { it.code in 0x0900..0x097F }
        return when (voiceLang) {
            VoiceLanguage.HI_IN -> Locale("hi", "IN")
            VoiceLanguage.EN_IN -> Locale("en", "IN")
            VoiceLanguage.AUTO -> {
                if (hasHindiUnicode) Locale("hi", "IN") else Locale("en", "IN")
            }
        }
    }

    fun speak(
        rawText: String,
        voiceLang: VoiceLanguage,
        onMissingVoicePack: (message: String, installIntent: Intent) -> Unit
    ) {
        if (!isTtsReady || tts == null) return

        val targetLocale = resolveTargetLocale(rawText, voiceLang)
        val status = checkTtsLanguageSupport(targetLocale)

        when (status) {
            TtsLanguageStatus.AVAILABLE -> {
                tts?.language = targetLocale
                requestAudioFocus()
                val sanitized = sanitizeTextForTts(rawText)
                tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "ParadoxAiTTS_${System.currentTimeMillis()}")
            }
            TtsLanguageStatus.MISSING_DATA -> {
                // DO NOT SILENTLY FALL BACK TO WRONG LANGUAGE!
                val langName = if (targetLocale.language == "hi") "Hindi" else "Indian English"
                val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                onMissingVoicePack(
                    "$langName TTS voice pack is missing. Please download the voice data in Android Text-to-Speech settings to enable spoken replies.",
                    installIntent
                )
            }
            TtsLanguageStatus.NOT_SUPPORTED -> {
                // Current engine doesn't support the language
                val langName = if (targetLocale.language == "hi") "Hindi" else "Indian English"
                val settingsIntent = Intent("com.android.settings.TTS_SETTINGS")
                onMissingVoicePack(
                    "Your current Text-to-Speech engine does not support $langName. Install Google Speech Recognition & Synthesis for Hindi and Indian English support.",
                    settingsIntent
                )
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        releaseAudioFocus()
    }

    fun shutdown() {
        stopSpeaking()
        cancelListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    // --- 3. AUDIO FOCUS ---

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stopSpeaking()
                    }
                }
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stopSpeaking()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun releaseAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    // --- 4. ZERO SECRET SPEAKING POLICY ---

    companion object {
        fun sanitizeTextForTts(rawText: String): String {
            var clean = rawText

            // Redact document attachment receipts
            clean = clean.replace(Regex("""\[DOCUMENT_ATTACHMENT_JSON][\s\S]*?\[/DOCUMENT_ATTACHMENT_JSON]"""), "Generated document attached.")

            // Redact Groq API keys (gsk_...)
            clean = clean.replace(Regex("""\bgsk_[a-zA-Z0-9]{20,}\b"""), "[API key redacted]")

            // Redact OpenRouter API keys (sk-or-v1-...)
            clean = clean.replace(Regex("""\bsk-or-v1-[a-zA-Z0-9]{20,}\b"""), "[API key redacted]")

            // Redact general OpenAI-style keys (sk-...)
            clean = clean.replace(Regex("""\bsk-[a-zA-Z0-9]{20,}\b"""), "[API key redacted]")

            // Redact password or token value pairs
            clean = clean.replace(Regex("""(?i)\b(password|token|secret|api_key|apikey)\s*[:=]\s*\S+"""), "$1 redacted")

            // Redact explicit [REDACTED_SECRET]
            clean = clean.replace("[REDACTED_SECRET]", "[secret redacted]")

            // Strip markdown headers, lists, code fences
            clean = clean.replace(Regex("""```[\s\S]*?```"""), " [code block omitted] ")
            clean = clean.replace(Regex("""[*#`_~>]"""), "")

            // Normalize spaces
            return clean.replace(Regex("""\s+"""), " ").trim()
        }

        fun normalizeRms(rmsdB: Float): Float {
            // rmsdB typically ranges from -2dB to 10dB
            return ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
        }

        fun formatSpeechError(errorCode: Int): String {
            return when (errorCode) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                else -> "Speech recognition error ($errorCode)"
            }
        }

        fun combineDraftWithSpokenText(priorDraft: String, spokenText: String): String {
            val trimmedDraft = priorDraft.trim()
            val trimmedSpoken = spokenText.trim()
            return when {
                trimmedDraft.isBlank() -> trimmedSpoken
                trimmedSpoken.isBlank() -> trimmedDraft
                else -> "$trimmedDraft $trimmedSpoken"
            }
        }
    }
}
