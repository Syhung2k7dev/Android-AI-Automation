package dev.syhung.cocassistant

import android.content.Context

sealed class ActionDispatchResult {
    data class Accepted(val skillId: String) : ActionDispatchResult()
    data class Rejected(val reason: String) : ActionDispatchResult()
}

/** Single gate between declarative Skill requests and Android Accessibility gestures. */
object ActionExecutor {

    /** Await one native action so the CASL interpreter cannot race ahead of the gesture. */
    suspend fun executeAwait(
        context: Context,
        registry: AgentSkillRegistry,
        request: SkillActionRequest,
        userApproved: Boolean = false
    ): ActionDispatchResult {
        if (!userApproved) return ActionDispatchResult.Rejected("Cần xác nhận của người dùng trước khi thực thi.")
        if (!request.validate()) return ActionDispatchResult.Rejected("Yêu cầu thao tác không hợp lệ.")
        val skill = registry.find(request.skillId, request.skillVersion)
            ?: return ActionDispatchResult.Rejected("Không tìm thấy đúng phiên bản Skill.")
        if (skill.status != SkillLifecycle.VERIFIED) return ActionDispatchResult.Rejected("Chỉ Skill đã xác minh mới được thực thi.")
        if (request.capability !in skill.capabilities) return ActionDispatchResult.Rejected("Skill không khai báo capability được yêu cầu.")
        val step = when (request.capability) {
            SkillActionCapability.TAP -> {
                // Dùng HumanizedGestureBuilder: thêm jitter ±5–7px vào toạ độ tap
                val (jx, jy) = HumanizedGestureBuilder.normalizedToPixelJittered(
                    request.normalizedX ?: 0, request.normalizedY ?: 0
                )
                AutomationStep.Tap(jx.toFloat(), jy.toFloat(), request.durationMs ?: 110L)
            }
            SkillActionCapability.SWIPE -> {
                // Swipe human-like qua Bézier (khai thác durationMs để tính tốc độ)
                val (jsx, jsy) = HumanizedGestureBuilder.normalizedToPixelJittered(
                    request.normalizedX ?: 0, request.normalizedY ?: 0, 3f, 3f
                )
                val (jex, jey) = HumanizedGestureBuilder.normalizedToPixelJittered(
                    request.endNormalizedX ?: 0, request.endNormalizedY ?: 0, 3f, 3f
                )
                AutomationStep.Swipe(jsx.toFloat(), jsy.toFloat(), jex.toFloat(), jey.toFloat(), request.durationMs!!)
            }
            SkillActionCapability.WAIT -> AutomationStep.Wait(request.durationMs!!)
            else -> return ActionDispatchResult.Rejected("Capability này chưa có bộ thực thi.")
        }
        return if (CoCAccessibilityService.performSingleStep(step)) {
            ActionDispatchResult.Accepted(skill.id)
        } else {
            ActionDispatchResult.Rejected("Accessibility Runtime chưa sẵn sàng hoặc từ chối thao tác.")
        }
    }

    fun execute(
        context: Context,
        registry: AgentSkillRegistry,
        request: SkillActionRequest,
        userApproved: Boolean = false
    ): ActionDispatchResult {
        if (!userApproved) return ActionDispatchResult.Rejected("Cần xác nhận của người dùng trước khi thực thi.")
        if (!request.validate()) return ActionDispatchResult.Rejected("Yêu cầu thao tác không hợp lệ.")
        val skill = registry.find(request.skillId, request.skillVersion)
            ?: return ActionDispatchResult.Rejected("Không tìm thấy Skill.")
        if (skill.status != SkillLifecycle.VERIFIED) {
            return ActionDispatchResult.Rejected("Chỉ Skill đã xác minh mới được thực thi.")
        }
        if (request.capability !in skill.capabilities) {
            return ActionDispatchResult.Rejected("Skill không khai báo capability được yêu cầu.")
        }
        if (!CoCAccessibilityService.isConnected()) {
            return ActionDispatchResult.Rejected("Accessibility Service chưa được kết nối.")
        }

        val step = when (request.capability) {
            SkillActionCapability.TAP -> {
                val (jx, jy) = HumanizedGestureBuilder.normalizedToPixelJittered(
                    request.normalizedX ?: 0, request.normalizedY ?: 0
                )
                AutomationStep.Tap(x = jx.toFloat(), y = jy.toFloat(), durationMs = request.durationMs ?: 110L)
            }
            SkillActionCapability.SWIPE -> {
                val (jsx, jsy) = HumanizedGestureBuilder.normalizedToPixelJittered(
                    request.normalizedX ?: 0, request.normalizedY ?: 0, 3f, 3f
                )
                val (jex, jey) = HumanizedGestureBuilder.normalizedToPixelJittered(
                    request.endNormalizedX ?: 0, request.endNormalizedY ?: 0, 3f, 3f
                )
                AutomationStep.Swipe(
                    startX = jsx.toFloat(), startY = jsy.toFloat(),
                    endX = jex.toFloat(), endY = jey.toFloat(),
                    durationMs = request.durationMs!!
                )
            }
            SkillActionCapability.WAIT -> AutomationStep.Wait(request.durationMs!!)
            else -> return ActionDispatchResult.Rejected("Capability này chưa có bộ thực thi.")
        }
        return if (CoCAccessibilityService.startWorkflow(context, listOf(step))) {
            ActionDispatchResult.Accepted(skill.id)
        } else {
            ActionDispatchResult.Rejected("Accessibility Runtime từ chối workflow.")
        }
    }
}
