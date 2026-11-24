package com.phoneagent.ai.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneagent.ai.data.model.AttachmentItem
import com.phoneagent.ai.data.model.ChatMessage
import com.phoneagent.ai.data.model.MessageRole
import com.phoneagent.ai.data.model.MessageStatus
import com.phoneagent.ai.data.model.SkillItem
import com.phoneagent.ai.ui.theme.LocalAppColors
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    content: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .padding(16.dp)
    ) {
        content()
    }
}

fun parseDocumentAttachment(text: String): Pair<JSONObject?, String> {
    val startTag = "[DOCUMENT_ATTACHMENT_JSON]"
    val endTag = "[/DOCUMENT_ATTACHMENT_JSON]"
    val startIdx = text.indexOf(startTag)
    val endIdx = text.indexOf(endTag)
    if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
        val jsonStr = text.substring(startIdx + startTag.length, endIdx).trim()
        val remaining = (text.substring(0, startIdx) + text.substring(endIdx + endTag.length)).trim()
        return try {
            Pair(JSONObject(jsonStr), remaining)
        } catch (e: Exception) {
            Pair(null, text)
        }
    }
    return Pair(null, text)
}

@Composable
fun DocumentAttachmentCard(docJson: JSONObject) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val fileName = docJson.optString("fileName", "Document")
    val fileType = docJson.optString("fileType", "DOC")
    val unitCount = docJson.optInt("unitCount", 1)
    val fileSizeBytes = docJson.optLong("fileSizeBytes", 0L)
    val contentUriStr = docJson.optString("contentUri", "")
    val isPdf = fileType.equals("PDF", ignoreCase = true)
    val unitLabel = if (isPdf) "$unitCount pages" else "$unitCount slides"
    val sizeLabel = if (fileSizeBytes > 1024 * 1024) {
        String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
    } else {
        String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024.0)
    }

    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.accent.copy(alpha = 0.4f), cardShape)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPdf) "PDF" else "PPTX",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    ),
                    maxLines = 1
                )
                Text(
                    text = "$unitLabel • $sizeLabel",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (contentUriStr.isNotBlank()) {
                        try {
                            val uri = Uri.parse(contentUriStr)
                            val mime = if (isPdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Open", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }

            OutlinedButton(
                onClick = {
                    if (contentUriStr.isNotBlank()) {
                        try {
                            val uri = Uri.parse(contentUriStr)
                            val mime = if (isPdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(sendIntent, "Share $fileName").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Share", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onCopyText: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    isSpeaking: Boolean = false
) {
    val colors = LocalAppColors.current
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL

    if (isTool) {
        ToolResultBubble(message)
        return
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
    }

    val bubbleBg = if (isUser) colors.userBubble else colors.assistantBubble
    val bubbleBorder = if (isUser) colors.border else colors.border

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role & Info Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = if (isUser) "You" else "PhoneAgent",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUser) colors.textSecondary else colors.accent
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            Text(
                text = timeStr,
                style = TextStyle(fontSize = 11.sp, color = colors.textMuted)
            )
        }

        // Attached files chips
        if (message.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (att in message.attachments) {
                    AttachmentChip(att)
                }
            }
        }

        // Thought / Reasoning accordion (for models like DeepSeek-R1)
        if (!message.thought.isNullOrBlank()) {
            ThoughtAccordion(message.thought)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Main Bubble Box
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .border(1.dp, bubbleBorder, bubbleShape)
                .padding(14.dp)
        ) {
            Column {
                if (message.status == MessageStatus.STREAMING && message.content.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Thinking...",
                            style = TextStyle(fontSize = 14.sp, color = colors.textSecondary)
                        )
                    }
                } else if (message.status == MessageStatus.ERROR) {
                    Text(
                        text = "⚠️ ${message.errorMessage ?: "Inference request failed"}",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = colors.error,
                            lineHeight = 20.sp
                        )
                    )
                } else {
                    val (docAttachment, cleanContent) = parseDocumentAttachment(message.content)
                    if (docAttachment != null) {
                        DocumentAttachmentCard(docAttachment)
                        if (cleanContent.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = cleanContent,
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    } else {
                        Text(
                            text = message.content,
                            style = TextStyle(
                                fontSize = 15.sp,
                                color = colors.textPrimary,
                                lineHeight = 22.sp
                            )
                        )
                    }
                }

                // Actions row
                if (message.content.isNotEmpty()) {
                    val (_, cleanContent) = parseDocumentAttachment(message.content)
                    val copyableText = cleanContent.ifBlank { message.content }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onCopyText(copyableText) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (!isUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onSpeakText(copyableText) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop Speaking" else "Read Aloud",
                                    tint = if (isSpeaking) colors.accent else colors.textMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThoughtAccordion(thought: String) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotate")

    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.border, shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Reasoning Process",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = thought,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun ToolResultBubble(message: ChatMessage) {
    val colors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.border, shape)
            .clickable { expanded = !expanded }
            .padding(10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚙️ Tool: ${message.toolName ?: "native_tool"}",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "Hide Output" else "View Output",
                    style = TextStyle(fontSize = 11.sp, color = colors.textMuted)
                )
            }
            val (docAttachment, cleanOutput) = parseDocumentAttachment(message.content)
            if (docAttachment != null) {
                Spacer(modifier = Modifier.height(8.dp))
                DocumentAttachmentCard(docAttachment)
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = cleanOutput.ifBlank { message.content },
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textPrimary,
                        lineHeight = 17.sp
                    ),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AttachmentChip(attachment: AttachmentItem) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(colors.surfaceHighlight)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = attachment.fileName.take(18),
            style = TextStyle(fontSize = 11.sp, color = colors.textPrimary)
        )
    }
}

@Composable
fun SkillQuickChips(
    skills: List<SkillItem>,
    selectedSkill: SkillItem?,
    onSelectSkill: (SkillItem?) -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (skill in skills) {
            val isSelected = selectedSkill?.id == skill.id
            val shape = RoundedCornerShape(16.dp)
            val bg = if (isSelected) colors.accent else colors.surfaceVariant
            val textColor = if (isSelected) colors.onAccent else colors.textSecondary
            val border = if (isSelected) colors.accent else colors.border

            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, border, shape)
                    .clickable {
                        if (isSelected) onSelectSkill(null) else onSelectSkill(skill)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = skill.name,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                )
            }
        }
    }
}

