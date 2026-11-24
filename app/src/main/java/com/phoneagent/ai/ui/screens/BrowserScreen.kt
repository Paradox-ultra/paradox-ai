package com.phoneagent.ai.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.phoneagent.ai.data.browser.AgenticBrowserController
import com.phoneagent.ai.data.browser.BrowserTab
import com.phoneagent.ai.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    browserController: AgenticBrowserController,
    onBack: () -> Unit,
    onSendPromptToAgent: (String) -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    val tabList = remember { mutableStateListOf<BrowserTab>() }
    var activeTabId by remember { mutableStateOf(browserController.activeTabId) }
    var urlInput by remember { mutableStateOf("") }
    var showDomSheet by remember { mutableStateOf(false) }
    var observedDomText by remember { mutableStateOf("") }
    var isObserving by remember { mutableStateOf(false) }

    fun refreshTabs() {
        tabList.clear()
        tabList.addAll(browserController.tabs)
        activeTabId = browserController.activeTabId
        val currentTab = browserController.getActiveTab()
        if (currentTab != null && !currentTab.isLoading) {
            urlInput = currentTab.url
        }
    }

    DisposableEffect(browserController) {
        browserController.onTabChangedListener = {
            refreshTabs()
        }
        refreshTabs()
        onDispose {
            browserController.onTabChangedListener = null
        }
    }

    val activeTab = browserController.getActiveTab()
    val activeWebView = browserController.getActiveWebView()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // --- 1. TOP APP BAR / ADDRESS BAR ---
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Security Badge
                    val isHttps = activeTab?.isHttps == true
                    Icon(
                        imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = if (isHttps) "HTTPS" else "HTTP",
                        tint = if (isHttps) Color(0xFF10B981) else Color(0xFFEAB308),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        placeholder = { Text("Search or enter web URL", fontSize = 13.sp) },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (urlInput.isNotBlank()) {
                                    scope.launch {
                                        browserController.navigate(urlInput)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Go",
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.surfaceVariant,
                            unfocusedContainerColor = colors.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = {
                        activeWebView?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = {
                        isObserving = true
                        showDomSheet = true
                        scope.launch {
                            observedDomText = browserController.observeDom()
                            isObserving = false
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Inspect Agent DOM",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
        )

        // --- 2. PARALLEL TABS ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabList.forEach { tab ->
                val isSelected = tab.id == activeTabId
                val shape = RoundedCornerShape(10.dp)
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isSelected) colors.surfaceVariant else colors.background)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
                        .clickable {
                            browserController.switchTab(tab.id)
                            refreshTabs()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tab.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = colors.accent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = tab.title.take(18),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.textPrimary else colors.textSecondary
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        tint = colors.textMuted,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                browserController.closeTab(tab.id)
                                refreshTabs()
                            }
                    )
                }
            }

            // New Tab button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .clickable {
                        browserController.createTab("https://en.m.wikipedia.org")
                        refreshTabs()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Parallel Tab",
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- 3. WEBVIEW CONTAINER ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            if (activeWebView != null) {
                AndroidView(
                    factory = {
                        // Ensure webview is detached from any previous parent before attaching
                        (activeWebView.parent as? ViewGroup)?.removeView(activeWebView)
                        activeWebView
                    },
                    update = { view ->
                        // No-op or dynamic view updates
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active tab. Tap '+' to create a tab.", color = Color.Gray)
                }
            }
        }
    }

    // --- 4. DOM INSPECTOR SHEET ---
    if (showDomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDomSheet = false },
            sheetState = sheetState,
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agent Semantic DOM Snapshot",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    )
                    IconButton(onClick = { showDomSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                    }
                }

                Text(
                    text = "This bounded snapshot shows interactive elements discovered by the AI Agent. Passwords and credit cards are strictly masked as [REDACTED_SECRET].",
                    style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                )

                if (isObserving) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.background)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = observedDomText,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}
