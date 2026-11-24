package com.phoneagent.ai.data.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import com.phoneagent.ai.data.browser.AgenticBrowserController
import com.phoneagent.ai.data.db.ChatStorage
import com.phoneagent.ai.data.document.PdfDocumentGenerator
import com.phoneagent.ai.data.document.PdfSection
import com.phoneagent.ai.data.document.PptxPresentationGenerator
import com.phoneagent.ai.data.document.PptxSlideData
import com.phoneagent.ai.data.hardware.PhoneHardwareController
import com.phoneagent.ai.data.model.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class NativeToolExecutor(
    private val context: Context,
    private val storage: ChatStorage,
    val browserController: AgenticBrowserController = AgenticBrowserController(context),
    val hardwareController: PhoneHardwareController = PhoneHardwareController(context),
    val pdfGenerator: PdfDocumentGenerator = PdfDocumentGenerator(context),
    val pptxGenerator: PptxPresentationGenerator = PptxPresentationGenerator(context)
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getToolDefinitionsJson(): JSONArray {
        val tools = JSONArray()

        // 1. Calculator
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "calculator")
                put("description", "Perform mathematical calculations and evaluations (e.g., '128 * 4.5', '(450 + 20) / 3')")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("expression", JSONObject().apply {
                            put("type", "string")
                            put("description", "Mathematical expression to evaluate")
                        })
                    })
                    put("required", JSONArray().put("expression"))
                })
            })
        })

        // 2. Date and Time
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "datetime")
                put("description", "Get the current system date, time, day of the week, and timezone on this phone")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // 3. Device Info
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "device_info")
                put("description", "Query phone device status: model, Android version, battery level, charging status, and available storage")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // 4. Web Fetch
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "web_fetch")
                put("description", "Safely fetch text content from a web URL or API endpoint")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "string")
                            put("description", "The HTTP or HTTPS URL to fetch")
                        })
                    })
                    put("required", JSONArray().put("url"))
                })
            })
        })

        // 5. Local File Read
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "local_file_read")
                put("description", "Read content of a local file in the app documents or attachments directory (bounded to 8000 characters)")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("file_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "File name or relative path to read")
                        })
                    })
                    put("required", JSONArray().put("file_name"))
                })
            })
        })

        // 6. Local File Search
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "local_file_search")
                put("description", "Search for local files or search text within local files in app storage")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "Text or pattern to search for across file names and contents")
                        })
                    })
                    put("required", JSONArray().put("query"))
                })
            })
        })

        // 7. Memory Save
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "memory_save")
                put("description", "Persist a fact, preference, or detail into the agent's long-term memory across sessions")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("key", JSONObject().apply {
                            put("type", "string")
                            put("description", "A concise identifier or tag for this memory (e.g. 'user_name', 'preferred_language', 'project_name')")
                        })
                        put("content", JSONObject().apply {
                            put("type", "string")
                            put("description", "The memory content or fact to remember")
                        })
                        put("category", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional category (e.g. 'preference', 'project', 'contact', 'note')")
                        })
                    })
                    put("required", JSONArray().put("key").put("content"))
                })
            })
        })

        // 8. Memory Recall
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "memory_recall")
                put("description", "Search long-term memory for saved facts, preferences, or notes")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "Keyword or query to search memories for, or empty string to retrieve all memories")
                        })
                    })
                })
            })
        })

        // 9. Browser Navigate
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_navigate")
                put("description", "Navigate an in-app agentic browser tab to a web URL (HTTP/HTTPS)")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "string")
                            put("description", "The web URL to load")
                        })
                        put("tab_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional tab ID (defaults to active tab)")
                        })
                    })
                    put("required", JSONArray().put("url"))
                })
            })
        })

        // 10. Browser Observe DOM
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_observe")
                put("description", "Inspect the visible DOM and interactive elements ([#1], [#2] tags) on the current browser tab. Sensitive fields are redacted.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("tab_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional tab ID (defaults to active tab)")
                        })
                    })
                })
            })
        })

        // 11. Browser Click
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_click")
                put("description", "Click an interactive element on the browser tab by its tag index (e.g. '[#1]') or CSS selector. Sensitive actions trigger user confirmation.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("selector_or_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "The element index '[#1]' or CSS selector")
                        })
                        put("tab_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional tab ID (defaults to active tab)")
                        })
                    })
                    put("required", JSONArray().put("selector_or_id"))
                })
            })
        })

        // 12. Browser Fill
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_fill")
                put("description", "Type text into an input field or textarea on the browser tab by its tag index (e.g. '[#2]') or CSS selector.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("selector_or_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "The element index '[#2]' or CSS selector")
                        })
                        put("text", JSONObject().apply {
                            put("type", "string")
                            put("description", "The text to input")
                        })
                        put("tab_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional tab ID (defaults to active tab)")
                        })
                    })
                    put("required", JSONArray().put("selector_or_id").put("text"))
                })
            })
        })

        // 13. Browser Scroll
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_scroll")
                put("description", "Scroll the browser tab window up or down")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("direction", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray().put("up").put("down"))
                            put("description", "Direction to scroll ('up' or 'down')")
                        })
                        put("tab_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional tab ID (defaults to active tab)")
                        })
                    })
                })
            })
        })

        // 14. Browser Tab Management: Create, Switch, Close, List
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_tab_create")
                put("description", "Open a new parallel browser tab with an optional initial URL")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional URL to load in the new tab")
                        })
                    })
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_tab_switch")
                put("description", "Switch active browser view to a specific tab ID")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("tab_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "The tab ID to switch to")
                        })
                    })
                    put("required", JSONArray().put("tab_id"))
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_tab_close")
                put("description", "Close a browser tab by ID")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("tab_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "The tab ID to close")
                        })
                    })
                    put("required", JSONArray().put("tab_id"))
                })
            })
        })

        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "browser_tab_list")
                put("description", "List all currently open parallel browser tabs and their status")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // 15. Phone Flashlight
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "flashlight")
                put("description", "Turn the device camera flashlight / torch ON or OFF")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("enabled", JSONObject().apply {
                            put("type", "boolean")
                            put("description", "True to turn on, False to turn off")
                        })
                    })
                    put("required", JSONArray().put("enabled"))
                })
            })
        })

        // 16. Phone Call (Prepares system dialer with confirmation)
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "call_phone")
                put("description", "Open phone dialer with pre-filled number. Requires explicit user confirmation on dialer to place call.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("phone_number", JSONObject().apply {
                            put("type", "string")
                            put("description", "The phone number or contact digits to dial")
                        })
                    })
                    put("required", JSONArray().put("phone_number"))
                })
            })
        })

        // 17. Create PDF
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_pdf")
                put("description", "Generate a genuine multi-page PDF document on-device with custom sections, headers, and bullet points. Verified with PdfRenderer.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("type", "string")
                            put("description", "Main title of the PDF document")
                        })
                        put("author", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional author or organization name")
                        })
                        put("file_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional custom filename (e.g. 'Project_Summary.pdf')")
                        })
                        put("sections", JSONObject().apply {
                            put("type", "array")
                            put("description", "Array of section objects, each with 'header', 'body', and optional 'bullet_points'")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("header", JSONObject().apply { put("type", "string") })
                                    put("body", JSONObject().apply { put("type", "string") })
                                    put("bullet_points", JSONObject().apply {
                                        put("type", "array")
                                        put("items", JSONObject().apply { put("type", "string") })
                                    })
                                })
                            })
                        })
                    })
                    put("required", JSONArray().put("title").put("sections"))
                })
            })
        })

        // 18. Append PDF
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "append_pdf")
                put("description", "Append a new section with optional bullet points to an existing PDF in documents.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("file_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "The existing PDF filename in app storage")
                        })
                        put("header", JSONObject().apply {
                            put("type", "string")
                            put("description", "New section header")
                        })
                        put("body", JSONObject().apply {
                            put("type", "string")
                            put("description", "New section body text")
                        })
                        put("bullet_points", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                    })
                    put("required", JSONArray().put("file_name"))
                })
            })
        })

        // 19. Create PPTX Presentation
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_pptx")
                put("description", "Generate a valid, genuinely editable OpenXML PowerPoint presentation (.pptx) with formatted slide titles, subtitles, and hierarchical bullet points.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("deck_title", JSONObject().apply {
                            put("type", "string")
                            put("description", "Presentation title / title slide header")
                        })
                        put("file_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Optional custom filename (e.g. 'Quarterly_Review.pptx')")
                        })
                        put("slides", JSONObject().apply {
                            put("type", "array")
                            put("description", "Array of slide objects with 'title', optional 'subtitle', optional 'bullet_points', and optional 'notes'")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("title", JSONObject().apply { put("type", "string") })
                                    put("subtitle", JSONObject().apply { put("type", "string") })
                                    put("bullet_points", JSONObject().apply {
                                        put("type", "array")
                                        put("items", JSONObject().apply { put("type", "string") })
                                    })
                                    put("notes", JSONObject().apply { put("type", "string") })
                                })
                                put("required", JSONArray().put("title"))
                            })
                        })
                    })
                    put("required", JSONArray().put("deck_title").put("slides"))
                })
            })
        })

        // 20. Open Document
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "open_document")
                put("description", "Open a generated or saved document (PDF, PPTX, text) using the Android system viewer or office app via FileProvider.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("file_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "The document filename to view")
                        })
                    })
                    put("required", JSONArray().put("file_name"))
                })
            })
        })

        // 21. Share Document
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "share_document")
                put("description", "Open the Android system share sheet to send a generated document via email, messaging, or cloud drive.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("file_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "The document filename to share")
                        })
                    })
                    put("required", JSONArray().put("file_name"))
                })
            })
        })

        return tools
    }

    suspend fun executeTool(name: String, argumentsJson: String): String = withContext(Dispatchers.IO) {
        try {
            val args = if (argumentsJson.trim().isNotEmpty()) JSONObject(argumentsJson) else JSONObject()
            when (name) {
                "calculator" -> {
                    val expr = args.optString("expression", "")
                    evaluateMath(expr)
                }
                "datetime" -> {
                    val sdf = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
                    sdf.timeZone = TimeZone.getDefault()
                    val now = sdf.format(Date())
                    "Current Device Time: $now (Timezone: ${TimeZone.getDefault().id})"
                }
                "device_info" -> {
                    getDeviceInfo()
                }
                "web_fetch" -> {
                    val url = args.optString("url", "")
                    fetchWeb(url)
                }
                "local_file_read" -> {
                    val fileName = args.optString("file_name", "")
                    readLocalFile(fileName)
                }
                "local_file_search" -> {
                    val query = args.optString("query", "")
                    searchLocalFiles(query)
                }
                "memory_save" -> {
                    val key = args.optString("key", "")
                    val content = args.optString("content", "")
                    val category = args.optString("category", "general")
                    if (key.isBlank() || content.isBlank()) {
                        "Error: key and content must not be blank"
                    } else {
                        storage.saveMemory(MemoryItem(key = key, content = content, category = category))
                        "Successfully saved to memory: [$key] $content"
                    }
                }
                "memory_recall" -> {
                    val query = args.optString("query", "").trim()
                    val memories = storage.getMemories()
                    if (memories.isEmpty()) {
                        "No memories found in storage."
                    } else {
                        val matches = if (query.isEmpty()) {
                            memories
                        } else {
                            memories.filter {
                                it.key.contains(query, ignoreCase = true) ||
                                it.content.contains(query, ignoreCase = true) ||
                                it.category.contains(query, ignoreCase = true)
                            }
                        }
                        if (matches.isEmpty()) {
                            "No memories matching '$query'."
                        } else {
                            matches.joinToString("\n") { "• [${it.key}] (${it.category}): ${it.content}" }
                        }
                    }
                }
                "browser_navigate" -> {
                    val url = args.optString("url", "")
                    val tabId = args.optString("tab_id").takeIf { it.isNotEmpty() }
                    browserController.navigate(url, tabId)
                }
                "browser_observe" -> {
                    val tabId = args.optString("tab_id").takeIf { it.isNotEmpty() }
                    browserController.observeDom(tabId)
                }
                "browser_click" -> {
                    val selectorOrId = args.optString("selector_or_id", "")
                    val tabId = args.optString("tab_id").takeIf { it.isNotEmpty() }
                    browserController.clickElement(selectorOrId, tabId)
                }
                "browser_fill" -> {
                    val selectorOrId = args.optString("selector_or_id", "")
                    val text = args.optString("text", "")
                    val tabId = args.optString("tab_id").takeIf { it.isNotEmpty() }
                    browserController.fillInput(selectorOrId, text, tabId)
                }
                "browser_scroll" -> {
                    val direction = args.optString("direction", "down")
                    val tabId = args.optString("tab_id").takeIf { it.isNotEmpty() }
                    browserController.scroll(direction, tabId)
                }
                "browser_tab_create" -> {
                    val url = args.optString("url", "https://en.m.wikipedia.org")
                    val tab = browserController.createTab(url)
                    "Created new parallel browser tab [${tab.id}] for URL: ${tab.url}"
                }
                "browser_tab_switch" -> {
                    val tabId = args.optString("tab_id", "")
                    if (browserController.switchTab(tabId)) {
                        "Switched active view to tab [$tabId]"
                    } else {
                        "Error: Tab [$tabId] not found"
                    }
                }
                "browser_tab_close" -> {
                    val tabId = args.optString("tab_id", "")
                    if (browserController.closeTab(tabId)) {
                        "Closed tab [$tabId]"
                    } else {
                        "Error: Tab [$tabId] not found"
                    }
                }
                "browser_tab_list" -> {
                    browserController.listTabs()
                }
                "flashlight" -> {
                    val enabled = args.optBoolean("enabled", false)
                    hardwareController.setFlashlight(enabled)
                }
                "call_phone" -> {
                    val phoneNumber = args.optString("phone_number", "")
                    hardwareController.prepareCall(phoneNumber)
                }
                "create_pdf" -> {
                    val title = args.optString("title", "Document")
                    val author = args.optString("author", "Paradox AI")
                    val fileName = args.optString("file_name").takeIf { it.isNotBlank() }
                    val sectionsArray = args.optJSONArray("sections") ?: JSONArray()
                    val sections = mutableListOf<PdfSection>()
                    for (i in 0 until sectionsArray.length()) {
                        val secObj = sectionsArray.optJSONObject(i) ?: continue
                        val header = secObj.optString("header", "")
                        val body = secObj.optString("body", "")
                        val bps = mutableListOf<String>()
                        val bpArray = secObj.optJSONArray("bullet_points")
                        if (bpArray != null) {
                            for (j in 0 until bpArray.length()) {
                                bps.add(bpArray.optString(j))
                            }
                        }
                        sections.add(PdfSection(header = header, body = body, bulletPoints = bps))
                    }
                    val result = pdfGenerator.createPdf(title, author, sections, fileName)
                    "[DOCUMENT_ATTACHMENT_JSON]${result.toJson()}[/DOCUMENT_ATTACHMENT_JSON]\n${result.message}\nPath: ${result.filePath}"
                }
                "append_pdf" -> {
                    val fileName = args.optString("file_name", "")
                    val header = args.optString("header", "")
                    val body = args.optString("body", "")
                    val bps = mutableListOf<String>()
                    val bpArray = args.optJSONArray("bullet_points")
                    if (bpArray != null) {
                        for (j in 0 until bpArray.length()) {
                            bps.add(bpArray.optString(j))
                        }
                    }
                    val sec = PdfSection(header = header, body = body, bulletPoints = bps)
                    val result = pdfGenerator.appendSection(fileName, sec)
                    "[DOCUMENT_ATTACHMENT_JSON]${result.toJson()}[/DOCUMENT_ATTACHMENT_JSON]\n${result.message}\nPath: ${result.filePath}"
                }
                "create_pptx" -> {
                    val deckTitle = args.optString("deck_title", "Presentation")
                    val fileName = args.optString("file_name").takeIf { it.isNotBlank() }
                    val slidesArray = args.optJSONArray("slides") ?: JSONArray()
                    val slides = mutableListOf<PptxSlideData>()
                    for (i in 0 until slidesArray.length()) {
                        val sObj = slidesArray.optJSONObject(i) ?: continue
                        val title = sObj.optString("title", "Slide ${i + 1}")
                        val subtitle = sObj.optString("subtitle").takeIf { it.isNotBlank() }
                        val notes = sObj.optString("notes").takeIf { it.isNotBlank() }
                        val bps = mutableListOf<String>()
                        val bpArray = sObj.optJSONArray("bullet_points")
                        if (bpArray != null) {
                            for (j in 0 until bpArray.length()) {
                                bps.add(bpArray.optString(j))
                            }
                        }
                        slides.add(PptxSlideData(title = title, subtitle = subtitle, bulletPoints = bps, notes = notes))
                    }
                    val result = pptxGenerator.createPresentation(deckTitle, slides, fileName)
                    "[DOCUMENT_ATTACHMENT_JSON]${result.toJson()}[/DOCUMENT_ATTACHMENT_JSON]\n${result.message}\nPath: ${result.filePath}"
                }
                "open_document" -> {
                    val fileName = args.optString("file_name", "")
                    openDocument(fileName)
                }
                "share_document" -> {
                    val fileName = args.optString("file_name", "")
                    shareDocument(fileName)
                }
                else -> "Error: Unknown tool '$name'"
            }
        } catch (e: Exception) {
            "Tool execution failed: ${e.message}"
        }
    }

    private fun findDocumentFile(fileName: String): File? {
        val clean = File(fileName).name
        val candidateDirs = listOf(
            File(context.filesDir, "documents"),
            File(context.filesDir, "attachments"),
            context.filesDir
        )
        for (dir in candidateDirs) {
            val candidate = File(dir, clean)
            if (candidate.exists() && candidate.isFile) return candidate
        }
        return null
    }

    private fun openDocument(fileName: String): String {
        if (fileName.isBlank()) return "Error: file_name cannot be blank"
        val file = findDocumentFile(fileName) ?: return "Error: Document '$fileName' not found in app storage."
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mimeType = when {
                file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                file.name.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                file.name.endsWith(".txt", ignoreCase = true) -> "text/plain"
                file.name.endsWith(".md", ignoreCase = true) -> "text/markdown"
                file.name.endsWith(".json", ignoreCase = true) -> "application/json"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened '${file.name}' with system viewer (URI: $uri)."
        } catch (e: Exception) {
            "Failed to open document: ${e.message}"
        }
    }

    private fun shareDocument(fileName: String): String {
        if (fileName.isBlank()) return "Error: file_name cannot be blank"
        val file = findDocumentFile(fileName) ?: return "Error: Document '$fileName' not found in app storage."
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mimeType = when {
                file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                file.name.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "*/*"
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            "Opened share sheet for '${file.name}'."
        } catch (e: Exception) {
            "Failed to share document: ${e.message}"
        }
    }

    private fun evaluateMath(expression: String): String {
        return try {
            val clean = expression.replace(" ", "")
            val result = SafeMathParser.eval(clean)
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
            "$expression = $formatted"
        } catch (e: Exception) {
            "Calculation error: ${e.message}"
        }
    }

    private fun getDeviceInfo(): String {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        // Battery
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val bStatus = context.registerReceiver(null, ifilter)
        val level = bStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val bPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1
        val status = bStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val availBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availGB = String.format(Locale.US, "%.1f GB", availBytes / (1024.0 * 1024.0 * 1024.0))
        val totalGB = String.format(Locale.US, "%.1f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))

        return "Device: $model\nOS: $androidVer\nBattery: $bPct% (Charging: $isCharging)\nInternal Storage: $availGB free of $totalGB"
    }

    private fun fetchWeb(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "Error: Invalid URL scheme. Must be http or https."
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PhoneAgent-Android/1.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "HTTP Error ${response.code}: ${response.message}"
            val body = response.body?.string() ?: ""
            // Truncate to safe bound of 4000 characters
            val bounded = if (body.length > 4000) body.take(4000) + "\n...[truncated]" else body
            return bounded
        }
    }

    private fun readLocalFile(fileName: String): String {
        if (fileName.isBlank()) return "Error: file_name cannot be blank"
        // Security check: restrict to app directories to prevent path traversal
        val cleanName = File(fileName).name
        val searchDirs = listOf(
            File(context.filesDir, "attachments"),
            context.filesDir,
            context.cacheDir
        )

        for (dir in searchDirs) {
            val target = File(dir, cleanName)
            if (target.exists() && target.isFile) {
                return try {
                    val text = target.readText()
                    val bounded = if (text.length > 8000) text.take(8000) + "\n...[truncated at 8000 characters]" else text
                    "File: ${target.name} (${target.length()} bytes):\n$bounded"
                } catch (e: Exception) {
                    "Error reading file '${target.name}': ${e.message}"
                }
            }
        }
        return "File not found: '$cleanName'. Use local_file_search to list available files."
    }

    private fun searchLocalFiles(query: String): String {
        val searchDirs = listOf(
            File(context.filesDir, "attachments"),
            context.filesDir
        )
        val results = mutableListOf<String>()

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            dir.walkTopDown().maxDepth(2).forEach { file ->
                if (file.isFile) {
                    val nameMatch = query.isBlank() || file.name.contains(query, ignoreCase = true)
                    var contentMatch = false
                    var snippet = ""
                    if (!nameMatch && file.length() < 100000) {
                        try {
                            val text = file.readText()
                            val idx = text.indexOf(query, ignoreCase = true)
                            if (idx >= 0) {
                                contentMatch = true
                                val start = (idx - 30).coerceAtLeast(0)
                                val end = (idx + query.length + 30).coerceAtMost(text.length)
                                snippet = " | ...${text.substring(start, end).replace("\n", " ")}..."
                            }
                        } catch (e: Exception) {
                            // ignore binary or unreadable
                        }
                    }
                    if (nameMatch || contentMatch) {
                        results.add("• ${file.name} (${file.length()} bytes)$snippet")
                    }
                }
            }
        }
        return if (results.isEmpty()) {
            "No files found matching query '$query'."
        } else {
            "Found ${results.size} file(s):\n" + results.joinToString("\n")
        }
    }

    // A tiny, safe recursive-descent math parser without external dependencies
    private object SafeMathParser {
        fun eval(str: String): Double {
            return object : Any() {
                var pos = -1
                var ch = 0

                fun nextChar() {
                    ch = if (++pos < str.length) str[pos].code else -1
                }

                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) {
                        nextChar()
                        return true
                    }
                    return false
                }

                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < str.length) throw RuntimeException("Unexpected character: " + ch.toChar())
                    return x
                }

                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+'.code) -> x += parseTerm()
                            eat('-'.code) -> x -= parseTerm()
                            else -> return x
                        }
                    }
                }

                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*'.code) -> x *= parseFactor()
                            eat('/'.code) -> {
                                val d = parseFactor()
                                if (d == 0.0) throw ArithmeticException("Division by zero")
                                x /= d
                            }
                            eat('%'.code) -> x %= parseFactor()
                            else -> return x
                        }
                    }
                }

                fun parseFactor(): Double {
                    if (eat('+'.code)) return +parseFactor()
                    if (eat('-'.code)) return -parseFactor()

                    var x: Double
                    val startPos = pos
                    if (eat('('.code)) {
                        x = parseExpression()
                        eat(')'.code)
                    } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                        while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                        x = str.substring(startPos, pos).toDouble()
                    } else {
                        throw RuntimeException("Unexpected expression syntax")
                    }

                    if (eat('^'.code)) x = Math.pow(x, parseFactor())
                    return x
                }
            }.parse()
        }
    }
}
