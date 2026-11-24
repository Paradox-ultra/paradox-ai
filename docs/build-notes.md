# Paradox AI — Reproducible Build Notes & Architecture Specification

## 1. Environment & Prerequisites
- **Project Name**: Paradox AI (formerly PhoneAgent)
- **Application ID / Package**: `com.phoneagent.ai`
- **Official Launcher Artwork**: 512x512 PNG supplied at `Desktop/1-paradox-ai-icon.png`
- **Operating System**: Windows 11 / Linux / macOS
- **JDK**: Java 17+ (Compiled and tested using Java 25 JBR bundled with Android Studio)
- **Android SDK**: API 35 (Android 15)
- **Build Tools**: 35.0.0
- **Gradle**: 9.6.0 wrapper (`gradlew.bat` / `./gradlew`)
- **Android Gradle Plugin (AGP)**: 8.7.2
- **Kotlin**: 2.0.21 with Compose Compiler Plugin
- **Jetpack Compose BOM**: `2024.11.00`
- **Device Tested**: Android Emulator (`emulator-5554`, AVD `Recall_R24c_Fresh_20261117_1509` - API 37.0)

---

## 2. Reproducible Build Steps

From the repository root (`c:\Users\chinm\Documents\PhoneAgent`):

### Compile Debug APK:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\chinm\AppData\Local\Android\Sdk"
.\gradlew.bat assembleDebug
```
Output artifact: `app/build/outputs/apk/debug/app-debug.apk` (~18.2 MB)

### Run JVM Unit Tests (10 Tests):
```powershell
.\gradlew.bat testDebugUnitTest
```
Runs OpenAI schema validation, untrusted input boundary tests, PPTX in-memory ZIP extraction, PDF text extraction, document attachment JSON parsing, pure OpenXML ZIP packaging validation, TTS secret sanitization, Hindi Devanagari transcript serialization, RMS dB normalization, and voice draft preservation rules without requiring an active device.

### Run Connected Tests on Emulator (28 Tests):
Ensure an emulator or physical device is online via `adb devices`:
```powershell
.\gradlew.bat connectedDebugAndroidTest
```
Executes all 28 deterministic test cases covering localhost MockWebServer SSE streaming, bounded tool loop, cancellation, offline recovery, multi-tab browser controller, voice command dispatches (English + Hindi), CameraManager flashlight, dialer intent with human confirmation, native `PdfDocument` pagination with `PdfRenderer` verification, OpenXML PPTX generation, voice language selector intent building, TTS availability checks, Devanagari script PDF synthesis, official Paradox AI launcher icon validation, and in-composer voice recording flow with partial result streaming.

### Install on Device:
```powershell
& "C:\Users\chinm\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
& "C:\Users\chinm\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.phoneagent.ai/.MainActivity
```

---

## 3. Official App Icon & Adaptive Asset Suite

### Sourced Artwork
- User-provided 512px artwork located at `Desktop/1-paradox-ai-icon.png` (via `Environment.GetFolderPath(SpecialFolder.Desktop)`).
- Clean 512x512 RGB artwork: Sherlock Holmes robot detective with deerstalker hat and red pipe. Zero external captions or watermarks.

### Adaptive Foreground & Mipmap Generation
- **Adaptive Canvas**: 108dp x 108dp.
- **Safe Zone Centering**: Artwork scaled to 72% inner safe zone with 14% transparent padding on all edges (`ic_launcher_foreground.png`). Guarantees zero clipping of hat, eyes, body, or pipe when circular, squircle, or teardrop OEM masks are applied by Android launchers.
- **Background Layer**: Vector drawable `res/drawable/ic_launcher_background.xml` tinted `#0C0D11` to seamlessly match Paradox AI's deep dark surface.
- **Density Assets Generated**:
  - `mipmap-mdpi`: 48x48 launcher, 108x108 adaptive foreground (78px art)
  - `mipmap-hdpi`: 72x72 launcher, 162x162 adaptive foreground (116px art)
  - `mipmap-xhdpi`: 96x96 launcher, 216x216 adaptive foreground (156px art)
  - `mipmap-xxhdpi`: 144x144 launcher, 324x324 adaptive foreground (234px art)
  - `mipmap-xxxhdpi`: 192x192 launcher, 432x432 adaptive foreground (312px art)
