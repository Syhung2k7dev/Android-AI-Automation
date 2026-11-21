package dev.syhung.cocassistant
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class WorldStateManager(private val context: Context, private val scope: CoroutineScope) {
    fun getVillageResources(mode: String): JSONObject = JSONObject()
    fun getActiveBuildingTimers(mode: String): JSONObject = JSONObject()
    fun getGlobalContext(key: String, default: String): String = default
    fun getRecentExecutionLogs(count: Int): List<JSONObject> = emptyList()
    
    fun updateState(newState: String) {}
}
