package dev.syhung.cocassistant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class WorldStateManager(private val context: Context, private val scope: CoroutineScope) {
    private val state = ConcurrentHashMap<String, JSONObject>()

    fun getVillageResources(mode: String): JSONObject {
        // Mock retrieval: In real implementation, this would query local DB or OCR cache
        return state.getOrDefault(\
resources_\\, JSONObject().put(\gold\, 0).put(\elixir\, 0))
    }

    fun getActiveBuildingTimers(mode: String): JSONObject {
        return state.getOrDefault(\timers_\\, JSONObject())
    }

    fun getGlobalContext(key: String, default: String): String {
        return state[\global_context\]?.optString(key, default) ?: default
    }

    fun getRecentExecutionLogs(count: Int): List<JSONObject> {
        val logs = state[\execution_logs\]?.optJSONArray(\logs\)
        val result = mutableListOf<JSONObject>()
        if (logs != null) {
            for (i in 0 until minOf(count, logs.length())) {
                result.add(logs.getJSONObject(i))
            }
        }
        return result
    }

    fun updateState(key: String, value: JSONObject) {
        state[key] = value
    }
}
