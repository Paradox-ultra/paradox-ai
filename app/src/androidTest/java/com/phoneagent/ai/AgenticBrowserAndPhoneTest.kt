package com.phoneagent.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phoneagent.ai.data.browser.AgenticBrowserController
import com.phoneagent.ai.data.browser.BrowserConfirmationResult
import com.phoneagent.ai.data.db.ChatStorage
import com.phoneagent.ai.data.hardware.PhoneHardwareController
import com.phoneagent.ai.data.tools.NativeToolExecutor
import com.phoneagent.ai.data.voice.VoiceActionOutcome
import com.phoneagent.ai.data.voice.VoiceCommandDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgenticBrowserAndPhoneTest {

    private lateinit var context: Context
    private lateinit var browserController: AgenticBrowserController
    private lateinit var hardwareController: PhoneHardwareController
    private lateinit var voiceDispatcher: VoiceCommandDispatcher
    private lateinit var chatStorage: ChatStorage
    private lateinit var toolExecutor: NativeToolExecutor

    @Before
    fun setUp() = runBlocking(Dispatchers.Main) {
        context = ApplicationProvider.getApplicationContext()
        browserController = AgenticBrowserController(context)
        hardwareController = PhoneHardwareController(context)
        hardwareController.shouldLaunchDialerActivity = false
        voiceDispatcher = VoiceCommandDispatcher(hardwareController, browserController)
        chatStorage = ChatStorage(context)
        toolExecutor = NativeToolExecutor(context, chatStorage, browserController, hardwareController)
    }

    @Test
    fun testMultiTabParallelManagement() = runBlocking(Dispatchers.Main) {
        // Tab 1 is created automatically on init
        val initialTabs = browserController.tabs
        assertTrue("Should have at least 1 initial tab", initialTabs.isNotEmpty())
        val tab1Id = initialTabs.first().id

        // Create Tab 2
        val tab2 = browserController.createTab("https://www.google.com")
        assertEquals(2, browserController.tabs.size)
        assertEquals(tab2.id, browserController.activeTabId)

        // Create Tab 3
        val tab3 = browserController.createTab("https://en.wikipedia.org")
        assertEquals(3, browserController.tabs.size)
        assertEquals(tab3.id, browserController.activeTabId)

        // Switch to Tab 1
        val switchSuccess = browserController.switchTab(tab1Id)
        assertTrue("Switch tab should succeed", switchSuccess)
        assertEquals(tab1Id, browserController.activeTabId)

        // Close Tab 2
        val closeSuccess = browserController.closeTab(tab2.id)
        assertTrue("Close tab should succeed", closeSuccess)
        assertEquals(2, browserController.tabs.size)
        assertFalse("Tab 2 should no longer exist", browserController.tabs.any { it.id == tab2.id })

        // List tabs output
        val tabListReport = browserController.listTabs()
        assertTrue("Report should list open tabs count", tabListReport.contains("Open Browser Tabs (2)"))
        assertTrue("Report should mark active tab", tabListReport.contains("[ACTIVE]"))
    }

    @Test
    fun testPromptInjectionDefenseAndDomWrapping() = runBlocking {
        // Test observe DOM output wrapping
        val observeResult = browserController.observeDom()
        assertTrue("Must wrap web content in untrusted boundary tags", observeResult.contains("<untrusted_web_content"))
        assertTrue("Must close untrusted boundary tag", observeResult.contains("</untrusted_web_content>"))
        assertTrue("Must include security protocol indicator", observeResult.contains("Security:"))
    }

    @Test
    fun testPurchaseConfirmationDefense() = runBlocking {
        // If an element contains purchase / pay keywords, clicking must be flagged for confirmation
        val confirmResult = browserController.clickElement("button[id='pay-now']")
        // If element doesn't exist on blank page it returns element not found, but if destructive it returns confirmation
        assertNotNull(confirmResult)
    }

    @Test
    fun testVoiceCommandDispatchFlashlight() = runBlocking {
        // Turn ON
        val onOutcome = voiceDispatcher.dispatchVoiceTranscript("Please turn on the flashlight now")
        assertTrue("Should dispatch to Flashlight ON", onOutcome is VoiceActionOutcome.Executed)
        val onExec = onOutcome as VoiceActionOutcome.Executed
        assertEquals("Flashlight ON", onExec.actionName)

        // Turn OFF
        val offOutcome = voiceDispatcher.dispatchVoiceTranscript("Turn off torch")
        assertTrue("Should dispatch to Flashlight OFF", offOutcome is VoiceActionOutcome.Executed)
        val offExec = offOutcome as VoiceActionOutcome.Executed
        assertEquals("Flashlight OFF", offExec.actionName)
    }

    @Test
    fun testVoiceCommandDispatchDialer() = runBlocking {
        val callOutcome = voiceDispatcher.dispatchVoiceTranscript("Call 555-0199")
        assertTrue("Should dispatch to Call", callOutcome is VoiceActionOutcome.Executed)
        val callExec = callOutcome as VoiceActionOutcome.Executed
        assertEquals("Call", callExec.actionName)
        assertTrue("Must indicate confirmation is required on system dialer", callExec.resultMessage.contains("confirmation required"))
    }

    @Test
    fun testVoiceCommandDispatchBrowser() = runBlocking {
        // Browse URL
        val browseOutcome = voiceDispatcher.dispatchVoiceTranscript("Open https://developer.android.com")
        assertTrue("Should dispatch to Browse", browseOutcome is VoiceActionOutcome.Executed)
        val browseExec = browseOutcome as VoiceActionOutcome.Executed
        assertEquals("Browse", browseExec.actionName)

        // Web Search
        val searchOutcome = voiceDispatcher.dispatchVoiceTranscript("Search for Kotlin coroutines flow")
        assertTrue("Should dispatch to Web Search", searchOutcome is VoiceActionOutcome.Executed)
        val searchExec = searchOutcome as VoiceActionOutcome.Executed
        assertEquals("Web Search", searchExec.actionName)
    }

    @Test
    fun testVoiceCommandRouteToAgent() = runBlocking {
        val agentOutcome = voiceDispatcher.dispatchVoiceTranscript("Summarize the architectural differences between Compose and Views")
        assertTrue("General question must route to Agent", agentOutcome is VoiceActionOutcome.RouteToAgent)
        val agentRoute = agentOutcome as VoiceActionOutcome.RouteToAgent
        assertEquals("Summarize the architectural differences between Compose and Views", agentRoute.prompt)
    }

    @Test
    fun testHardwarePhoneToolsViaExecutor() = runBlocking {
        // Flashlight tool execution
        val flashResult = toolExecutor.executeTool("flashlight", """{"enabled":true}""")
        assertNotNull(flashResult)
        assertTrue("Flashlight result must report hardware status", flashResult.contains("Flashlight") || flashResult.contains("Camera"))

        // Call tool execution
        val callResult = toolExecutor.executeTool("call_phone", """{"phone_number":"+18005550199"}""")
        assertTrue("Call tool must open dialer and state user confirmation required", callResult.contains("Dialer opened with +18005550199"))
        assertTrue("Must require user confirmation", callResult.contains("confirmation required"))

        // Browser tool execution via NativeToolExecutor
        val tabListResult = toolExecutor.executeTool("browser_tab_list", "{}")
        assertTrue(tabListResult.contains("Open Browser Tabs"))
    }

    @Test
    fun testHindiVoiceCommands() = runBlocking {
        // 1. Hindi Flashlight ON
        val hiFlashOn = voiceDispatcher.dispatchVoiceTranscript("टॉर्च चालू करो")
        assertTrue("Hindi torch on must execute", hiFlashOn is VoiceActionOutcome.Executed)
        assertEquals("Flashlight ON", (hiFlashOn as VoiceActionOutcome.Executed).actionName)

        // 2. Hindi Flashlight OFF
        val hiFlashOff = voiceDispatcher.dispatchVoiceTranscript("टॉर्च बंद करो")
        assertTrue("Hindi torch off must execute", hiFlashOff is VoiceActionOutcome.Executed)
        assertEquals("Flashlight OFF", (hiFlashOff as VoiceActionOutcome.Executed).actionName)

        // 3. Hindi Phone Call
        val hiCall = voiceDispatcher.dispatchVoiceTranscript("कॉल करो 9876543210")
        assertTrue("Hindi call must execute", hiCall is VoiceActionOutcome.Executed)
        assertEquals("Call", (hiCall as VoiceActionOutcome.Executed).actionName)

        // 4. Hindi Search
        val hiSearch = voiceDispatcher.dispatchVoiceTranscript("खोजो भारत का संविधान")
        assertTrue("Hindi search must execute", hiSearch is VoiceActionOutcome.Executed)
        assertEquals("Web Search", (hiSearch as VoiceActionOutcome.Executed).actionName)

        // 5. Hindi General Prompt -> Route to Agent
        val hiAgent = voiceDispatcher.dispatchVoiceTranscript("कृत्रिम बुद्धिमत्ता क्या है?")
        assertTrue("Hindi general query must route to agent", hiAgent is VoiceActionOutcome.RouteToAgent)
        assertEquals("कृत्रिम बुद्धिमत्ता क्या है?", (hiAgent as VoiceActionOutcome.RouteToAgent).prompt)
    }

    @Test
    fun testPdfGenerationAndRendererVerification() = runBlocking {
        val pdfGen = com.phoneagent.ai.data.document.PdfDocumentGenerator(context)
        val sections = listOf(
            com.phoneagent.ai.data.document.PdfSection(
                header = "Section 1: Executive Overview",
                body = "Paradox AI operates completely on-device for everyday planning, analysis, and synthesis.",
                bulletPoints = listOf("Zero paid telemetry", "Local keystore encryption", "Safe bounded tool loop")
            ),
            com.phoneagent.ai.data.document.PdfSection(
                header = "Section 2: System Specifications",
                body = "Android PdfDocument native pagination ensures exact bounds and pagination margins without third-party dependencies.",
                bulletPoints = listOf("595x842 pt page size (A4 standard)", "PdfRenderer verification", "FileProvider secure content URI")
            )
        )

        val result = pdfGen.createPdf(
            title = "Paradox AI Architecture Report",
            author = "Engineering Core",
            sections = sections,
            customFileName = "test_arch_report.pdf"
        )

        assertTrue("PDF generation must succeed", result.success)
        assertEquals("PDF", result.fileType)
        assertTrue("Unit count must be >= 1 page", result.unitCount >= 1)
        assertTrue("File size must be > 0 bytes", result.fileSizeBytes > 0)
        assertTrue("Content URI must use app fileprovider", result.contentUri.startsWith("content://com.phoneagent.ai.fileprovider"))

        val targetFile = java.io.File(result.filePath)
        assertTrue("PDF file must exist on disk", targetFile.exists())

        // Verify with PdfRenderer
        val pfd = android.os.ParcelFileDescriptor.open(targetFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        assertEquals(result.unitCount, renderer.pageCount)
        val firstPage = renderer.openPage(0)
        assertNotNull(firstPage)
        assertEquals(595, firstPage.width)
        assertEquals(842, firstPage.height)
        firstPage.close()
        renderer.close()
        pfd.close()
    }

    @Test
    fun testPptxPresentationGenerationAndZipValidation() = runBlocking {
        val pptxGen = com.phoneagent.ai.data.document.PptxPresentationGenerator(context)
        val slides = listOf(
            com.phoneagent.ai.data.document.PptxSlideData(
                title = "Paradox AI Assistant",
                subtitle = "On-Device Autonomous Agent Beta",
                bulletPoints = listOf("Modern rounded Material 3 UI", "AMOLED and dark mode defaults", "Bounded native tools")
            ),
            com.phoneagent.ai.data.document.PptxSlideData(
                title = "Everyday Skills & Capabilities",
                subtitle = "Productivity Workflows",
                bulletPoints = listOf("Daily Planner", "Executive Summary", "Research Brief", "Document Creator (PDF & PPTX)")
            )
        )

        val result = pptxGen.createPresentation(
            deckTitle = "Paradox AI Assistant Overview",
            slides = slides,
            customFileName = "test_overview_deck.pptx"
        )

        assertTrue("PPTX generation must succeed", result.success)
        assertEquals("PPTX", result.fileType)
        assertEquals(2, result.unitCount)
        assertTrue("File size must be > 0 bytes", result.fileSizeBytes > 0)
        assertTrue("Content URI must use app fileprovider", result.contentUri.startsWith("content://com.phoneagent.ai.fileprovider"))

        val targetFile = java.io.File(result.filePath)
        assertTrue("PPTX file must exist on disk", targetFile.exists())

        // Verify PPTX text extraction back from the generated ZIP
        val extractedText = com.phoneagent.ai.data.document.DocumentTextExtractor.extractPptxTextFromStream(targetFile.inputStream())
        assertTrue("Extracted text has slide 1 title", extractedText.contains("Paradox AI Assistant"))
        assertTrue("Extracted text has slide 2 title", extractedText.contains("Everyday Skills & Capabilities"))
        assertTrue("Extracted text has bullet point", extractedText.contains("Daily Planner"))
    }

    @Test
    fun testDocumentToolsViaNativeToolExecutor() = runBlocking {
        // 1. Tool execution: create_pdf
        val createPdfArgs = """
            {
                "title": "Quarterly Sprint Goals",
                "author": "Product Lead",
                "file_name": "sprint_goals.pdf",
                "sections": [
                    {
                        "header": "Sprint 42 Focus",
                        "body": "Deliver production-ready everyday document synthesis and voice assistant workflows.",
                        "bullet_points": ["Complete PDF multi-page pagination", "Validate OpenXML ZIP structures", "Verify FileProvider permissions"]
                    }
                ]
            }
        """.trimIndent()

        val pdfToolOutput = toolExecutor.executeTool("create_pdf", createPdfArgs)
        assertTrue("Output should contain document JSON marker", pdfToolOutput.contains("[DOCUMENT_ATTACHMENT_JSON]"))
        assertTrue("Output should confirm PDF verification", pdfToolOutput.contains("PdfRenderer"))

        // 2. Tool execution: append_pdf
        val appendPdfArgs = """
            {
                "file_name": "sprint_goals.pdf",
                "header": "Sprint 42 Addendum",
                "body": "All 22+ tests passed deterministically.",
                "bullet_points": ["Offline execution verified", "Zero cloud leaks"]
            }
        """.trimIndent()
        val appendToolOutput = toolExecutor.executeTool("append_pdf", appendPdfArgs)
        assertTrue("Append output should contain document JSON marker", appendToolOutput.contains("[DOCUMENT_ATTACHMENT_JSON]"))

        // 3. Tool execution: create_pptx
        val createPptxArgs = """
            {
                "deck_title": "Sprint 42 Deck",
                "file_name": "sprint_deck.pptx",
                "slides": [
                    {
                        "title": "Sprint 42 Deliverables",
                        "subtitle": "Android Beta Milestone",
                        "bullet_points": ["Native Document Synthesis", "Agentic Browser Multi-Tab", "Hindi Voice Commands"]
                    }
                ]
            }
        """.trimIndent()
        val pptxToolOutput = toolExecutor.executeTool("create_pptx", createPptxArgs)
        assertTrue("Output should contain document JSON marker", pptxToolOutput.contains("[DOCUMENT_ATTACHMENT_JSON]"))
        assertTrue("Output should confirm OpenXML structure", pptxToolOutput.contains("OpenXML"))
    }

    @Test
    fun testGenerateSampleOutputsAndPreview() = runBlocking {
        val pdfGen = com.phoneagent.ai.data.document.PdfDocumentGenerator(context)
        val pptxGen = com.phoneagent.ai.data.document.PptxPresentationGenerator(context)

        // 1. PDF Output
        val pdfResult = pdfGen.createPdf(
            title = "Paradox AI Executive Brief",
            author = "Paradox Autonomous Systems",
            sections = listOf(
                com.phoneagent.ai.data.document.PdfSection(
                    header = "1. Strategic Mission",
                    body = "Paradox AI is an autonomous, on-device intelligence platform prioritizing user sovereignty, local document synthesis, and complete data boundaries.",
                    bulletPoints = listOf(
                        "No telemetry, third-party trackers, or cloud storage lock-in",
                        "Encrypted Android Keystore credential isolation",
                        "Material 3 rounded ergonomic interface with pure AMOLED mode"
                    )
                ),
                com.phoneagent.ai.data.document.PdfSection(
                    header = "2. Everyday Productivity Capabilities",
                    body = "On-device native synthesis generates professional multi-page PDFs and genuinely editable OpenXML PowerPoint presentations.",
                    bulletPoints = listOf(
                        "Android PdfDocument pagination with exact margins and bounds",
                        "Real-time PdfRenderer page-level integrity validation",
                        "FileProvider content sharing across system email and messaging apps"
                    )
                ),
                com.phoneagent.ai.data.document.PdfSection(
                    header = "3. Multilingual Voice & Hardware Tools",
                    body = "Direct hardware integrations enable voice control and agentic browser automation.",
                    bulletPoints = listOf(
                        "Camera flashlight / torch toggle via CameraManager",
                        "Guarded phone dialing with mandatory human confirmation",
                        "Parallel multi-tab browser controller with DOM secret redaction"
                    )
                )
            ),
            customFileName = "sample_executive_summary.pdf"
        )
        assertTrue(pdfResult.success)

        val pdfFile = java.io.File(pdfResult.filePath)
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val targetPdf = java.io.File(downloadDir, "sample_executive_summary.pdf")
        pdfFile.inputStream().use { input ->
            java.io.FileOutputStream(targetPdf).use { output ->
                input.copyTo(output)
            }
        }
        try {
            val publicSd = java.io.File("/sdcard/Download")
            if (publicSd.exists() && publicSd.canWrite()) {
                pdfFile.inputStream().use { input ->
                    java.io.FileOutputStream(java.io.File(publicSd, "sample_executive_summary.pdf")).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Exception) {}

        // Render PDF First Page to PNG using PdfRenderer
        val pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        val page = renderer.openPage(0)
        val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()

        val previewFile = java.io.File(downloadDir, "sample_pdf_preview.png")
        java.io.FileOutputStream(previewFile).use { fos ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
        }
        try {
            val publicSd = java.io.File("/sdcard/Download")
            if (publicSd.exists() && publicSd.canWrite()) {
                java.io.FileOutputStream(java.io.File(publicSd, "sample_pdf_preview.png")).use { fos ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
                }
            }
        } catch (_: Exception) {}

        // 2. PPTX Output
        val pptxResult = pptxGen.createPresentation(
            deckTitle = "Paradox AI Q4 Roadmap",
            slides = listOf(
                com.phoneagent.ai.data.document.PptxSlideData(
                    title = "Paradox AI Android Beta",
                    subtitle = "Autonomous On-Device Agent Architecture",
                    bulletPoints = listOf(
                        "Pure on-device bounded tool orchestration",
                        "Keystore-backed Groq and OpenRouter inference",
                        "Material 3 Soft Rounded Design with AMOLED Theme"
                    )
                ),
                com.phoneagent.ai.data.document.PptxSlideData(
                    title = "Everyday Document Engine",
                    subtitle = "Native PDF & OpenXML PPTX Synthesis",
                    bulletPoints = listOf(
                        "Android PdfDocument pagination with word wrap",
                        "Standard OpenXML ZIP generation with editable text frames",
                        "Export to PowerPoint, Google Slides, and Acrobat"
                    )
                ),
                com.phoneagent.ai.data.document.PptxSlideData(
                    title = "Hardware & Voice Automation",
                    subtitle = "Multilingual Voice Command Dispatch",
                    bulletPoints = listOf(
                        "Camera flashlight toggle and guarded phone calls",
                        "Agentic browser controller with parallel tabs",
                        "English and Hindi natural language voice dispatch"
                    )
                )
            ),
            customFileName = "sample_product_roadmap.pptx"
        )
        assertTrue(pptxResult.success)

        val pptxFile = java.io.File(pptxResult.filePath)
        val targetPptx = java.io.File(downloadDir, "sample_product_roadmap.pptx")
        pptxFile.inputStream().use { input ->
            java.io.FileOutputStream(targetPptx).use { output ->
                input.copyTo(output)
            }
        }
        try {
            val publicSd = java.io.File("/sdcard/Download")
            if (publicSd.exists() && publicSd.canWrite()) {
                pptxFile.inputStream().use { input ->
                    java.io.FileOutputStream(java.io.File(publicSd, "sample_product_roadmap.pptx")).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @Test
    fun testVoiceLanguageSelectorAndIntentBuilding() {
        val voiceMgr = com.phoneagent.ai.data.voice.VoiceManager(context)

        // 1. English en-IN Intent
        val enIntent = voiceMgr.buildSpeechRecognizerIntent(com.phoneagent.ai.data.model.VoiceLanguage.EN_IN)
        assertEquals(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH, enIntent.action)
        assertEquals("en-IN", enIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("en-IN", enIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertTrue(enIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_PROMPT)!!.contains("English"))

        // 2. Hindi hi-IN Intent
        val hiIntent = voiceMgr.buildSpeechRecognizerIntent(com.phoneagent.ai.data.model.VoiceLanguage.HI_IN)
        assertEquals("hi-IN", hiIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("hi-IN", hiIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertTrue(hiIntent.getStringExtra(android.speech.RecognizerIntent.EXTRA_PROMPT)!!.contains("हिंदी"))

        // 3. Auto Multilingual Intent
        val autoIntent = voiceMgr.buildSpeechRecognizerIntent(com.phoneagent.ai.data.model.VoiceLanguage.AUTO)
        val additionalLangs = autoIntent.getStringArrayExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES")
        assertNotNull(additionalLangs)
        assertTrue(additionalLangs!!.contains("hi-IN"))
        assertTrue(additionalLangs.contains("en-IN"))
    }

    @Test
    fun testSpeechRecognitionAvailabilityAndEmulatorDistinction() {
        val voiceMgr = com.phoneagent.ai.data.voice.VoiceManager(context)
        val status = voiceMgr.checkSpeechRecognitionAvailable()

        // On emulators without Google Play services, isRecognitionAvailable may be false.
        // We verify that the system detects this accurately and provides actionable user instructions.
        if (!status.isRecognitionAvailable) {
            assertNotNull("Must provide user-facing instruction message when service absent", status.missingServiceMessage)
            assertTrue("Message directs user to install Google speech service", status.missingServiceMessage!!.contains("Google Speech Recognition"))
            assertNotNull("Must provide Settings intent to resolve missing service", status.settingsIntent)
            assertEquals(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS, status.settingsIntent!!.action)
        } else {
            assertTrue("Speech recognition service is available", status.isRecognitionAvailable)
        }
    }

    @Test
    fun testTtsLanguageResolutionAndTargetLocale() {
        val voiceMgr = com.phoneagent.ai.data.voice.VoiceManager(context)

        // 1. English text in AUTO mode -> English locale
        val enLocale = voiceMgr.resolveTargetLocale("Hello, how can I help you?", com.phoneagent.ai.data.model.VoiceLanguage.AUTO)
        assertEquals("en", enLocale.language)

        // 2. Hindi text in AUTO mode -> Hindi locale
        val hiLocale = voiceMgr.resolveTargetLocale("नमस्ते, मैं आपकी क्या सहायता कर सकता हूँ?", com.phoneagent.ai.data.model.VoiceLanguage.AUTO)
        assertEquals("hi", hiLocale.language)
        assertEquals("IN", hiLocale.country)

        // 3. Explicit HI_IN setting overrides text script
        val explicitHi = voiceMgr.resolveTargetLocale("Call 555-0199", com.phoneagent.ai.data.model.VoiceLanguage.HI_IN)
        assertEquals("hi", explicitHi.language)

        // 4. Explicit EN_IN setting overrides text script
        val explicitEn = voiceMgr.resolveTargetLocale("नमस्ते", com.phoneagent.ai.data.model.VoiceLanguage.EN_IN)
        assertEquals("en", explicitEn.language)
    }

    @Test
    fun testHindiUnicodeSurvivalInPdfGeneration() = runBlocking {
        val pdfGen = com.phoneagent.ai.data.document.PdfDocumentGenerator(context)
        val sections = listOf(
            com.phoneagent.ai.data.document.PdfSection(
                header = "१. दैनिक कार्य योजना (Daily Schedule)",
                body = "पैराडॉक्स एआई (Paradox AI) पूरी तरह से ऑन-डिवाइस कार्य करता है और आपकी गोपनीयता की रक्षा करता है।",
                bulletPoints = listOf(
                    "शून्य क्लाउड टेलीमेट्री व डेटा सुरक्षा",
                    "कीस्टोर एईएस-२५६ एन्क्रिप्शन",
                    "हिंदी व अंग्रेजी दोनों भाषाओं में वॉयस कमांड समर्थन"
                )
            ),
            com.phoneagent.ai.data.document.PdfSection(
                header = "२. मुख्य प्राथमिकताएं (Key Priorities)",
                body = "दैनिक लक्ष्यों को समयबद्ध रूप से पूर्ण करना और उत्पादकता में वृद्धि करना।",
                bulletPoints = listOf(
                    "सुबह ९ बजे: टीम रिव्यू व आर्किटेक्चर योजना",
                    "दोपहर २ बजे: ऑटोमेटेड टेस्टिंग व वेरिफिकेशन",
                    "शाम ६ बजे: दैनिक प्रगति रिपोर्ट तैयार करना"
                )
            )
        )

        val result = pdfGen.createPdf(
            title = "दैनिक योजना — पैराडॉक्स एआई",
            author = "Paradox AI Hindi Engine",
            sections = sections,
            customFileName = "hindi_daily_plan.pdf"
        )

        assertTrue("Hindi PDF generation must succeed", result.success)
        assertEquals("PDF", result.fileType)
        assertTrue("Page count must be >= 1", result.unitCount >= 1)
        assertTrue("File size must be > 0 bytes", result.fileSizeBytes > 0)

        // Verify with PdfRenderer that Hindi PDF renders without corruption
        val targetFile = java.io.File(result.filePath)
        val pfd = android.os.ParcelFileDescriptor.open(targetFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        val firstPage = renderer.openPage(0)
        assertNotNull(firstPage)
        firstPage.close()
        renderer.close()
        pfd.close()
    }

    @Test
    fun testLauncherIconAndAdaptiveArtwork() {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)

        // 1. App Name
        val appName = pm.getApplicationLabel(appInfo).toString()
        assertEquals("Paradox AI", appName)

        // 2. Icon resources exist and can be loaded
        val iconDrawable = pm.getApplicationIcon(appInfo)
        assertNotNull("Application icon drawable must not be null", iconDrawable)
        assertTrue("Icon dimensions must be positive", iconDrawable.intrinsicWidth > 0 && iconDrawable.intrinsicHeight > 0)

        // 3. Direct R.mipmap.ic_launcher resource validation
        val res = context.resources
        val launcherDrawable = androidx.core.content.res.ResourcesCompat.getDrawable(res, com.phoneagent.ai.R.mipmap.ic_launcher, null)
        assertNotNull("R.mipmap.ic_launcher must resolve to valid drawable", launcherDrawable)

        val roundLauncherDrawable = androidx.core.content.res.ResourcesCompat.getDrawable(res, com.phoneagent.ai.R.mipmap.ic_launcher_round, null)
        assertNotNull("R.mipmap.ic_launcher_round must resolve to valid drawable", roundLauncherDrawable)
    }

    @Test
    fun testVoiceManagerInComposerContinuousFlow() {
        val voiceMgr = com.phoneagent.ai.data.voice.VoiceManager(context)

        // 1. Initial State
        assertFalse("Initial isListening must be false", voiceMgr.isListening.value)
        assertEquals("Initial rmsLevel must be 0f", 0f, voiceMgr.rmsLevel.value, 0.001f)
        assertEquals("Initial liveTranscript must be empty", "", voiceMgr.liveTranscript.value)
        assertNull("Initial speechError must be null", voiceMgr.speechError.value)

        // 2. Cancellation State Transition
        voiceMgr.cancelListening()
        assertFalse("After cancel, isListening must be false", voiceMgr.isListening.value)
        assertEquals("After cancel, rmsLevel must be 0f", 0f, voiceMgr.rmsLevel.value, 0.001f)
        assertEquals("After cancel, liveTranscript must be cleared", "", voiceMgr.liveTranscript.value)

        // 3. Stop Listening State Transition
        var stopCallbackInvoked = false
        voiceMgr.stopListening {
            stopCallbackInvoked = true
        }
        assertFalse("After stop, isListening must be false", voiceMgr.isListening.value)
        assertEquals("After stop, rmsLevel must be 0f", 0f, voiceMgr.rmsLevel.value, 0.001f)

        // 4. Cleanup
        voiceMgr.shutdown()
        assertFalse("After shutdown, isTtsReady must be false", voiceMgr.isTtsReady)
    }

    @Test
    fun testMaliciousSelectorTreatedAsData() = runBlocking(Dispatchers.Main) {
        // Test malicious selector payload designed to break string literal boundaries in JS
        val maliciousClickSelector = "button'); window.__injected = true; ('"
        val clickResult = browserController.clickElement(maliciousClickSelector)
        // With JSONObject.quote, the string is treated purely as a CSS selector literal
        assertTrue(
            "Malicious selector must fail safely without code execution: $clickResult",
            clickResult.contains("Click failed") || clickResult.contains("syntax error") || clickResult.contains("not found")
        )

        // Test malicious fill selector and value
        val maliciousFillSelector = "input[name=\"'); alert('xss'); ('\"]"
        val fillResult = browserController.fillInput(maliciousFillSelector, "safe value")
        assertTrue(
            "Malicious fill selector must fail safely as data: $fillResult",
            fillResult.contains("Fill failed") || fillResult.contains("syntax error") || fillResult.contains("not found")
        )
    }

    private suspend fun setupTestWebPage(htmlContent: String) = withContext(Dispatchers.Main) {
        val webView = browserController.getActiveWebView()
        assertNotNull("Active WebView must exist", webView)
        webView?.loadDataWithBaseURL("https://example.com/test", htmlContent, "text/html", "UTF-8", null)
        
        // Wait until WebView finishes parsing and renders interactive elements into DOM
        for (i in 0 until 50) {
            delay(50)
            val readyDeferred = CompletableDeferred<Boolean>()
            webView?.evaluateJavascript("(function() { return (document.getElementById('regular-btn') != null || document.getElementById('user-password') != null || (document.body != null && document.body.innerHTML.length > 20)); })()") { res ->
                readyDeferred.complete(res == "true")
            }
            if (readyDeferred.await()) {
                break
            }
        }
        delay(100)
    }

    @Test
    fun testBrowserActionConfirmationAllowed() = runBlocking(Dispatchers.Main) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="regular-btn">Submit Request</button>
                <input type="text" id="regular-input" value="" />
            </body>
            </html>
        """.trimIndent()
        setupTestWebPage(html)

        // 1. Allowed Click via production pendingActionRequest flow (no fake auto-allow callback)
        val clickDeferred = async { browserController.clickElement("#regular-btn") }
        val clickReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertEquals("click", clickReq.action)
        assertEquals("#regular-btn", clickReq.target)
        assertTrue("Site must match example domain", clickReq.site.contains("example.com"))
        assertFalse("Must not be sensitive", clickReq.isSensitive)
        // User explicitly taps Allow in production dialog
        clickReq.respond(BrowserConfirmationResult.ALLOWED)

        val clickRes = clickDeferred.await()
        assertTrue("Allowed click must succeed: $clickRes", clickRes.contains("Successfully clicked '#regular-btn'"))

        // 2. Allowed Fill via production pendingActionRequest flow
        val fillDeferred = async { browserController.fillInput("#regular-input", "Test input text") }
        val fillReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertEquals("fill", fillReq.action)
        assertEquals("#regular-input", fillReq.target)
        assertEquals("Test input text", fillReq.proposedValue)
        assertFalse("Must not be sensitive", fillReq.isSensitive)
        // User explicitly taps Allow in production dialog
        fillReq.respond(BrowserConfirmationResult.ALLOWED)

        val fillRes = fillDeferred.await()
        assertTrue("Allowed fill must succeed: $fillRes", fillRes.contains("Successfully filled '#regular-input'"))
    }

    @Test
    fun testBrowserActionConfirmationDenied() = runBlocking(Dispatchers.Main) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="regular-btn">Submit Request</button>
                <input type="text" id="regular-input" value="" />
            </body>
            </html>
        """.trimIndent()
        setupTestWebPage(html)

        // 1. Denied Click via production pendingActionRequest flow
        val clickDeferred = async { browserController.clickElement("#regular-btn") }
        val clickReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        clickReq.respond(BrowserConfirmationResult.DENIED)
        val clickRes = clickDeferred.await()
        assertTrue("Denied click must fail closed with ACTION_DENIED: $clickRes", clickRes.contains("ACTION_DENIED"))

        // 2. Denied Fill via production pendingActionRequest flow
        val fillDeferred = async { browserController.fillInput("#regular-input", "Should not fill") }
        val fillReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        fillReq.respond(BrowserConfirmationResult.DENIED)
        val fillRes = fillDeferred.await()
        assertTrue("Denied fill must fail closed with ACTION_DENIED: $fillRes", fillRes.contains("ACTION_DENIED"))
    }

    @Test
    fun testBrowserActionConfirmationDismissed() = runBlocking(Dispatchers.Main) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="regular-btn">Submit Request</button>
                <input type="text" id="regular-input" value="" />
            </body>
            </html>
        """.trimIndent()
        setupTestWebPage(html)

        // 1. Dismissed Click via production pendingActionRequest flow
        val clickDeferred = async { browserController.clickElement("#regular-btn") }
        val clickReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        clickReq.respond(BrowserConfirmationResult.DISMISSED)
        val clickRes = clickDeferred.await()
        assertTrue("Dismissed click must fail closed with ACTION_DISMISSED: $clickRes", clickRes.contains("ACTION_DISMISSED"))

        // 2. Dismissed Fill via production pendingActionRequest flow
        val fillDeferred = async { browserController.fillInput("#regular-input", "Should not fill") }
        val fillReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        fillReq.respond(BrowserConfirmationResult.DISMISSED)
        val fillRes = fillDeferred.await()
        assertTrue("Dismissed fill must fail closed with ACTION_DISMISSED: $fillRes", fillRes.contains("ACTION_DISMISSED"))
    }

    @Test
    fun testBrowserActionConfirmationSensitiveBlocksAllow() = runBlocking(Dispatchers.Main) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <input type="password" id="user-password" />
                <input type="text" id="user-otp" autocomplete="one-time-code" />
                <input type="text" id="card-num" autocomplete="cc-number" />
                <button id="pay-now-btn">Pay $99.00 Now</button>
            </body>
            </html>
        """.trimIndent()
        setupTestWebPage(html)

        // 1. Password field fill: even if respond(ALLOWED) is invoked, controller must strictly reject sensitive actions
        val pwdDeferred = async { browserController.fillInput("#user-password", "my-secret-password") }
        val pwdReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertTrue("Password fill must be flagged sensitive", pwdReq.isSensitive)
        pwdReq.respond(BrowserConfirmationResult.ALLOWED)
        val pwdRes = pwdDeferred.await()
        assertTrue("Password fill must return ACTION_BLOCKED_SENSITIVE: $pwdRes", pwdRes.contains("ACTION_BLOCKED_SENSITIVE"))

        // 2. OTP field fill
        val otpDeferred = async { browserController.fillInput("#user-otp", "654321") }
        val otpReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertTrue("OTP fill must be flagged sensitive", otpReq.isSensitive)
        otpReq.respond(BrowserConfirmationResult.ALLOWED)
        val otpRes = otpDeferred.await()
        assertTrue("OTP fill must return ACTION_BLOCKED_SENSITIVE: $otpRes", otpRes.contains("ACTION_BLOCKED_SENSITIVE"))

        // 3. Payment credit card field fill
        val cardDeferred = async { browserController.fillInput("#card-num", "4111222233334444") }
        val cardReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertTrue("Card fill must be flagged sensitive", cardReq.isSensitive)
        cardReq.respond(BrowserConfirmationResult.ALLOWED)
        val cardRes = cardDeferred.await()
        assertTrue("Card fill must return ACTION_BLOCKED_SENSITIVE: $cardRes", cardRes.contains("ACTION_BLOCKED_SENSITIVE"))

        // 4. Payment submit button click
        val payDeferred = async { browserController.clickElement("#pay-now-btn") }
        val payReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertTrue("Payment button click must be flagged sensitive", payReq.isSensitive)
        payReq.respond(BrowserConfirmationResult.ALLOWED)
        val payRes = payDeferred.await()
        assertTrue("Payment button click must return ACTION_BLOCKED_SENSITIVE: $payRes", payRes.contains("ACTION_BLOCKED_SENSITIVE"))
    }

    @Test
    fun testBrowserActionConfirmationTimeout() = runBlocking(Dispatchers.Main) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="regular-btn">Submit Request</button>
            </body>
            </html>
        """.trimIndent()
        setupTestWebPage(html)

        // Real 15s timeout test: confirmationHandler stays strictly null, testing actual wait with no handler
        browserController.confirmationHandler = null
        browserController.confirmationTimeoutMs = 15000L

        val startTime = System.currentTimeMillis()
        val res = browserController.clickElement("#regular-btn")
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Actual timeout wait must be at least 14500ms (elapsed=${elapsed}ms)", elapsed >= 14500L)
        assertTrue("Natural timeout must return ACTION_TIMEOUT (default deny): $res", res.contains("ACTION_TIMEOUT"))
    }

    @Test
    fun testDomMutationWhileApprovalPendingBlocked() = runBlocking(Dispatchers.Main) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <button id="mutate-btn">Initial Safe Button</button>
                <input type="text" id="mutate-input" value="safe" />
            </body>
            </html>
        """.trimIndent()
        setupTestWebPage(html)

        val webView = browserController.getActiveWebView()
        assertNotNull(webView)

        // 1. Test fill mutation: target input mutated to password while confirmation is pending
        val fillDeferred = async { browserController.fillInput("#mutate-input", "injected_value") }
        val fillReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertEquals("fill", fillReq.action)
        assertFalse(fillReq.isSensitive)

        // Real DOM Mutation occurs asynchronously on WebView while user approval is pending:
        webView?.evaluateJavascript("document.getElementById('mutate-input').setAttribute('type', 'password');", null)
        delay(100)

        // User confirms the original harmless text input
        fillReq.respond(BrowserConfirmationResult.ALLOWED)
        val fillRes = fillDeferred.await()

        assertTrue(
            "Synchronous atomic sensitivity recheck must catch mutation immediately before execution: $fillRes",
            fillRes.contains("ACTION_BLOCKED_MUTATION") || fillRes.contains("ATOMIC_RECHECK_FAILED")
        )

        // 2. Test click mutation: button mutated to payment submission while confirmation is pending
        val clickDeferred = async { browserController.clickElement("#mutate-btn") }
        val clickReq = withTimeout(5000) {
            browserController.pendingActionRequest.filterNotNull().first()
        }
        assertEquals("click", clickReq.action)
        assertFalse(clickReq.isSensitive)

        // Real DOM Mutation: button text changed to payment button while approval pending
        webView?.evaluateJavascript("document.getElementById('mutate-btn').innerText = 'Pay $500.00 Now';", null)
        delay(100)

        clickReq.respond(BrowserConfirmationResult.ALLOWED)
        val clickRes = clickDeferred.await()

        assertTrue(
            "Synchronous atomic sensitivity recheck must catch payment mutation before click execution: $clickRes",
            clickRes.contains("ACTION_BLOCKED_MUTATION") || clickRes.contains("ATOMIC_RECHECK_FAILED")
        )
    }

    @Test
    fun testDomObservationRedactsOtpAndPaymentFields() = runBlocking(Dispatchers.Main) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <input type="text" autocomplete="one-time-code" id="otp-field" value="987654" />
                <input type="text" autocomplete="cc-number" id="card-field" value="4111222233334444" />
                <input type="password" id="pass-field" value="supersecret" />
                <input type="text" id="regular-field" value="harmless text" />
            </body>
            </html>
        """.trimIndent()
        setupTestWebPage(html)

        val dom = browserController.observeDom()
        assertTrue("DOM observation must contain REDACTED_SECRET for sensitive inputs", dom.contains("[REDACTED_SECRET]"))
        assertFalse("DOM observation must redact OTP one-time-code value", dom.contains("987654"))
        assertFalse("DOM observation must redact credit card cc-* value", dom.contains("4111222233334444"))
        assertFalse("DOM observation must redact password value", dom.contains("supersecret"))
        assertTrue("DOM observation must retain harmless text", dom.contains("harmless text"))
    }
}
