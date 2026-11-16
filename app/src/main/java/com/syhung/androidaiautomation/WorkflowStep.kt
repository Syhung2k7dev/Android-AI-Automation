package com.syhung.androidaiautomation

sealed class WorkflowStep {
    data class Tap(val x: Float, val y: Float, val durationMs: Long) : WorkflowStep()
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long
    ) : WorkflowStep()
    data class Wait(val durationMs: Long) : WorkflowStep()

    fun displayText(): String = when (this) {
        is Tap -> "Tap (${x.toInt()}, ${y.toInt()})"
        is Swipe -> "Swipe (${startX.toInt()}, ${startY.toInt()}) to (${endX.toInt()}, ${endY.toInt()})"
        is Wait -> "Wait ${durationMs} ms"
    }

    fun encode(): String = when (this) {
        is Tap -> "T|$x|$y|$durationMs"
        is Swipe -> "S|$startX|$startY|$endX|$endY|$durationMs"
        is Wait -> "W|$durationMs"
    }

    companion object {
        fun decode(value: String): WorkflowStep? {
            val parts = value.split("|")
            return when (parts.firstOrNull()) {
                "T" -> if (parts.size == 4) {
                    val x = parts[1].toFloatOrNull()
                    val y = parts[2].toFloatOrNull()
                    val duration = parts[3].toLongOrNull()
                    if (x != null && y != null && duration != null) Tap(x, y, duration) else null
                } else null
                "S" -> if (parts.size == 6) {
                    val x1 = parts[1].toFloatOrNull()
                    val y1 = parts[2].toFloatOrNull()
                    val x2 = parts[3].toFloatOrNull()
                    val y2 = parts[4].toFloatOrNull()
                    val duration = parts[5].toLongOrNull()
                    if (x1 != null && y1 != null && x2 != null && y2 != null && duration != null) {
                        Swipe(x1, y1, x2, y2, duration)
                    } else null
                } else null
                "W" -> if (parts.size == 2) parts[1].toLongOrNull()?.let { Wait(it) } else null
                else -> null
            }
        }
    }
}