@Composable
fun VoiceRmsWaveform(
    rmsLevel: Float,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    // 11 vertical equalizer bars responding dynamically to voice RMS level
    val multipliers = listOf(0.35f, 0.6f, 0.85f, 1.1f, 0.75f, 1.25f, 0.9f, 1.15f, 0.8f, 0.55f, 0.4f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        multipliers.forEach { mult ->
            val effectiveHeight = (4.dp + (14.dp * (rmsLevel * mult).coerceIn(0.08f, 1.0f)))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(effectiveHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    onAttachFile: () -> Unit,
    onAttachCamera: () -> Unit,
    onStartMic: () -> Unit,
    attachments: List<AttachmentItem>,
    onRemoveAttachment: (AttachmentItem) -> Unit,
    voiceLanguage: com.phoneagent.ai.data.model.VoiceLanguage = com.phoneagent.ai.data.model.VoiceLanguage.AUTO,
    onCycleVoiceLanguage: () -> Unit = {},
    isVoiceRecording: Boolean = false,
    rmsLevel: Float = 0f,
    voiceTranscript: String = "",
    speechError: String? = null,
    onCancelVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onSendVoice: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val barShape = RoundedCornerShape(26.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Pending attachments preview
        if (attachments.isNotEmpty() && !isVoiceRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (att in attachments) {
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(shape)
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.border, shape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = att.fileName.take(15),
                            style = TextStyle(fontSize = 11.sp, color = colors.textPrimary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = colors.textMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onRemoveAttachment(att) }
                        )
                    }
                }
            }
        }

        if (isVoiceRecording) {
            // IN-COMPOSER RECORDING BAR:
            // Cancel (X) left | Real RMS waveform & transcript preview center | Stop (Square) & Send (Arrow) right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.surfaceVariant)
                    .border(1.5.dp, colors.accent.copy(alpha = 0.6f), barShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel X on left (discards recorded speech, restores prior draft)
                IconButton(
                    onClick = onCancelVoice,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Voice Recording",
                        tint = colors.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Center: Editable transcript preview when available + real RMS waveform
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (voiceTranscript.isNotBlank()) {
                        Text(
                            text = voiceTranscript,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = speechError ?: "Listening (${voiceLanguage.shortTag})...",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = if (speechError != null) colors.error else colors.textSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    VoiceRmsWaveform(
                        rmsLevel = rmsLevel,
                        accentColor = colors.accent
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Stop square on right (finalizes speech to editable draft)
                IconButton(
                    onClick = onStopVoice,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Recording (Keep Draft)",
                        tint = colors.warning,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Send arrow on right (submits recognized text only after user tap)
                val canSendVoice = voiceTranscript.isNotBlank() || text.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canSendVoice) colors.accent else colors.surface)
                        .clickable(enabled = canSendVoice) { onSendVoice() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Recognized Text",
                        tint = if (canSendVoice) colors.onAccent else colors.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        } else {
            // STANDARD CHAT COMPOSER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, barShape)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach file
                IconButton(
                    onClick = onAttachFile,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach File",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Camera
                IconButton(
                    onClick = onAttachCamera,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Take Photo",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Mic with Language Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    IconButton(
                        onClick = onStartMic,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Dictate",
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.copy(alpha = 0.15f))
                            .clickable { onCycleVoiceLanguage() }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = voiceLanguage.shortTag,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        )
                    }
                }

                // Text field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Message Paradox AI...",
                            style = TextStyle(fontSize = 15.sp, color = colors.textMuted)
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChanged,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (text.isNotBlank() || attachments.isNotEmpty()) onSend()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Send / Stop Pill Button
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.error)
                            .clickable { onStop() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = colors.onAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (text.isNotBlank() || attachments.isNotEmpty()) colors.accent else colors.surfaceVariant)
                            .clickable(enabled = text.isNotBlank() || attachments.isNotEmpty()) {
                                onSend()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (text.isNotBlank() || attachments.isNotEmpty()) colors.onAccent else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
