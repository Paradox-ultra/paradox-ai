package com.phoneagent.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneagent.ai.data.model.MemoryItem
import com.phoneagent.ai.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    memories: List<MemoryItem>,
    onAddMemory: (String, String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllMemories: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredMemories = if (searchQuery.isBlank()) {
        memories
    } else {
        memories.filter {
            it.key.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Agent Memory",
                    style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Fact",
                        tint = colors.accent
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
        )

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search memories...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = colors.textMuted)
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredMemories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No memories stored yet",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                            )
                            Text(
                                text = "Tap '+' above to add facts, or ask PhoneAgent to remember them in chat.",
                                style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                            )
                        }
                    }
                }
            } else {
                items(filteredMemories, key = { it.id }) { memory ->
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(colors.surface)
                            .border(1.dp, colors.border, shape)
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val catShape = RoundedCornerShape(6.dp)
                                    Box(
                                        modifier = Modifier
                                            .clip(catShape)
                                            .background(colors.surfaceVariant)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = memory.category.uppercase(),
                                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = memory.key,
                                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = memory.content,
                                    style = TextStyle(fontSize = 13.sp, color = colors.textSecondary, lineHeight = 18.sp)
                                )
                            }
                            IconButton(onClick = { onDeleteMemory(memory.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onClearAllMemories,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear All Memories", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        var keyInput by remember { mutableStateOf("") }
        var contentInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("general") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Memory Item", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Key / Identifier (e.g. user_project)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        label = { Text("Memory Fact or Detail") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Category (preference, project, contact)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank() && contentInput.isNotBlank()) {
                            onAddMemory(keyInput.trim(), contentInput.trim(), categoryInput.trim())
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Fact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}
