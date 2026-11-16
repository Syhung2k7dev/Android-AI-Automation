package com.syhung.androidaiautomation

sealed class WorkflowStep {
    data class Tap(val x: Float, val y: Float, val durationMs: Long) : WorkflowStep()
    data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long) : WorkflowStep()
    data class Wait(val durationMs: Long) : WorkflowStep()
    data class WaitForText(val text: String, val timeoutMs: Long, val pollIntervalMs: Long) : WorkflowStep()
    data class WaitForImage(val template: String, val timeoutMs: Long, val pollIntervalMs: Long, val threshold: Float) : WorkflowStep()
    data class IfText(val text: String) : WorkflowStep()
    data class IfImage(val template: String, val threshold: Float) : WorkflowStep()
    object ElseBranch : WorkflowStep()
    object EndIf : WorkflowStep()

    fun displayText(): String = when (this) {
        is Tap -> "Tap (${x.toInt()}, ${y.toInt()})"
        is Swipe -> "Swipe (${startX.toInt()}, ${startY.toInt()}) to (${endX.toInt()}, ${endY.toInt()})"
        is Wait -> "Wait ${durationMs} ms"
        is WaitForText -> "Wait for text: '$text' (timeout ${timeoutMs} ms)"
        is WaitForImage -> "Wait for image: '$template' (threshold $threshold, timeout ${timeoutMs} ms)"
        is IfText -> "IF text appears: '$text'"
        is IfImage -> "IF image appears: '$template' (threshold $threshold)"
        ElseBranch -> "ELSE"
        EndIf -> "END IF"
    }

    fun encode(): String = when (this) {
        is Tap -> "T|$x|$y|$durationMs"
        is Swipe -> "S|$startX|$startY|$endX|$endY|$durationMs"
        is Wait -> "W|$durationMs"
        is WaitForText -> "C|$text|$timeoutMs|$pollIntervalMs"
        is WaitForImage -> "I|$template|$timeoutMs|$pollIntervalMs|$threshold"
        is IfText -> "FT|$text"
        is IfImage -> "FI|$template|$threshold"
        ElseBranch -> "ELSE"
        EndIf -> "ENDIF"
    }

    companion object {
        private fun validCoordinate(value: Float) = value.isFinite() && value in 0f..20_000f

        fun decode(value: String): WorkflowStep? {
            val parts = value.split("|")
            return when (parts.firstOrNull()) {
                "T" -> if (parts.size == 4) {
                    val x = parts[1].toFloatOrNull(); val y = parts[2].toFloatOrNull()
                    val duration = parts[3].toLongOrNull()
                    if (x != null && y != null && validCoordinate(x) && validCoordinate(y) && duration != null && duration in 50L..10_000L) Tap(x, y, duration) else null
                } else null
                "S" -> if (parts.size == 6) {
                    val x1 = parts[1].toFloatOrNull(); val y1 = parts[2].toFloatOrNull()
                    val x2 = parts[3].toFloatOrNull(); val y2 = parts[4].toFloatOrNull()
                    val duration = parts[5].toLongOrNull()
                    if (x1 != null && y1 != null && x2 != null && y2 != null && validCoordinate(x1) && validCoordinate(y1) && validCoordinate(x2) && validCoordinate(y2) && duration != null && duration in 50L..10_000L) Swipe(x1, y1, x2, y2, duration) else null
                } else null
                "W" -> if (parts.size == 2) parts[1].toLongOrNull()?.takeIf { it in 100L..60_000L }?.let { Wait(it) } else null
                "C" -> if (parts.size == 4) {
                    val text = parts[1].trim(); val timeout = parts[2].toLongOrNull(); val poll = parts[3].toLongOrNull()
                    if (text.isNotEmpty() && text.length <= 80 && !text.contains('\n') && timeout != null && timeout in 1_000L..120_000L && poll != null && poll in 1_000L..10_000L && poll <= timeout) WaitForText(text, timeout, poll) else null
                } else null
                "I" -> if (parts.size == 5) {
                    val template = parts[1]; val timeout = parts[2].toLongOrNull(); val poll = parts[3].toLongOrNull(); val threshold = parts[4].toFloatOrNull()
                    if (template.matches(Regex("[A-Za-z0-9_-]{1,40}")) && timeout != null && timeout in 1_000L..120_000L && poll != null && poll in 1_000L..10_000L && poll <= timeout && threshold != null && threshold.isFinite() && threshold in 0.50f..0.99f) WaitForImage(template, timeout, poll, threshold) else null
                } else null
                "FT" -> if (parts.size == 2 && parts[1].isNotBlank() && parts[1].length <= 80) IfText(parts[1]) else null
                "FI" -> if (parts.size == 3) {
                    val threshold = parts[2].toFloatOrNull()
                    if (parts[1].matches(Regex("[A-Za-z0-9_-]{1,40}")) && threshold != null && threshold.isFinite() && threshold in 0.50f..0.99f) IfImage(parts[1], threshold) else null
                } else null
                "ELSE" -> if (parts.size == 1) ElseBranch else null
                "ENDIF" -> if (parts.size == 1) EndIf else null
                else -> null
            }
        }

        fun validateStructure(steps: List<WorkflowStep>): Boolean {
            if (steps.isEmpty() || steps.size > 50) return false
            val stack = ArrayDeque<Boolean>()
            for (step in steps) when (step) {
                is IfText, is IfImage -> {
                    if (stack.size >= 10) return false
                    stack.addLast(false)
                }
                ElseBranch -> {
                    if (stack.isEmpty() || stack.removeLast()) return false
                    stack.addLast(true)
                }
                EndIf -> if (stack.isEmpty()) return false else stack.removeLast()
                else -> Unit
            }
            return stack.isEmpty()
        }
    }
}
