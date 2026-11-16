package com.syhung.androidaiautomation

import org.json.JSONArray
import org.json.JSONObject

object WorkflowJsonCodec {
    fun encode(steps: List<WorkflowStep>): String {
        require(WorkflowStep.validateStructure(steps))
        val array = JSONArray()
        steps.forEach { step ->
            val item = JSONObject()
            when (step) {
                is WorkflowStep.Tap -> item.put("type", "tap").put("x", step.x).put("y", step.y).put("durationMs", step.durationMs)
                is WorkflowStep.Swipe -> item.put("type", "swipe").put("startX", step.startX).put("startY", step.startY).put("endX", step.endX).put("endY", step.endY).put("durationMs", step.durationMs)
                is WorkflowStep.Wait -> item.put("type", "wait").put("durationMs", step.durationMs)
                is WorkflowStep.WaitForText -> item.put("type", "waitForText").put("text", step.text).put("timeoutMs", step.timeoutMs).put("pollIntervalMs", step.pollIntervalMs)
                is WorkflowStep.WaitForImage -> item.put("type", "waitForImage").put("template", step.template).put("timeoutMs", step.timeoutMs).put("pollIntervalMs", step.pollIntervalMs).put("threshold", step.threshold.toDouble())
                is WorkflowStep.IfText -> item.put("type", "ifText").put("text", step.text)
                is WorkflowStep.IfImage -> item.put("type", "ifImage").put("template", step.template).put("threshold", step.threshold.toDouble())
                WorkflowStep.ElseBranch -> item.put("type", "else")
                WorkflowStep.EndIf -> item.put("type", "endIf")
            }
            array.put(item)
        }
        return JSONObject().put("format", "AIA_WORKFLOW").put("version", 2).put("steps", array).toString(2)
    }

    fun decode(content: String): List<WorkflowStep>? {
        return try {
            val root = JSONObject(content)
            if (root.optString("format") != "AIA_WORKFLOW" || root.optInt("version") !in 1..2) return null
            val array = root.optJSONArray("steps") ?: return null
            if (array.length() !in 1..50) return null
            val steps = ArrayList<WorkflowStep>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: return null
                val step = when (item.optString("type")) {
                    "tap" -> WorkflowStep.decode("T|${item.optDouble("x", Double.NaN)}|${item.optDouble("y", Double.NaN)}|${item.optLong("durationMs", -1)}")
                    "swipe" -> WorkflowStep.decode("S|${item.optDouble("startX", Double.NaN)}|${item.optDouble("startY", Double.NaN)}|${item.optDouble("endX", Double.NaN)}|${item.optDouble("endY", Double.NaN)}|${item.optLong("durationMs", -1)}")
                    "wait" -> WorkflowStep.decode("W|${item.optLong("durationMs", -1)}")
                    "waitForText" -> WorkflowStep.decode("C|${item.optString("text")}|${item.optLong("timeoutMs", -1)}|${item.optLong("pollIntervalMs", -1)}")
                    "waitForImage" -> WorkflowStep.decode("I|${item.optString("template")}|${item.optLong("timeoutMs", -1)}|${item.optLong("pollIntervalMs", -1)}|${item.optDouble("threshold", Double.NaN)}")
                    "ifText" -> WorkflowStep.decode("FT|${item.optString("text")}")
                    "ifImage" -> WorkflowStep.decode("FI|${item.optString("template")}|${item.optDouble("threshold", Double.NaN)}")
                    "else" -> WorkflowStep.ElseBranch
                    "endIf" -> WorkflowStep.EndIf
                    else -> null
                } ?: return null
                steps.add(step)
            }
            steps.takeIf { WorkflowStep.validateStructure(it) }
        } catch (_: Exception) {
            null
        }
    }
}
