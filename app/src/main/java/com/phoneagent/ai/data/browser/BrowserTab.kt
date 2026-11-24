package com.phoneagent.ai.data.browser

import android.webkit.WebView
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "about:blank",
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var isHttps: Boolean = false,
    var lastObservedDom: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
