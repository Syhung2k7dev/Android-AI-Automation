package com.syhung.androidaiautomation.agent

import android.content.Context
import com.syhung.androidaiautomation.agent.casl.CaslValidator
import com.syhung.androidaiautomation.agent.data.CadssRecord
import com.syhung.androidaiautomation.agent.data.CadssRepository
import org.json.JSONObject

data class AgentAssessment(
    val accepted: Boolean,
    val summary: String,
    val relatedRecords: List<CadssRecord>,
    val warnings: List<String>
)

/**
 * Local-first orchestration boundary. Gemini adapters can be injected by the host,
 * while data retrieval and validation remain deterministic and available offline.
 */
class AgentCore(context: Context) {
    val repository = CadssRepository(context.applicationContext)

    fun findContext(recordTypes: List<String>, limitPerType: Int = 30): List<CadssRecord> =
        recordTypes.flatMap { repository.list(it, limit = limitPerType.coerceIn(1, 100)) }

    fun validateSkill(manifest: String, workflow: String) = CaslValidator.validate(manifest, workflow)

    fun createAssessment(title: String, details: String, relatedIds: List<String>): AgentAssessment {
        val records = relatedIds.mapNotNull(repository::get)
        val warnings = mutableListOf<String>()
        if (records.size != relatedIds.distinct().size) warnings += "Some referenced records could not be found"
        val payload = JSONObject().put("title", title).put("details", details)
            .put("related_ids", org.json.JSONArray(relatedIds)).toString()
        repository.put(CadssRecord(type = "agent_assessment", payload = payload, sourceRefs = relatedIds))
        return AgentAssessment(true, title, records, warnings)
    }
}
