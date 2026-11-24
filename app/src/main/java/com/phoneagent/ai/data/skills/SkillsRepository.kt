package com.phoneagent.ai.data.skills

import android.content.Context
import com.phoneagent.ai.data.model.SkillItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillsRepository(private val context: Context) {

    private val skillMetadata = listOf(
        SkillMeta("autoplan", "Autoplan", "Autonomous multi-phase review pipeline (CEO, design, eng, DX)", listOf("autoplan", "plan review", "run reviews")),
        SkillMeta("build-check", "Build Check", "Autonomous build verification, compiler diagnostics and clean compile tests", listOf("build-check", "check build", "compile check")),
        SkillMeta("careful", "Careful", "Destructive command safety guardrails and system boundaries protection", listOf("careful", "be careful", "safety mode")),
        SkillMeta("review", "Code Review", "Comprehensive code review across logic, error handling, edge cases, and cleanliness", listOf("review", "code review", "audit code")),
        SkillMeta("qa", "QA Test", "Interactive and automated quality assurance, regression testing and defect discovery", listOf("qa", "qa test", "test site")),
        SkillMeta("ship", "Ship", "Pre-ship verification, change logs, branch safety checks, and deployment preparation", listOf("ship", "ship it", "prepare release")),
        SkillMeta("health", "Health Check", "Agent diagnostics, environment check, token budget and runtime integrity", listOf("health", "system health", "diagnostics")),
        SkillMeta("design-review", "Design Review", "UX consistency, mobile touch targets, dark/AMOLED accessibility, soft rounded UI audit", listOf("design-review", "ui audit", "mobile design")),
        SkillMeta("daily-planner", "Daily Planner", "Everyday planning, priority matrix, time-blocking, task organization, and daily agenda", listOf("daily-planner", "planner", "plan my day", "daily agenda")),
        SkillMeta("executive-summary", "Executive Summary", "Key takeaways, bulleted action items, risk analysis, and high-level briefing memo", listOf("executive-summary", "summary", "summarize", "exec summary")),
        SkillMeta("research-brief", "Research Brief", "Structured topic exploration, verified sources, balanced pros/cons, synthesis, and takeaways", listOf("research-brief", "research", "deep research", "brief")),
        SkillMeta("document-creator", "Document Creator", "Generate native multi-page PDF reports and editable OpenXML PowerPoint presentations (PPTX)", listOf("document-creator", "create pdf", "create pptx", "presentation", "slides"))
    )

    private var loadedSkills: List<SkillItem>? = null

    suspend fun getSkills(): List<SkillItem> = withContext(Dispatchers.IO) {
        if (loadedSkills != null) return@withContext loadedSkills!!

        val list = mutableListOf<SkillItem>()
        for (meta in skillMetadata) {
            val content = try {
                context.assets.open("skills/${meta.id}.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "# ${meta.name}\n\n${meta.description}"
            }
            list.add(
                SkillItem(
                    id = meta.id,
                    name = meta.name,
                    description = meta.description,
                    triggers = meta.triggers,
                    content = content,
                    isEnabled = true,
                    isBuiltIn = true
                )
            )
        }
        loadedSkills = list
        list
    }

    private data class SkillMeta(
        val id: String,
        val name: String,
        val description: String,
        val triggers: List<String>
    )
}
