package com.syhung.androidaiautomation.agent.data

import java.util.UUID

data class CadssRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val revision: Long = 1,
    val status: String = "active",
    val ownerRef: String? = null,
    val sourceRefs: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val payload: String,
    val extensions: String = "{}"
)

data class CadssAsset(
    val id: String,
    val parentImageId: String?,
    val relativePath: String,
    val mimeType: String,
    val byteLength: Long,
    val sha256: String,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val purpose: String
)

object CadssRecordTypes {
    const val ACCOUNT = "account"
    const val ACCOUNT_SNAPSHOT = "account_snapshot"
    const val RESOURCE_SNAPSHOT = "resource_snapshot"
    const val LAYOUT = "layout"
    const val LAYOUT_OBJECT = "layout_object"
    const val ARMY = "army"
    const val GAME_DEFINITION = "game_definition"
    const val CALCULATION = "calculation"
    const val TASK = "task"
    const val IMAGE = "image"
    const val CROP = "crop"
    const val SCENE_MAP = "scene_map"
    const val VISION_RECORD = "vision_record"
    const val SKILL = "skill"
    const val SKILL_REVISION = "skill_revision"
    const val RESEARCH = "research"
    const val MEMORY = "memory"
}
