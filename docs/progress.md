# Paradox AI — Native Android AI Agent Beta

## Overview
**Paradox AI** (formerly PhoneAgent) is a fully autonomous, privacy-first Android AI Agent beta application built natively with Jetpack Compose, Kotlin coroutines, Android Keystore encryption, and OkHttp SSE streaming. It provides an official Sherlock Holmes detective robot launcher icon & adaptive artwork, in-app agentic browser with parallel tab management, phone hardware tools (flashlight and dialer confirmation), multilingual voice commands and spoken replies (English and Hindi), everyday assistant skills (Daily Planner, Executive Summary, Research Brief, Document Creator), and genuine on-device document creation & editing (native Android `PdfDocument` pagination and pure Kotlin OpenXML ZIP PPTX generation) without managed paid plans, middleware, telemetry, or personal credential reads.

---

## Deliverables & Acceptance Status

| Feature / Requirement | Status | Implementation Details |
|---|---|---|
| **App Branding & Name** | ✅ Complete | Renamed to **Paradox AI** (`com.phoneagent.ai`) in `strings.xml`, `buildConfigField`, TopAppBar, and greeting headers |
| **Official App Icon & Artwork** | ✅ Complete | Applied user-provided 512px artwork (`Desktop/1-paradox-ai-icon.png`). Generated full legacy mipmap suite (`mdpi`–`xxxhdpi`) + adaptive foreground with 72% inner safe zone padding and `#0C0D11` background layer |
| **In-App Compose Logo** | ✅ Complete | Replaced AutoAwesome placeholder with `R.drawable.ic_paradox_logo` in welcome card (`ChatScreen.kt`) |
| **Android APK Compilation** | ✅ Complete | Compiled `app-debug.apk` (18.2 MB) using Android SDK 35, Gradle 9.6.0, Kotlin 2.0.21, Compose 2024.11.00 |
| **Automated Testing Suite** | ✅ Complete | **38 / 38 Tests Passed (100% Success)**: 28 connected tests on emulator + 10 JVM unit tests |
| **In-Composer Voice Recording Bar** | ✅ Complete | In-composer recording bar matching reference UX: Cancel X (restores typed draft), live editable transcript preview + 11-bar RMS-driven dynamic waveform equalizer, Stop square (finalizes speech to editable draft), and Send arrow (submits only after explicit tap) |
| **Draft Preservation Guarantee** | ✅ Complete | Cancelling dictation never discards prior user text; stopping keeps combined text in composer for editing; zero auto-submission on silence |
| **Bilingual Voice Language Selector** | ✅ Complete | Explicit selector for English (`en-IN`), Hindi (`hi-IN`), and Auto-Detect (`AUTO`). Chat input quick-chip badge (`EN`/`HI`/`AUTO`) + dedicated Settings card. Honest disclosure: mixed Hindi/English code-switching depends on installed speech recognition engine |
| **Speech-to-Text (`SpeechRecognizer`)** | ✅ Complete | Uses Android `SpeechRecognizer` with runtime `RECORD_AUDIO` permission. Includes manifest package visibility queries (`RecognitionService`). Provides clear in-app guidance if speech services are missing (no silent failure) |
| **Spoken Output & Audio Focus** | ✅ Complete | `VoiceManager` requests audio focus (`USAGE_ASSISTANT` with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`). Background audio ducks cleanly; releases focus immediately on stop/finish |
| **Zero Secret Spoken Guarantee** | ✅ Complete | Sanitizes API keys (`gsk_...`, `sk-or-v1-...`, `sk-...`), passwords, credential tokens, `[DOCUMENT_ATTACHMENT_JSON]` blocks, and code blocks before speaking |
| **Instant TTS Stop & Sync** | ✅ Complete | Reactive `isSpeaking` state flow synchronized with UI; one-tap instant "Stop Speaking" button in message bubbles |
| **TTS Language Availability Checks** | ✅ Complete | Verifies `isLanguageAvailable` for target locale before speech generation; avoids silent fallback to English for Hindi text; provides direct intent to Android TTS voice data settings |
| **Hindi Unicode Integrity** | ✅ Complete | Devanagari Unicode script survives transcripts, chat storage, and native PDF documents without mojibake |
| **Everyday Document Synthesis** | ✅ Complete | On-device multi-page PDF generation via `PdfDocument` and genuine editable OpenXML presentation (`.pptx`) generation via pure Kotlin ZIP stream without heavy third-party binaries |
| **PdfRenderer Verification** | ✅ Complete | Generated PDFs are verified in real time via `android.graphics.pdf.PdfRenderer` to guarantee page count and corruption-free rendering |
| **Document Result Cards in Chat** | ✅ Complete | Styled squircle card in chat bubbles parsing `[DOCUMENT_ATTACHMENT_JSON]` with file type badge (PDF/PPTX), size, page/slide count, and "Open" / "Share" buttons |
| **FileProvider Secure Sharing** | ✅ Complete | Files stored in app sandbox (`context.filesDir/documents/`) and opened/shared via `FileProvider.getUriForFile` with `FLAG_GRANT_READ_URI_PERMISSION` |
| **Everyday Assistant Skills** | ✅ Complete | Added 4 MIT everyday assistant skills: `daily-planner.md`, `executive-summary.md`, `research-brief.md`, `document-creator.md` |
| **Native In-App Agentic Browser** | ✅ Complete | Multi-tab parallel controller (`AgenticBrowserController`), parallel tab strip, URL navigation, scroll, click, fill, clean semantic DOM observation |
| **Browser Security Defenses** | ✅ Complete | Prompt-injection defense tags (`<untrusted_web_content url="...">`), password and credit card redaction (`[REDACTED_SECRET]`), strict SSL rejection (`handler.cancel()`, zero bypass) |
| **Purchase & Payment Safety** | ✅ Complete | Form submissions or button clicks containing payment/purchase keywords are flagged with `ACTION_REQUIRES_CONFIRMATION` |
| **Phone Flashlight Tool** | ✅ Complete | Controlled via `CameraManager.setTorchMode` with hardware flash detection and runtime permission handling |
| **Phone Call Tool** | ✅ Complete | Uses `Intent.ACTION_DIAL` with pre-filled number. Strictly requires human confirmation on device dialer (no silent background dialing) |
| **Voice Command Mode (EN + HI)** | ✅ Complete | Multilingual parser for English and Hindi voice commands ("turn on flashlight" / "टॉर्च चालू करो", "call [number]" / "कॉल करो [number]", "search [query]" / "खोजो [query]", and fallback to agent LLM) |
| **Document Text Extraction** | ✅ Complete | Pure Kotlin `ZipInputStream` slide XML parser for PowerPoint `.pptx` and bounded text stream parser for `.pdf`, wrapped in `<untrusted_user_file>` boundaries |
| **Theme System (Dark, Light, AMOLED)** | ✅ Complete | Default Dark (`#111215`), Light (`#F7F8FA`), and AMOLED (`#000000` pitch black). Soft squircle radii (`16dp` - `24dp`), 6 free selectable accents |
| **Groq Provider (Default)** | ✅ Complete | Base `https://api.groq.com/openai/v1`, dynamic `/models` fetching, default `llama-3.3-70b-versatile` |
| **OpenRouter Provider** | ✅ Complete | Base `https://openrouter.ai/api/v1`, default `openrouter/free`, strict free-tier policy (**never paid fallback**) |
| **Custom FreeLLMAPI Endpoint** | ✅ Complete | User-hosted private instance support for FreeLLMAPI (`https://github.com/Paradox-ultra/freellmapi`), default emulator base `http://10.0.2.2:8000/v1` |
| **Keystore Encryption & Security** | ✅ Complete | `EncryptedSharedPreferences` backed by Android Keystore (AES-256 GCM + SIV). Excluded keys from device/cloud backups (`data_extraction_rules.xml`, `backup_rules.xml`), logs, and APK binaries |
| **Explicit Clipboard Access** | ✅ Complete | Clipboard read only on explicit user tap of "Paste Key" (no background or passive clipboard monitoring) |
| **Native Bounded Tool Loop** | ✅ Complete | 21 native tools with configurable bound (1–10 iterations, default 5) and strict token protection limits |
| **QA Report & Contact Sheet** | ✅ Complete | Generated `qa.html`, `contact_sheet.html`, `test-results.txt`, and verified on live emulator |

---

## Artifacts & Links
- **APK Path**: [app-debug.apk](file:///c:/Users/chinm/Documents/PhoneAgent/app/build/outputs/apk/debug/app-debug.apk) (18.2 MB)
- **Official App Icon**: [paradox-ai-icon.png](file:///c:/Users/chinm/Documents/PhoneAgent/paradox-ai-icon.png) (512x512 RGB)
- **System App Info Screenshot**: [screenshot_paradox_app_info_icon.png](file:///c:/Users/chinm/Documents/PhoneAgent/screenshot_paradox_app_info_icon.png) (Adaptive Icon with Circular Mask)
- **Chat Welcome Logo Screenshot**: [screenshot_paradox_new_icon_chat.png](file:///c:/Users/chinm/Documents/PhoneAgent/screenshot_paradox_new_icon_chat.png) (Detective Robot Welcome Logo)
- **In-Composer Voice Recording Bar Screenshot**: [screenshot_voice_recording_bar_waveform.png](file:///c:/Users/chinm/Documents/PhoneAgent/screenshot_voice_recording_bar_waveform.png) (Cancel X, 11-Bar RMS Waveform, Stop Square & Send Arrow)
- **QA Report**: [qa.html](file:///c:/Users/chinm/Documents/PhoneAgent/qa.html)
- **Sample PDF**: [sample_executive_summary.pdf](file:///c:/Users/chinm/Documents/PhoneAgent/sample_executive_summary.pdf) (59.9 KB)
- **Sample PPTX**: [sample_product_roadmap.pptx](file:///c:/Users/chinm/Documents/PhoneAgent/sample_product_roadmap.pptx) (3.9 KB)
- **Rendered PDF Preview**: [sample_pdf_preview.png](file:///c:/Users/chinm/Documents/PhoneAgent/sample_pdf_preview.png) (83.8 KB)
- **Test Results**: [test-results.txt](file:///c:/Users/chinm/Documents/PhoneAgent/test-results.txt)
- **Status Metadata**: [status.json](file:///c:/Users/chinm/Documents/PhoneAgent/status.json)
- **Build Notes**: [build-notes.md](file:///c:/Users/chinm/Documents/PhoneAgent/build-notes.md)
