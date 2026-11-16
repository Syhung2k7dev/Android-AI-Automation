package com.syhung.androidaiautomation.agent.casl

import org.json.JSONArray
import org.json.JSONObject

data class CaslValidationResult(val valid: Boolean, val errors: List<String>, val warnings: List<String>)

object CaslValidator {
    private val allowedOperations = setOf(
        "INPUT", "CONTEXT", "READ_RECORD", "SEARCH_KNOWLEDGE", "READ_MEMORY",
        "GET_SOURCE", "REASON", "RESEARCH", "SUMMARIZE_EVIDENCE", "REQUEST_USER_CONFIRMATION",
        "ANALYZE_IMAGE", "GET_CROP", "GET_SCENE_MAP", "OCR_QUERY", "MATCH_TEMPLATE",
        "CALCULATE", "COMPARE", "VALIDATE", "PLAN_TASKS", "CALL_SKILL", "WRITE_MEMORY",
        "UPDATE_RECORD", "CREATE_TASK", "CREATE_REMINDER", "IF", "ELSE", "END_IF",
        "LOOP", "END_LOOP", "WAIT", "OUTPUT", "ON_ERROR"
    )
    private val allowedCapabilities = setOf(
        "knowledge.read", "account.read", "account.update_user_fields", "layout.read",
        "layout.analyze", "army.read", "calculation.run", "vision.local_analyze",
        "vision.cloud_analyze", "memory.read", "memory.write", "task.create",
        "reminder.create", "gemini.reason", "gemini.research"
    )

    fun validate(manifestText: String, workflowText: String): CaslValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val manifest = try { JSONObject(manifestText) } catch (e: Exception) {
            return CaslValidationResult(false, listOf("Manifest JSON is invalid: " + e.message), emptyList())
        }
        val workflow = try { JSONObject(workflowText) } catch (e: Exception) {
            return CaslValidationResult(false, listOf("Workflow JSON is invalid: " + e.message), emptyList())
        }
        if (manifest.optString("skill_id").isBlank()) errors += "skill_id is required"
        if (manifest.optString("name").isBlank()) errors += "name is required"
        if (manifest.optString("skill_version").isBlank()) errors += "skill_version is required"
        val requested = manifest.optJSONArray("capabilities") ?: JSONArray()
        for (i in 0 until requested.length()) {
            val capability = requested.optString(i)
            if (capability !in allowedCapabilities) errors += "Capability is not allowed: " + capability
        }
        val steps = workflow.optJSONArray("steps") ?: run { errors += "workflow.steps must be an array"; JSONArray() }
        if (steps.length() !in 1..100) errors += "workflow must contain 1–100 steps"
        val ids = mutableSetOf<String>()
        val stack = mutableListOf<Pair<String, Int>>()
        val elseSeen = mutableSetOf<Int>()
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i)
            if (step == null) { errors += "Step " + i + " is not an object"; continue }
            val id = step.optString("id")
            if (id.isBlank()) errors += "Step " + i + " has no id" else if (!ids.add(id)) errors += "Duplicate step id: " + id
            val op = step.optString("op").uppercase()
            if (op !in allowedOperations) errors += "Unknown operation at step " + i + ": " + op
            when (op) {
                "IF" -> stack.add("IF" to i)
                "ELSE" -> { val top = stack.lastOrNull(); if (top?.first != "IF" || !elseSeen.add(top.second)) errors += "ELSE without matching IF or duplicate ELSE at step " + i }
                "END_IF" -> if (stack.removeLastOrNull()?.first != "IF") errors += "END_IF without IF at step " + i
                "LOOP" -> { val count = step.optInt("count", 0); if (count !in 1..20) errors += "LOOP at step " + i + " requires count from 1 to 20"; stack.add("LOOP" to i) }
                "END_LOOP" -> if (stack.removeLastOrNull()?.first != "LOOP") errors += "END_LOOP without LOOP at step " + i
                "WAIT" -> { val ms = step.optLong("duration_ms", -1); if (ms !in 0..30000) errors += "WAIT at step " + i + " must be 0–30000 ms" }
            }
        }
        if (stack.isNotEmpty()) errors += "Unclosed " + stack.last().first + " block"
        if (workflow.optInt("version", 0) != 1) errors += "Unsupported CASL workflow version"
        if (manifest.optString("status", "draft") == "active") warnings += "Active status is ignored until explicit user approval"
        return CaslValidationResult(errors.isEmpty(), errors, warnings)
    }
}