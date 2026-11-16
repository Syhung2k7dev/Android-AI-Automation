package com.syhung.androidaiautomation.agent.domain

import android.content.Context
import com.syhung.androidaiautomation.agent.data.CadssRecord
import com.syhung.androidaiautomation.agent.data.CadssRecordTypes
import com.syhung.androidaiautomation.agent.data.CadssRepository
import org.json.JSONObject

/**
 * Typed domain facade over CADSS. Payload schemas are versioned JSON contracts;
 * game facts remain source/version tagged and are never invented by this layer.
 */
class CocDomainRepository(context: Context) {
    private val records = CadssRepository(context.applicationContext)

    fun saveAccount(id: String? = null, displayName: String, playerTag: String? = null, townHall: Int? = null): String {
        require(displayName.isNotBlank())
        require(townHall == null || townHall in 1..20)
        val record = CadssRecord(
            id = id ?: java.util.UUID.randomUUID().toString(),
            type = CadssRecordTypes.ACCOUNT,
            payload = JSONObject().put("display_name", displayName)
                .put("player_tag", playerTag).put("town_hall", townHall)
                .put("credentials_stored", false).toString()
        )
        records.put(record)
        return record.id
    }

    fun saveLayout(accountId: String?, name: String, townHall: Int, baseType: String, sourceImageId: String?): String {
        require(name.isNotBlank() && townHall in 1..20)
        require(baseType in setOf("home", "war", "builder"))
        val id = java.util.UUID.randomUUID().toString()
        records.put(CadssRecord(
            id = id, type = CadssRecordTypes.LAYOUT, ownerRef = accountId,
            sourceRefs = listOfNotNull(sourceImageId),
            payload = JSONObject().put("name", name).put("town_hall", townHall)
                .put("base_type", baseType).put("source_image_id", sourceImageId)
                .put("verification", "unverified").toString()
        ))
        return id
    }

    fun saveArmy(accountId: String?, name: String, townHall: Int, capacity: Int, composition: JSONObject): String {
        require(name.isNotBlank() && townHall in 1..20 && capacity >= 0)
        val id = java.util.UUID.randomUUID().toString()
        records.put(CadssRecord(
            id = id, type = CadssRecordTypes.ARMY, ownerRef = accountId,
            payload = JSONObject().put("name", name).put("town_hall", townHall)
                .put("capacity", capacity).put("composition", composition)
                .put("verification", "unverified").toString()
        ))
        return id
    }

    fun saveGameDefinition(kind: String, name: String, level: Int, values: JSONObject, sourceRef: String?, gameDataVersion: String?) : String {
        require(kind.isNotBlank() && name.isNotBlank() && level >= 0)
        val id = java.util.UUID.randomUUID().toString()
        records.put(CadssRecord(
            id = id, type = CadssRecordTypes.GAME_DEFINITION,
            sourceRefs = listOfNotNull(sourceRef),
            payload = JSONObject().put("kind", kind).put("name", name).put("level", level)
                .put("values", values).put("game_data_version", gameDataVersion)
                .put("verification", if (sourceRef != null && !gameDataVersion.isNullOrBlank()) "source_verified" else "unverified")
                .toString()
        ))
        return id
    }

    fun listAccounts() = records.list(CadssRecordTypes.ACCOUNT)
    fun listLayouts(accountId: String? = null) = records.list(CadssRecordTypes.LAYOUT).filter { accountId == null || it.ownerRef == accountId }
    fun listArmies(accountId: String? = null) = records.list(CadssRecordTypes.ARMY).filter { accountId == null || it.ownerRef == accountId }
    fun close() = records.close()
}
