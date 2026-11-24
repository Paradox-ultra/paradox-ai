package com.phoneagent.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneagent.ai.data.model.SkillItem
import com.phoneagent.ai.ui.components.SoftCard
import com.phoneagent.ai.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    skills: List<SkillItem>,
    selectedSkill: SkillItem?,
    onSelectSkill: (SkillItem?) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "gstack MIT Text Skills",
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Bundled MIT-licensed prompt methodologies from local Claude skills. Tap to activate a skill for your active chat.",
                    style = TextStyle(fontSize = 13.sp, color = colors.textMuted)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(skills, key = { it.id }) { skill ->
                var expanded by remember { mutableStateOf(false) }
                val isSelected = selectedSkill?.id == skill.id
                val shape = RoundedCornerShape(18.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (isSelected) colors.surfaceVariant else colors.surface)
                        .border(1.dp, if (isSelected) colors.accent else colors.border, shape)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = skill.name,
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.accent else colors.textPrimary
                                    )
                                )
                                Text(
                                    text = skill.description,
                                    style = TextStyle(fontSize = 12.sp, color = colors.textSecondary),
                                    maxLines = if (expanded) 10 else 2
                                )
                            }
                            IconButton(onClick = {
                                if (isSelected) onSelectSkill(null) else onSelectSkill(skill)
                            }) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.accent else colors.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = colors.onAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Triggers
                        if (skill.triggers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (trig in skill.triggers) {
                                    val tShape = RoundedCornerShape(8.dp)
                                    Box(
                                        modifier = Modifier
                                            .clip(tShape)
                                            .background(colors.surfaceHighlight)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "/$trig",
                                            style = TextStyle(fontSize = 11.sp, color = colors.accent)
                                        )
                                    }
                                }
                            }
                        }

                        // Expand prompt preview
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = if (expanded) "Hide Prompt Methodology" else "View Prompt Methodology",
                                style = TextStyle(fontSize = 12.sp, color = colors.textMuted)
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = skill.content.take(1500) + if (skill.content.length > 1500) "\n...[truncated]" else "",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.textSecondary,
                                    lineHeight = 16.sp
                                ),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceHighlight)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