- **In-App Compose Logo**: Sourced as direct raster `res/drawable-nodpi/ic_paradox_logo.png` (bypassing Compose's painterResource adaptive-icon XML constraint).

---

## 4. Bilingual Voice & Speech Architecture (`VoiceManager.kt`)

### In-Composer Voice Recording Bar UX
- **In-Composer Bar Replacement**: When dictation starts, the composer bar is replaced with a dedicated voice recording bar:
  - **Left**: Cancel (`Icons.Default.Close` `X`) button immediately aborts speech recognition and strictly restores the user's previously typed draft text intact.
  - **Center**: Real RMS-driven 11-bar dynamic equalizer (`VoiceRmsWaveform`) responding live to input decibels (`((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)`), with live partial transcript preview.
  - **Right**: Stop (`Icons.Default.Stop` `Square`) finalizes speech into an editable draft in the composer without auto-submitting; Send (`Icons.Default.Send` `Arrow`) sends recognized text only upon explicit user tap.
- **Draft Preservation Guarantee**:
  - Cancel `X` restores the draft text that was in the composer before the mic was tapped.
  - Stop `Square` appends recognized speech to any pre-existing text so the user can edit or review before sending.
  - Send is never automatically triggered upon silence; it strictly requires an explicit user tap.

### Language Configuration & Recognition
- **Explicit Language Selector**: Supports English (`en-IN`), Hindi (`hi-IN`), and Auto-Detect (`AUTO`).
- **Quick Chip Badge**: In-chat toggle badge (`EN` / `HI` / `AUTO`) positioned adjacent to the microphone button, plus dedicated configuration in Settings.
- **Bilingual Code-Switching Boundary**: Mixed Hindi/English code-switching depends on the installed speech recognition service (e.g. Google Speech Services); Paradox AI does not claim guaranteed universal bilingual auto-detection across all arbitrary engines.
- **Android SpeechRecognizer Integration**:
  - Uses `EXTRA_LANGUAGE` and `EXTRA_LANGUAGE_PREFERENCE` for precise locale targeting.
  - Requests partial recognition results via `EXTRA_PARTIAL_RESULTS`.
  - Manifest queries include `android.speech.RecognitionService` for API 30+ package visibility.
  - Runtime `RECORD_AUDIO` permission checks with descriptive rationale.
  - **No Silent Failures**: Returns clear in-app guidance if speech recognition services are missing, with direct intents to install Google Speech Services or launch system voice input settings.

### Spoken Output & Text-To-Speech (TTS)
- **Audio Focus Management**:
  - Employs `AudioAttributes.USAGE_ASSISTANT` with `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
  - Background audio ducks smoothly during speech.
  - Focus is immediately and safely released on completion, error, or user interrupt.
- **Strict Zero-Secret Speaking**:
  - `VoiceManager.sanitizeTextForTts()` strips all API keys (`gsk_...`, `sk-or-v1-...`, `sk-...`), passwords, credential tokens, `[DOCUMENT_ATTACHMENT_JSON]` receipt blocks, and markdown code blocks before text reaches the audio synthesizer.
- **Instant Speech Cancellation**:
  - Reactive `isSpeaking` state flow synchronizes with UI.
  - Instant one-tap "Stop Speaking" button in active chat bubbles.
  - Automatically ceases speech if user navigates between sessions or enters settings.
- **Language Availability Verification**:
  - Calls `tts.isLanguageAvailable(targetLocale)` before speaking.
  - Prevents silent fallback to wrong or mismatched languages. Displays actionable alerts if a voice pack is missing, with a shortcut to Android TTS settings.

---

## 5. Everyday Document Engine (`PdfDocumentGenerator.kt` & `PptxPresentationGenerator.kt`)

### Native PDF Generation
- Built with Android's native `android.graphics.pdf.PdfDocument`.
- Standard A4 page dimensions: 595 x 842 points.
- Header, body, and bullet point formatting with automatic word wrap.
- Dynamic page allocation with page overflow detection and bottom margin guards.
- Automatic page numbering ("Page X of Y") and timestamped footer metadata.
- Supports Devanagari Unicode rendering without font corruption or crashes.
- Real-time `android.graphics.pdf.PdfRenderer` verification before returning file handles to confirm zero corrupt structures.
- Appended version generation preserves original files while appending new sections with timestamps.

### Genuine Editable OpenXML PowerPoint Generation
- Pure Kotlin implementation using standard `java.util.zip.ZipOutputStream` with zero heavyweight third-party binaries.
- Generates standards-compliant OpenXML ZIP packages:
  - `[Content_Types].xml` (slide and presentation types)
  - `_rels/.rels` (root relationship manifest)
  - `ppt/presentation.xml` (widescreen 16:9 format: 9,144,000 x 5,143,500 EMUs)
  - `ppt/_rels/presentation.xml.rels`
  - `ppt/slides/slide{N}.xml` with `<p:sp>`, `<p:txBody>`, `<a:p>`, `<a:r>`, and `<a:t>` tags.
- Fully compatible with and editable in Microsoft PowerPoint, Google Slides, and Apple Keynote.

### Android FileProvider & Secure Sharing
- Files are saved in app-specific isolated storage (`context.filesDir/documents/`).
- Android `FileProvider` (`com.phoneagent.ai.fileprovider`) exposes `content://` URIs.
- `FLAG_GRANT_READ_URI_PERMISSION` is attached to open and share intents.
- Interactive squircle cards in chat bubbles allow instant "Open" with system viewers or "Share" via Android system share sheet.

---

## 6. Honest Scope & Disclaimers

1. **No Hermes Parity Claim**: Paradox AI is an independent mobile agent beta designed for personal everyday productivity; it does not claim parity with Nous Research Hermes or other expansive multi-agent server frameworks.
2. **No Adobe Acrobat Pro Parity Claim**: Paradox AI supports structured on-device document generation, multi-page pagination, and section appending. It does NOT support arbitrary vector PDF layout reflow, OCR font replacement, or pre-existing form XObject manipulation.
3. **No Microsoft Office Parity Claim**: Paradox AI produces valid, editable widescreen OpenXML slide structures with text frames and bullet points. It does NOT support macro-enabled VBA code (`.pptm`) or 3D animations.
4. **Speech Service Dependencies**: Android `SpeechRecognizer` requires an installed speech recognition engine. Paradox AI transparently guides the user if an emulator or device lacks Google Speech Services.
5. **Zero Paid Telemetry or Leaks**: 100% of keys are stored solely in local Android Keystore-backed AES-256 storage and excluded from Android backup manifests. Zero telemetry or network tracking exists.
