package com.syhung.androidaiautomation.agent.casl

import org.json.JSONObject

data class CaslStepResult(val stepId: String, val success: Boolean, val output: JSONObject = JSONObject(), val error: String? = null)
data class CaslExecutionResult(val success: Boolean, val results: List<CaslStepResult>, val error: String? = null)

fun interface CaslCapability { fun execute(input: JSONObject): JSONObject }

/** Bounded interpreter. Only explicitly registered capabilities can perform operations. */
class CaslEngine(private val capabilities: Map<String, CaslCapability>) {
    fun execute(workflowText: String, approved: Boolean): CaslExecutionResult {
        if (!approved) return CaslExecutionResult(false, emptyList(), "Skill has not been approved by the user")
        val workflow = try { JSONObject(workflowText) } catch (_: Exception) { return CaslExecutionResult(false, emptyList(), "Invalid workflow JSON") }
        if (workflow.optInt("version", 0) != 1) return CaslExecutionResult(false, emptyList(), "Unsupported CASL version")
        val steps = workflow.optJSONArray("steps") ?: return CaslExecutionResult(false, emptyList(), "Missing steps")
        if (steps.length() !in 1..100) return CaslExecutionResult(false, emptyList(), "Step count must be 1–100")
        val ifElse = mutableMapOf<Int, Int>()
        val ifEnd = mutableMapOf<Int, Int>()
        val elseEnd = mutableMapOf<Int, Int>()
        val loopEnd = mutableMapOf<Int, Int>()
        val loopStart = mutableMapOf<Int, Int>()
        val stack = mutableListOf<Pair<String, Int>>()
        for (i in 0 until steps.length()) {
            val op = steps.optJSONObject(i)?.optString("op")?.uppercase() ?: return CaslExecutionResult(false, emptyList(), "Invalid step " + i)
            when (op) {
                "IF" -> stack.add("IF" to i)
                "ELSE" -> { val top = stack.lastOrNull(); if (top?.first != "IF" || ifElse.containsKey(top.second)) return CaslExecutionResult(false, emptyList(), "Invalid ELSE at " + i); ifElse[top.second] = i }
                "END_IF" -> { val top = stack.removeLastOrNull(); if (top?.first != "IF") return CaslExecutionResult(false, emptyList(), "Invalid END_IF at " + i); ifEnd[top.second] = i; if (ifElse.containsKey(top.second)) elseEnd[ifElse[top.second]!!] = i }
                "LOOP" -> stack.add("LOOP" to i)
                "END_LOOP" -> { val top = stack.removeLastOrNull(); if (top?.first != "LOOP") return CaslExecutionResult(false, emptyList(), "Invalid END_LOOP at " + i); loopEnd[top.second] = i; loopStart[i] = top.second }
            }
        }
        if (stack.isNotEmpty()) return CaslExecutionResult(false, emptyList(), "Unclosed control-flow block")
        val results = mutableListOf<CaslStepResult>()
        val loopRemaining = mutableMapOf<Int, Int>()
        var pc = 0
        var operations = 0
        while (pc < steps.length()) {
            if (++operations > 1000) return CaslExecutionResult(false, results, "Execution operation limit exceeded")
            val step = steps.optJSONObject(pc) ?: return CaslExecutionResult(false, results, "Invalid step " + pc)
            val op = step.optString("op").uppercase()
            val id = step.optString("id", "step-" + pc)
            try {
                when (op) {
                    "IF" -> {
                        val conditionOp = step.optString("condition_op").uppercase()
                        val provider = capabilities[conditionOp] ?: return CaslExecutionResult(false, results, "Condition capability not registered: " + conditionOp)
                        val matched = provider.execute(step.optJSONObject("condition_input") ?: JSONObject()).optBoolean("matched", false)
                        if (matched) pc++ else pc = (ifElse[pc] ?: ifEnd[pc] ?: return CaslExecutionResult(false, results, "Invalid IF block")) + 1
                    }
                    "ELSE" -> pc = (elseEnd[pc] ?: return CaslExecutionResult(false, results, "Invalid ELSE block")) + 1
                    "END_IF" -> pc++
                    "LOOP" -> { val count = step.optInt("count", 0); if (count !in 1..20) return CaslExecutionResult(false, results, "LOOP count must be 1–20"); loopRemaining[pc] = count; pc++ }
                    "END_LOOP" -> { val start = loopStart[pc] ?: return CaslExecutionResult(false, results, "Invalid LOOP block"); val left = (loopRemaining[start] ?: 1) - 1; if (left > 0) { loopRemaining[start] = left; pc = start + 1 } else { loopRemaining.remove(start); pc++ } }
                    "WAIT" -> { val duration = step.optLong("duration_ms", -1); if (duration !in 0..30000) return CaslExecutionResult(false, results, "WAIT duration must be 0–30000 ms"); Thread.sleep(duration); results += CaslStepResult(id, true, JSONObject().put("waited_ms", duration)); pc++ }
                    "END_LOOP" -> pc++
                    else -> { val provider = capabilities[op] ?: return CaslExecutionResult(false, results, "No capability provider registered for " + op); val output = provider.execute(step.optJSONObject("input") ?: JSONObject()); results += CaslStepResult(id, true, output); pc++ }
                }
            } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return CaslExecutionResult(false, results, "Execution interrupted") }
            catch (e: Exception) { results += CaslStepResult(id, false, error = e.message ?: "Capability failed"); return CaslExecutionResult(false, results, "Execution stopped at " + id) }
        }
        return CaslExecutionResult(true, results)
    }
}