package com.phoneagent.ai.data.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.UUID

enum class BrowserConfirmationResult {
    ALLOWED,
    DENIED,
    DISMISSED,
    NAVIGATED,
    TIMEOUT,
    BLOCKED_SENSITIVE
}

data class BrowserActionRequest(
    val id: String = UUID.randomUUID().toString(),
    val tabId: String,
    val site: String,
    val action: String, // "click" or "fill"
    val target: String,
    val proposedValue: String = "",
    val isSensitive: Boolean = false,
    val sensitiveReason: String? = null,
    val deferred: CompletableDeferred<BrowserConfirmationResult> = CompletableDeferred()
) {
    fun respond(result: BrowserConfirmationResult) {
        if (!deferred.isCompleted) {
            deferred.complete(result)
        }
    }
}

class AgenticBrowserController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _tabs = Collections.synchronizedList(mutableListOf<BrowserTab>())
    private val webViews = Collections.synchronizedMap(mutableMapOf<String, WebView>())
    
    @Volatile
    private var _activeTabId: String? = null

    val tabs: List<BrowserTab>
        get() = synchronized(_tabs) { _tabs.toList() }

    val activeTabId: String?
        get() = _activeTabId

    var onTabChangedListener: (() -> Unit)? = null

    // Confirmation request flow for in-app Allow/Deny dialog
    private val _pendingActionRequest = MutableStateFlow<BrowserActionRequest?>(null)
    val pendingActionRequest: StateFlow<BrowserActionRequest?> = _pendingActionRequest.asStateFlow()

    // Confirmation handler delegate: strictly null in production (for test injection only)
    var confirmationHandler: (suspend (BrowserActionRequest) -> BrowserConfirmationResult)? = null

    // Default confirmation timeout (15s default deny)
    var confirmationTimeoutMs: Long = 15000L

    init {
        // Create initial default blank tab on main thread
        mainHandler.post {
            createTab("https://en.m.wikipedia.org")
        }
    }

    fun getActiveTab(): BrowserTab? {
        val currentId = _activeTabId ?: return null
        return synchronized(_tabs) { _tabs.firstOrNull { it.id == currentId } }
    }

    fun getActiveWebView(): WebView? {
        val currentId = _activeTabId ?: return null
        return webViews[currentId]
    }

    fun getWebView(tabId: String): WebView? {
        return webViews[tabId]
    }

    fun cancelPendingConfirmationForTab(tabId: String, reason: BrowserConfirmationResult = BrowserConfirmationResult.NAVIGATED) {
        val req = _pendingActionRequest.value
        if (req != null && req.tabId == tabId) {
            req.respond(reason)
            _pendingActionRequest.value = null
        }
    }

    suspend fun requestActionConfirmation(
        tabId: String,
        site: String,
        action: String,
        target: String,
        proposedValue: String,
        isSensitive: Boolean,
        sensitiveReason: String?
    ): BrowserConfirmationResult {
        val req = BrowserActionRequest(
            tabId = tabId,
            site = site,
            action = action,
            target = target,
            proposedValue = proposedValue,
            isSensitive = isSensitive,
            sensitiveReason = sensitiveReason
        )

        val handler = confirmationHandler
        if (handler != null) {
            val result = handler.invoke(req)
            if (isSensitive && result == BrowserConfirmationResult.ALLOWED) {
                return BrowserConfirmationResult.BLOCKED_SENSITIVE
            }
            return result
        }

        _pendingActionRequest.value = req
        return try {
            // Default deny timeout (default 15 seconds)
            val result = withTimeoutOrNull(confirmationTimeoutMs) {
                req.deferred.await()
            } ?: BrowserConfirmationResult.TIMEOUT

            if (isSensitive && result == BrowserConfirmationResult.ALLOWED) {
                BrowserConfirmationResult.BLOCKED_SENSITIVE
            } else {
                result
            }
        } finally {
            if (_pendingActionRequest.value?.id == req.id) {
                _pendingActionRequest.value = null
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createTab(initialUrl: String = "about:blank"): BrowserTab {
        val tabId = UUID.randomUUID().toString()
        val formattedUrl = normalizeUrl(initialUrl)
        val tab = BrowserTab(
            id = tabId,
            title = "Loading...",
            url = formattedUrl,
            isLoading = true,
            isHttps = formattedUrl.startsWith("https://")
        )

        val webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Security: Prevent file access from web context
                allowFileAccess = false
                allowContentAccess = false
                // Privacy: Disable geolocation & database APIs
                setGeolocationEnabled(false)
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 PhoneAgent/1.0"
            }

            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    // CRITICAL SECURITY: Never bypass SSL errors under any circumstances
                    handler?.cancel()
                    tab.isLoading = false
                    tab.title = "SSL Security Error (Blocked)"
                    onTabChangedListener?.invoke()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: ""
                    // Only allow standard web protocols inside the agentic webview
                    if (scheme != "http" && scheme != "https" && scheme != "about") {
                        // Reject tel:, sms:, mailto:, intent:, etc. from silent execution
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    // Invalidate and cancel any pending action confirmations on navigation
                    cancelPendingConfirmationForTab(tab.id, BrowserConfirmationResult.NAVIGATED)
                    tab.isLoading = true
                    tab.url = url ?: ""
                    tab.isHttps = url?.startsWith("https://") == true
                    onTabChangedListener?.invoke()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    tab.isLoading = false
                    tab.url = url ?: ""
                    tab.title = view?.title ?: (url ?: "Web Page")
                    tab.isHttps = url?.startsWith("https://") == true
                    onTabChangedListener?.invoke()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    tab.progress = newProgress
                    if (newProgress == 100) {
                        tab.isLoading = false
                    }
                    onTabChangedListener?.invoke()
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        tab.title = title
                        onTabChangedListener?.invoke()
                    }
                }
            }

            if (formattedUrl.isNotBlank() && formattedUrl != "about:blank") {
                loadUrl(formattedUrl)
            }
        }

        webViews[tabId] = webView
        synchronized(_tabs) { _tabs.add(tab) }
        _activeTabId = tabId
        onTabChangedListener?.invoke()
        return tab
    }

    fun switchTab(tabId: String): Boolean {
        val exists = synchronized(_tabs) { _tabs.any { it.id == tabId } }
        if (exists) {
            _activeTabId = tabId
            onTabChangedListener?.invoke()
            return true
        }
        return false
    }

    fun closeTab(tabId: String): Boolean {
        val tab = synchronized(_tabs) { _tabs.firstOrNull { it.id == tabId } } ?: return false
        val webView = webViews.remove(tabId)
        
        cancelPendingConfirmationForTab(tabId, BrowserConfirmationResult.NAVIGATED)

        mainHandler.post {
            webView?.stopLoading()
            webView?.destroy()
        }

        synchronized(_tabs) { _tabs.remove(tab) }

        if (_activeTabId == tabId) {
            _activeTabId = synchronized(_tabs) { _tabs.lastOrNull()?.id }
            if (_activeTabId == null) {
                mainHandler.post {
                    createTab("about:blank")
                }
            }
        }

        onTabChangedListener?.invoke()
        return true
    }

    suspend fun navigate(url: String, tabId: String? = null): String = withContext(Dispatchers.Main) {
        val targetTabId = tabId ?: _activeTabId ?: createTab().id
        val targetUrl = normalizeUrl(url)
        val webView = webViews[targetTabId] ?: return@withContext "Error: Tab $targetTabId does not exist."
        val tab = synchronized(_tabs) { _tabs.firstOrNull { it.id == targetTabId } }

        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            return@withContext "Error: Invalid URL scheme. Only HTTP and HTTPS are permitted."
        }

        cancelPendingConfirmationForTab(targetTabId, BrowserConfirmationResult.NAVIGATED)
        tab?.isLoading = true
        tab?.url = targetUrl
        webView.loadUrl(targetUrl)
        onTabChangedListener?.invoke()

        "Navigating tab [$targetTabId] to $targetUrl"
    }

    suspend fun observeDom(tabId: String? = null): String = withContext(Dispatchers.Main) {
        val targetTabId = tabId ?: _activeTabId ?: return@withContext "Error: No active tab to observe."
        val webView = webViews[targetTabId] ?: return@withContext "Error: WebView for tab $targetTabId not found."
        val tab = synchronized(_tabs) { _tabs.firstOrNull { it.id == targetTabId } }

        val jsScript = """
            (function() {
                var items = [];
                var index = 1;

                // Helper to check if element is visible
                function isVisible(elem) {
                    if (!elem) return false;
                    var style = window.getComputedStyle(elem);
                    return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0' && elem.offsetWidth > 0 && elem.offsetHeight > 0;
                }

                // Helper to check sensitive attribute (redacts passwords, OTP one-time-code, and payment cc-*)
                function isSensitive(elem) {
                    var type = (elem.getAttribute('type') || '').toLowerCase();
                    var name = (elem.getAttribute('name') || '').toLowerCase();
                    var id = (elem.getAttribute('id') || '').toLowerCase();
                    var autocomplete = (elem.getAttribute('autocomplete') || '').toLowerCase();
                    var placeholder = (elem.getAttribute('placeholder') || '').toLowerCase();
                    var ariaLabel = (elem.getAttribute('aria-label') || '').toLowerCase();

                    var isPassword = type === 'password' || 
                           autocomplete.indexOf('password') !== -1 ||
                           name.indexOf('pass') !== -1 || name.indexOf('secret') !== -1 ||
                           id.indexOf('pass') !== -1 || id.indexOf('secret') !== -1 ||
                           placeholder.indexOf('password') !== -1;

                    var isOtp = autocomplete.indexOf('one-time-code') !== -1 || 
                           name.indexOf('otp') !== -1 || name.indexOf('2fa') !== -1 ||
                           id.indexOf('otp') !== -1 || id.indexOf('2fa') !== -1 ||
                           placeholder.indexOf('otp') !== -1 || placeholder.indexOf('2fa') !== -1 ||
                           ariaLabel.indexOf('otp') !== -1 || ariaLabel.indexOf('2fa') !== -1;

                    var isPayment = autocomplete.indexOf('cc-') !== -1 || 
                           name.indexOf('card') !== -1 || name.indexOf('cvv') !== -1 || name.indexOf('cvc') !== -1 || name.indexOf('payment') !== -1 ||
                           id.indexOf('card') !== -1 || id.indexOf('cvv') !== -1 || id.indexOf('cvc') !== -1 || id.indexOf('payment') !== -1 ||
                           placeholder.indexOf('card') !== -1 || placeholder.indexOf('cvv') !== -1 || placeholder.indexOf('cvc') !== -1;

                    return isPassword || isOtp || isPayment;
                }

                // Scan interactive & structural elements
                var all = document.querySelectorAll('h1, h2, h3, h4, p, a, button, input, select, textarea');
                for (var i = 0; i < all.length && index <= 60; i++) {
                    var el = all[i];
                    if (!isVisible(el)) continue;

                    var tag = el.tagName.toLowerCase();
                    var text = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ');
                    var id = el.id ? '#' + el.id : '';
                    var name = el.getAttribute('name') ? '[name="' + el.getAttribute('name') + '"]' : '';

                    if (tag === 'input' || tag === 'textarea') {
                        var inputType = el.getAttribute('type') || 'text';
                        var placeholder = el.getAttribute('placeholder') || '';
                        var val = el.value || '';
                        if (isSensitive(el)) {
                            val = '[REDACTED_SECRET]';
                        }
                        items.push('[' + index + '] <' + tag + ' type="' + inputType + '"' + id + name + ' placeholder="' + placeholder + '"> value="' + val + '"');
                        el.setAttribute('data-agent-id', '' + index);
                        index++;
                    } else if (tag === 'button' || (tag === 'a' && el.getAttribute('href'))) {
                        if (text.length > 0) {
                            items.push('[' + index + '] <' + tag + id + '> "' + text.substring(0, 80) + '"');
                            el.setAttribute('data-agent-id', '' + index);
                            index++;
                        }
                    } else if (tag.startsWith('h') || tag === 'p') {
                        if (text.length > 10) {
                            items.push('<' + tag + '> ' + text.substring(0, 150));
                        }
                    }
                }

                return JSON.stringify({
                    title: document.title || 'Untitled',
                    url: window.location.href,
                    elements: items
                });
            })();
        """.trimIndent()

        val resultDeferred = CompletableDeferred<String>()
        webView.evaluateJavascript(jsScript) { rawJson ->
            resultDeferred.complete(rawJson ?: "{}")
        }

        val raw = withTimeoutOrNull(3000) { resultDeferred.await() } ?: "{}"
        val parsedJson = try {
            val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                org.json.JSONTokener(raw).nextValue().toString()
            } else raw
            JSONObject(unquoted)
        } catch (e: Exception) {
            JSONObject()
        }

        val title = parsedJson.optString("title", tab?.title ?: "Web Page")
        val pageUrl = parsedJson.optString("url", tab?.url ?: "unknown")
        val elementsArr = parsedJson.optJSONArray("elements") ?: JSONArray()
        
        val domTextBuilder = StringBuilder()
        for (i in 0 until elementsArr.length()) {
            domTextBuilder.appendLine(elementsArr.getString(i))
        }

        val rawDomText = domTextBuilder.toString().ifBlank { "No interactive elements observed on this page." }
        val boundedDom = if (rawDomText.length > 6000) rawDomText.take(6000) + "\n...[DOM snapshot bounded at 6000 chars]" else rawDomText

        tab?.lastObservedDom = boundedDom

        buildString {
            appendLine("<untrusted_web_content url=\"$pageUrl\" title=\"$title\">")
            appendLine("Tab ID: $targetTabId | Security: ${if (pageUrl.startsWith("https://")) "HTTPS Validated" else "HTTP"}")
            appendLine("Observed Interactive Elements:")
            appendLine(boundedDom)
            appendLine("</untrusted_web_content>")
        }
    }

    private data class ElementInspection(
        val exists: Boolean,
        val isSensitive: Boolean,
        val reason: String?,
        val error: String?
    )

    private suspend fun inspectTargetElement(webView: WebView, quotedSelector: String): ElementInspection = withContext(Dispatchers.Main) {
        val jsScript = """
            (function() {
                var selector = $quotedSelector;
                try {
                    var el = document.querySelector(selector);
                    if (!el) {
                        return JSON.stringify({ exists: false, error: 'Element not found for selector: ' + selector });
                    }
                    var tag = (el.tagName || '').toLowerCase();
                    var type = (el.getAttribute('type') || '').toLowerCase();
                    var name = (el.getAttribute('name') || '').toLowerCase();
                    var id = (el.getAttribute('id') || '').toLowerCase();
                    var autocomplete = (el.getAttribute('autocomplete') || '').toLowerCase();
                    var placeholder = (el.getAttribute('placeholder') || '').toLowerCase();
                    var ariaLabel = (el.getAttribute('aria-label') || '').toLowerCase();
                    var text = (el.innerText || el.textContent || el.value || '').toLowerCase().trim();

                    // 1. Password detection by type, autocomplete, name, id, placeholder
                    var isPassword = type === 'password' || 
                                     autocomplete.indexOf('password') !== -1 || 
                                     name.indexOf('password') !== -1 || 
                                     id.indexOf('password') !== -1 ||
                                     placeholder.indexOf('password') !== -1;

                    // 2. OTP / 2FA verification code detection
                    var isOtp = autocomplete.indexOf('one-time-code') !== -1 || 
                                name.indexOf('otp') !== -1 || name.indexOf('2fa') !== -1 ||
                                id.indexOf('otp') !== -1 || id.indexOf('2fa') !== -1 ||
                                placeholder.indexOf('otp') !== -1 || placeholder.indexOf('2fa') !== -1 ||
                                ariaLabel.indexOf('otp') !== -1 || ariaLabel.indexOf('2fa') !== -1;

                    // 3. Payment field detection (credit card number, cvv, exp, etc.)
                    var isPaymentField = autocomplete.indexOf('cc-') !== -1 ||
                                         name.indexOf('card') !== -1 || name.indexOf('cvv') !== -1 || name.indexOf('cvc') !== -1 || name.indexOf('payment') !== -1 ||
                                         id.indexOf('card') !== -1 || id.indexOf('cvv') !== -1 || id.indexOf('cvc') !== -1 || id.indexOf('payment') !== -1 ||
                                         placeholder.indexOf('card') !== -1 || placeholder.indexOf('cvv') !== -1 || placeholder.indexOf('cvc') !== -1;

                    // 4. Payment submission detection
                    var isPaymentSubmit = false;
                    var submitKeywords = ['pay', 'purchase', 'buy', 'checkout', 'confirm payment', 'place order', 'transfer', 'subscribe'];
                    for (var i = 0; i < submitKeywords.length; i++) {
                        var kw = submitKeywords[i];
                        if (text.indexOf(kw) !== -1 || id.indexOf(kw) !== -1 || name.indexOf(kw) !== -1 || ariaLabel.indexOf(kw) !== -1) {
                            isPaymentSubmit = true;
                            break;
                        }
                    }

                    var isSensitive = isPassword || isOtp || isPaymentField || isPaymentSubmit;
                    var reason = '';
                    if (isPassword) reason = 'Password field detected';
                    else if (isOtp) reason = 'OTP / 2FA verification field detected';
                    else if (isPaymentField) reason = 'Credit card / payment details field detected';
                    else if (isPaymentSubmit) reason = 'Payment submission / purchase button detected';

                    return JSON.stringify({
                        exists: true,
                        isSensitive: isSensitive,
                        reason: reason
                    });
                } catch (e) {
                    return JSON.stringify({ exists: false, error: 'Selector syntax error: ' + (e.message || 'invalid selector') });
                }
            })();
        """.trimIndent()

        val resultDeferred = CompletableDeferred<String>()
        webView.evaluateJavascript(jsScript) { res ->
            resultDeferred.complete(res ?: "{}")
        }

        val raw = withTimeoutOrNull(3000) { resultDeferred.await() } ?: "{}"
        try {
            val unquoted = if (raw.startsWith("\"") && raw.endsWith("\"")) {
                org.json.JSONTokener(raw).nextValue().toString()
            } else raw
            val json = JSONObject(unquoted)
            ElementInspection(
                exists = json.optBoolean("exists", false),
                isSensitive = json.optBoolean("isSensitive", false),
                reason = json.optString("reason").ifBlank { null },
                error = json.optString("error").ifBlank { null }
            )
        } catch (e: Exception) {
            ElementInspection(exists = false, isSensitive = false, reason = null, error = e.message)
        }
    }

    suspend fun clickElement(selectorOrId: String, tabId: String? = null): String = withContext(Dispatchers.Main) {
        val targetTabId = tabId ?: _activeTabId ?: return@withContext "Error: No active tab."
        val webView = webViews[targetTabId] ?: return@withContext "Error: WebView not found."
        val tab = synchronized(_tabs) { _tabs.firstOrNull { it.id == targetTabId } }

        val cleanTarget = selectorOrId.trim()
        val indexMatch = Regex("""^\[?#?(\d+)\]?$""").find(cleanTarget)?.groupValues?.get(1)

        val targetCssSelector = if (indexMatch != null) {
            "[data-agent-id=\"$indexMatch\"]"
        } else {
            cleanTarget
        }

        // CRITICAL SECURITY DEFENSE: Use JSONObject.quote to ensure ANY selector
        // (including single/double quotes, newlines, or JS payloads) is strictly treated as data
        val quotedSelector = JSONObject.quote(targetCssSelector)

        // 1. Inspect DOM for existence and sensitivity
        val inspection = inspectTargetElement(webView, quotedSelector)
        if (!inspection.exists) {
            return@withContext "Click failed: ${inspection.error ?: "Element not found for selector: $cleanTarget"}"
        }

        // 2. Request user confirmation (default deny, in-app dialog)
        val siteUrl = tab?.url?.ifBlank { "about:blank" } ?: "about:blank"
        val quotedSiteUrl = JSONObject.quote(siteUrl)
        val confirmation = requestActionConfirmation(
            tabId = targetTabId,
            site = siteUrl,
            action = "click",
            target = cleanTarget,
            proposedValue = "(click element)",
            isSensitive = inspection.isSensitive,
            sensitiveReason = inspection.reason
        )

        // 3. Enforce confirmation policy: denial, dismissal, navigation, timeout, or sensitive block must perform no action
        when (confirmation) {
            BrowserConfirmationResult.BLOCKED_SENSITIVE -> {
                return@withContext "ACTION_BLOCKED_SENSITIVE: Click action on sensitive element ($cleanTarget: ${inspection.reason ?: "Password, OTP, or Payment field"}) is strictly prohibited."
            }
            BrowserConfirmationResult.DENIED -> {
                return@withContext "ACTION_DENIED: Browser click on '$cleanTarget' was denied by user."
            }
            BrowserConfirmationResult.DISMISSED -> {
                return@withContext "ACTION_DISMISSED: Browser click confirmation dialog for '$cleanTarget' was dismissed."
            }
            BrowserConfirmationResult.TIMEOUT -> {
                return@withContext "ACTION_TIMEOUT: Browser click confirmation for '$cleanTarget' timed out after 15s (default deny)."
            }
            BrowserConfirmationResult.NAVIGATED -> {
                return@withContext "ACTION_CANCELLED: Browser navigation occurred while waiting for click confirmation on '$cleanTarget'."
            }
            BrowserConfirmationResult.ALLOWED -> {
                if (inspection.isSensitive) {
                    return@withContext "ACTION_BLOCKED_SENSITIVE: Cannot allow click on sensitive field ($cleanTarget: ${inspection.reason})."
                }
                // Allowed - proceed to execute click on WebView
            }
        }

        // 4. ATOMIC SYNCHRONOUS RECHECK & EXECUTION:
        // Synchronously re-inspect element sensitivity and current URL in the exact same JS execution
        // immediately before el.click() to eliminate DOM mutation race conditions
        val jsScript = """
            (function() {
                var selector = $quotedSelector;
                var expectedUrl = $quotedSiteUrl;
                try {
                    // Atomic URL verification
                    var currentUrl = window.location.href || '';
                    if (expectedUrl && expectedUrl !== 'about:blank' && currentUrl !== expectedUrl) {
                        return JSON.stringify({
                            success: false,
                            atomicRecheckFailed: true,
                            error: 'Page URL changed during approval from ' + expectedUrl + ' to ' + currentUrl
                        });
                    }

                    var el = document.querySelector(selector);
                    if (!el) {
                        return JSON.stringify({
                            success: false,
                            error: 'Element not found for selector: ' + selector
                        });
                    }

                    // Synchronous Atomic Sensitivity Re-check immediately before click
                    var tag = (el.tagName || '').toLowerCase();
                    var type = (el.getAttribute('type') || '').toLowerCase();
                    var name = (el.getAttribute('name') || '').toLowerCase();
                    var id = (el.getAttribute('id') || '').toLowerCase();
                    var autocomplete = (el.getAttribute('autocomplete') || '').toLowerCase();
                    var placeholder = (el.getAttribute('placeholder') || '').toLowerCase();
                    var ariaLabel = (el.getAttribute('aria-label') || '').toLowerCase();
                    var text = (el.innerText || el.textContent || el.value || '').toLowerCase().trim();

                    var isPassword = type === 'password' || 
                                     autocomplete.indexOf('password') !== -1 || 
                                     name.indexOf('password') !== -1 || 
                                     id.indexOf('password') !== -1 ||
                                     placeholder.indexOf('password') !== -1;

                    var isOtp = autocomplete.indexOf('one-time-code') !== -1 || 
                                name.indexOf('otp') !== -1 || name.indexOf('2fa') !== -1 ||
                                id.indexOf('otp') !== -1 || id.indexOf('2fa') !== -1 ||
                                placeholder.indexOf('otp') !== -1 || placeholder.indexOf('2fa') !== -1 ||
                                ariaLabel.indexOf('otp') !== -1 || ariaLabel.indexOf('2fa') !== -1;

                    var isPaymentField = autocomplete.indexOf('cc-') !== -1 ||
                                         name.indexOf('card') !== -1 || name.indexOf('cvv') !== -1 || name.indexOf('cvc') !== -1 || name.indexOf('payment') !== -1 ||
                                         id.indexOf('card') !== -1 || id.indexOf('cvv') !== -1 || id.indexOf('cvc') !== -1 || id.indexOf('payment') !== -1 ||
                                         placeholder.indexOf('card') !== -1 || placeholder.indexOf('cvv') !== -1 || placeholder.indexOf('cvc') !== -1;

                    var isPaymentSubmit = false;
                    var submitKeywords = ['pay', 'purchase', 'buy', 'checkout', 'confirm payment', 'place order', 'transfer', 'subscribe'];
                    for (var i = 0; i < submitKeywords.length; i++) {
                        var kw = submitKeywords[i];
                        if (text.indexOf(kw) !== -1 || id.indexOf(kw) !== -1 || name.indexOf(kw) !== -1 || ariaLabel.indexOf(kw) !== -1) {
                            isPaymentSubmit = true;
                            break;
                        }
                    }

                    if (isPassword || isOtp || isPaymentField || isPaymentSubmit) {
                        return JSON.stringify({
                            success: false,
                            atomicRecheckFailed: true,
                            error: 'Target element mutated or became sensitive prior to click execution'
                        });
                    }

                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    el.click();
                    return JSON.stringify({
                        success: true,
                        text: (el.innerText || el.textContent || '').trim().substring(0, 40)
                    });
                } catch (e) {
                    return JSON.stringify({
                        success: false,
                        error: 'Selector syntax error: ' + (e.message || 'invalid selector')
                    });
                }
            })();
        """.trimIndent()

        val resultDeferred = CompletableDeferred<String>()
        webView.evaluateJavascript(jsScript) { res ->
            resultDeferred.complete(res ?: "{}")
        }

        val res = withTimeoutOrNull(3000) { resultDeferred.await() } ?: "{}"
        try {
            val unquoted = if (res.startsWith("\"") && res.endsWith("\"")) {
                org.json.JSONTokener(res).nextValue().toString()
            } else res
            val json = JSONObject(unquoted)
            if (json.optBoolean("atomicRecheckFailed", false)) {
                return@withContext "ACTION_BLOCKED_MUTATION: ${json.optString("error", "Atomic sensitivity recheck failed")}"
            }
            if (json.optBoolean("success", false)) {
                return@withContext "Successfully clicked '$cleanTarget' (text: '${json.optString("text")}') on tab [$targetTabId]"
            } else {
                return@withContext "Click failed: ${json.optString("error", "Unknown error")}"
            }
        } catch (e: Exception) {
            "Executed click on '$cleanTarget' on tab [$targetTabId]"
        }
    }

    suspend fun fillInput(selectorOrId: String, text: String, tabId: String? = null): String = withContext(Dispatchers.Main) {
        val targetTabId = tabId ?: _activeTabId ?: return@withContext "Error: No active tab."
        val webView = webViews[targetTabId] ?: return@withContext "Error: WebView not found."
        val tab = synchronized(_tabs) { _tabs.firstOrNull { it.id == targetTabId } }

        val cleanTarget = selectorOrId.trim()
        val indexMatch = Regex("""^\[?#?(\d+)\]?$""").find(cleanTarget)?.groupValues?.get(1)

        val targetCssSelector = if (indexMatch != null) {
            "[data-agent-id=\"$indexMatch\"]"
        } else {
            cleanTarget
        }

        // CRITICAL SECURITY DEFENSE: Use JSONObject.quote for both selector and text values
        val quotedSelector = JSONObject.quote(targetCssSelector)
        val quotedText = JSONObject.quote(text)

        // 1. Inspect DOM for existence and sensitivity
        val inspection = inspectTargetElement(webView, quotedSelector)
        if (!inspection.exists) {
            return@withContext "Fill failed: ${inspection.error ?: "Input element not found for selector: $cleanTarget"}"
        }

        // 2. Request user confirmation (default deny, in-app dialog)
        val siteUrl = tab?.url?.ifBlank { "about:blank" } ?: "about:blank"
        val quotedSiteUrl = JSONObject.quote(siteUrl)
        val confirmation = requestActionConfirmation(
            tabId = targetTabId,
            site = siteUrl,
            action = "fill",
            target = cleanTarget,
            proposedValue = text,
            isSensitive = inspection.isSensitive,
            sensitiveReason = inspection.reason
        )

        // 3. Enforce confirmation policy: denial, dismissal, navigation, timeout, or sensitive block must perform no action
        when (confirmation) {
            BrowserConfirmationResult.BLOCKED_SENSITIVE -> {
                return@withContext "ACTION_BLOCKED_SENSITIVE: Fill action on sensitive input ($cleanTarget: ${inspection.reason ?: "Password, OTP, or Payment field"}) is strictly prohibited."
            }
            BrowserConfirmationResult.DENIED -> {
                return@withContext "ACTION_DENIED: Browser fill on '$cleanTarget' was denied by user."
            }
            BrowserConfirmationResult.DISMISSED -> {
                return@withContext "ACTION_DISMISSED: Browser fill confirmation dialog for '$cleanTarget' was dismissed."
            }
            BrowserConfirmationResult.TIMEOUT -> {
                return@withContext "ACTION_TIMEOUT: Browser fill confirmation for '$cleanTarget' timed out after 15s (default deny)."
            }
            BrowserConfirmationResult.NAVIGATED -> {
                return@withContext "ACTION_CANCELLED: Browser navigation occurred while waiting for fill confirmation on '$cleanTarget'."
            }
            BrowserConfirmationResult.ALLOWED -> {
                if (inspection.isSensitive) {
                    return@withContext "ACTION_BLOCKED_SENSITIVE: Cannot allow fill on sensitive field ($cleanTarget: ${inspection.reason})."
                }
                // Allowed - proceed to execute fill on WebView
            }
        }

        // 4. ATOMIC SYNCHRONOUS RECHECK & EXECUTION:
        // Synchronously re-inspect element sensitivity and current URL in the exact same JS execution
        // immediately before el.value = ... to eliminate DOM mutation race conditions
        val jsScript = """
            (function() {
                var selector = $quotedSelector;
                var expectedUrl = $quotedSiteUrl;
                try {
                    // 1. Atomic URL verification
                    var currentUrl = window.location.href || '';
                    if (expectedUrl && expectedUrl !== 'about:blank' && currentUrl !== expectedUrl) {
                        return JSON.stringify({
                            success: false,
                            atomicRecheckFailed: true,
                            error: 'Page URL changed during approval from ' + expectedUrl + ' to ' + currentUrl
                        });
                    }

                    var el = document.querySelector(selector);
                    if (!el) {
                        return JSON.stringify({
                            success: false,
                            error: 'Input element not found for selector: ' + selector
                        });
                    }

                    // 2. Synchronous Atomic Sensitivity Re-check immediately before fill
                    var tag = (el.tagName || '').toLowerCase();
                    var type = (el.getAttribute('type') || '').toLowerCase();
                    var name = (el.getAttribute('name') || '').toLowerCase();
                    var id = (el.getAttribute('id') || '').toLowerCase();
                    var autocomplete = (el.getAttribute('autocomplete') || '').toLowerCase();
                    var placeholder = (el.getAttribute('placeholder') || '').toLowerCase();
                    var ariaLabel = (el.getAttribute('aria-label') || '').toLowerCase();

                    var isPassword = type === 'password' || 
                                     autocomplete.indexOf('password') !== -1 || 
                                     name.indexOf('password') !== -1 || 
                                     id.indexOf('password') !== -1 ||
                                     placeholder.indexOf('password') !== -1;

                    var isOtp = autocomplete.indexOf('one-time-code') !== -1 || 
                                name.indexOf('otp') !== -1 || name.indexOf('2fa') !== -1 ||
                                id.indexOf('otp') !== -1 || id.indexOf('2fa') !== -1 ||
                                placeholder.indexOf('otp') !== -1 || placeholder.indexOf('2fa') !== -1 ||
                                ariaLabel.indexOf('otp') !== -1 || ariaLabel.indexOf('2fa') !== -1;

                    var isPaymentField = autocomplete.indexOf('cc-') !== -1 ||
                                         name.indexOf('card') !== -1 || name.indexOf('cvv') !== -1 || name.indexOf('cvc') !== -1 || name.indexOf('payment') !== -1 ||
                                         id.indexOf('card') !== -1 || id.indexOf('cvv') !== -1 || id.indexOf('cvc') !== -1 || id.indexOf('payment') !== -1 ||
                                         placeholder.indexOf('card') !== -1 || placeholder.indexOf('cvv') !== -1 || placeholder.indexOf('cvc') !== -1;

                    if (isPassword || isOtp || isPaymentField) {
                        return JSON.stringify({
                            success: false,
                            atomicRecheckFailed: true,
                            error: 'Target input mutated or became sensitive prior to fill execution'
                        });
                    }

                    el.focus();
                    el.value = $quotedText;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return JSON.stringify({ success: true });
                } catch (e) {
                    return JSON.stringify({
                        success: false,
                        error: 'Selector syntax error: ' + (e.message || 'invalid selector')
                    });
                }
            })();
        """.trimIndent()

        val resultDeferred = CompletableDeferred<String>()
        webView.evaluateJavascript(jsScript) { res ->
            resultDeferred.complete(res ?: "{}")
        }

        val res = withTimeoutOrNull(3000) { resultDeferred.await() } ?: "{}"
        try {
            val unquoted = if (res.startsWith("\"") && res.endsWith("\"")) {
                org.json.JSONTokener(res).nextValue().toString()
            } else res
            val json = JSONObject(unquoted)
            if (json.optBoolean("atomicRecheckFailed", false)) {
                return@withContext "ACTION_BLOCKED_MUTATION: ${json.optString("error", "Atomic sensitivity recheck failed")}"
            }
            if (json.optBoolean("success", false)) {
                "Successfully filled '$cleanTarget' on tab [$targetTabId]"
            } else {
                "Fill failed: ${json.optString("error", "Unknown error")}"
            }
        } catch (e: Exception) {
            "Filled '$cleanTarget' on tab [$targetTabId]"
        }
    }

    suspend fun scroll(direction: String, tabId: String? = null): String = withContext(Dispatchers.Main) {
        val targetTabId = tabId ?: _activeTabId ?: return@withContext "Error: No active tab."
        val webView = webViews[targetTabId] ?: return@withContext "Error: WebView not found."

        val deltaY = if (direction.equals("up", ignoreCase = true)) -500 else 500
        val jsScript = "window.scrollBy({ top: $deltaY, behavior: 'smooth' });"
        webView.evaluateJavascript(jsScript, null)
        "Scrolled ${if (deltaY > 0) "down" else "up"} by 500px on tab [$targetTabId]"
    }

    fun listTabs(): String {
        val tabList = synchronized(_tabs) { _tabs.toList() }
        if (tabList.isEmpty()) return "No open browser tabs."
        return buildString {
            appendLine("Open Browser Tabs (${tabList.size}):")
            tabList.forEachIndexed { idx, tab ->
                val activeIndicator = if (tab.id == _activeTabId) " [ACTIVE]" else ""
                val securityIndicator = if (tab.isHttps) "🔒 HTTPS" else "⚠️ HTTP"
                appendLine("• [${idx + 1}] ID: ${tab.id}$activeIndicator")
                appendLine("    Title: ${tab.title}")
                appendLine("    URL: ${tab.url} ($securityIndicator)")
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed == "about:blank") return "about:blank"
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }
}
