package com.phoneagent.ai.data.voice

import com.phoneagent.ai.data.browser.AgenticBrowserController
import com.phoneagent.ai.data.hardware.PhoneHardwareController

sealed class VoiceActionOutcome {
    data class Executed(val actionName: String, val resultMessage: String) : VoiceActionOutcome()
    data class RouteToAgent(val prompt: String) : VoiceActionOutcome()
}

class VoiceCommandDispatcher(
    private val hardwareController: PhoneHardwareController,
    private val browserController: AgenticBrowserController
) {

    suspend fun dispatchVoiceTranscript(transcript: String): VoiceActionOutcome {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) {
            return VoiceActionOutcome.Executed("None", "Empty voice transcript received.")
        }

        val lower = trimmed.lowercase()

        // 1. Flashlight ON (English + Hindi / Hinglish)
        val isTorchOnCmd = Regex("""\b(turn\s+on|enable|activate|start)\s+(the\s+)?(flash\s*light|torch)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("""\b(टॉर्च|फ्लैशलाइट|लाइट|torch|flashlight|light)\s*(चालू|ऑन|जलाओ|khojo|on\s*karo|chalu\s*karo|jalao)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("""\b(चालू|ऑन)\s*करो\s*(टॉर्च|फ्लैशलाइट|लाइट|torch|flashlight)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                lower == "flashlight on" || lower == "torch on"

        if (isTorchOnCmd) {
            val res = hardwareController.setFlashlight(true)
            return VoiceActionOutcome.Executed("Flashlight ON", res)
        }

        // 2. Flashlight OFF (English + Hindi / Hinglish)
        val isTorchOffCmd = Regex("""\b(turn\s+off|disable|deactivate|stop)\s+(the\s+)?(flash\s*light|torch)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("""\b(टॉर्च|फ्लैशलाइट|लाइट|torch|flashlight|light)\s*(बंद|ऑफ|बुझाओ|off\s*karo|band\s*karo|bujhao)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("""\b(बंद|ऑफ)\s*करो\s*(टॉर्च|फ्लैशलाइट|लाइट|torch|flashlight)\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                lower == "flashlight off" || lower == "torch off"

        if (isTorchOffCmd) {
            val res = hardwareController.setFlashlight(false)
            return VoiceActionOutcome.Executed("Flashlight OFF", res)
        }

        // 3. Phone Call / Dial (English + Hindi: "call 555-0199", "राहुल को कॉल करो", "कॉल करो 9876543210")
        val callMatchEn = Regex("""\b(?:call|dial|phone)\s+([0-9+\s\-()]{3,}|[a-zA-Z\u0900-\u097F\s]+)""", RegexOption.IGNORE_CASE).find(trimmed)
        val callMatchHi = Regex("""(?:कॉल\s*करो|फोन\s*करो|फोन\s*लगाओ|कॉल\s*लगाओ)\s+([0-9+\s\-()]{3,}|[a-zA-Z\u0900-\u097F\s]+)""").find(trimmed)
            ?: Regex("""([0-9+\s\-()]{3,}|[a-zA-Z\u0900-\u097F\s]+)\s+को\s+(?:कॉल|फोन)\s*(?:करो|लगाओ)?""").find(trimmed)

        val targetCall = callMatchEn?.groupValues?.get(1)?.trim() ?: callMatchHi?.groupValues?.get(1)?.trim()
        if (targetCall != null && targetCall.isNotBlank()) {
            val res = hardwareController.prepareCall(targetCall)
            return VoiceActionOutcome.Executed("Call", res)
        }

        // 4. In-App Browser Navigation
        val browseMatch = Regex("""\b(?:open|browse|visit|go\s+to|खोलो)\s+(https?://\S+|\S+\.\S+)""", RegexOption.IGNORE_CASE).find(trimmed)
        if (browseMatch != null) {
            val rawUrl = browseMatch.groupValues[1].trim()
            val formatted = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) "https://$rawUrl" else rawUrl
            val res = browserController.navigate(formatted)
            return VoiceActionOutcome.Executed("Browse", res)
        }

        // 5. In-App Web Search (English + Hindi: "search for quantum", "सर्च करो क्वांटम", "खोजो भारत का इतिहास")
        val searchMatchEn = Regex("""\b(?:search|google|look\s+up)\s+(?:for\s+)?(.+)""", RegexOption.IGNORE_CASE).find(trimmed)
        val searchMatchHi = Regex("""(?:सर्च\s*करो|खोजो|ढूंढो|search\s*karo)\s+(.+)""", RegexOption.IGNORE_CASE).find(trimmed)
            ?: Regex("""(.+)\s+(?:के\s+बारे\s+में\s+खोजो|सर्च\s*करो)""").find(trimmed)

        val searchQuery = searchMatchEn?.groupValues?.get(1)?.trim() ?: searchMatchHi?.groupValues?.get(1)?.trim()
        if (searchQuery != null && searchQuery.isNotBlank()) {
            val searchUrl = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(searchQuery, "UTF-8")
            val res = browserController.navigate(searchUrl)
            return VoiceActionOutcome.Executed("Web Search", res)
        }

        // 6. Route to General Agent Prompt
        return VoiceActionOutcome.RouteToAgent(trimmed)
    }
}
