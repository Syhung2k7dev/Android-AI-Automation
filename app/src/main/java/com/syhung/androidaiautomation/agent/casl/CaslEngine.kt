package com.syhung.androidaiautomation.agent.casl

import org.json.JSONObject

data class CaslStepResult(val stepId: String, val success: Boolean, val output: JSONObject = JSONObject(), val error: String? = null)
data class CaslExecutionResult(val success: Boolean, val results: List<CaslStepResult>, val error: String? = null)

fun interface CaslCapability {
    fun execute(input: JSONObject): JSONObject
}

/**
 * Executes only explicitly registered capabilities. It does not evaluate expressions,
 * load classes, access files, or perform arbitrary network/platform operations.
 */
class CaslEngine(private val capabilities: Map<String, CaslCapability>) {
    fun execute(workflowText: String, approved: Boolean): CaslExecutionResult {
        if (!approved) return CaslExecutionResult(false, emptyList(), "Skill has not been approved by the user")
        val workflow = try { JSONObject(workflowText) } catch (e: Exception) {
            return CaslExecutionResult(false, emptyList(), "Invalid workflow JSON")
        }
        val steps = workflow.optJSONArray("steps")
            ?: return CaslExecutionResult(false, emptyList(), "Missing steps")
        if (steps.length() > 100) return CaslExecutionResult(false, emptyList(), "Step limit exceeded")
        val results = mutableListOf<CaslStepResult>()
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: return CaslExecutionResult(false, results, "Invalid step $i")
            val op = step.optString("op").uppercase()
            val id = step.optString("id", "step-$i")
            if (op in setOf("IF", "ELSE", "END_IF", "LOOP", "WAIT")) {
                return CaslExecutionResult(false, results, "Control-flow execution provider is not installed for $op")
            }
            val provider = capabilities[op]
                ?: return CaslExecutionResult(false, results, "No capability provider registered for $op")
            try {
                val output = provider.execute(step.optJSONObject("input") ?: JSONObject())
                results += CaslStepResult(id, true, output)
            } catch (e: Exception) {
                results += CaslStepResult(id, false, error = e.message ?: "Capability failed")
                return CaslExecutionResult(false, results, "Execution stopped at $id")
            }
        }
        return CaslExecutionResult(true, results)
    }
}
