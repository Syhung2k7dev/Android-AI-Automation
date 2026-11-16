package com.syhung.androidaiautomation.agent.domain

data class ResourceAmounts(val gold: Long = 0, val elixir: Long = 0, val darkElixir: Long = 0, val builderGold: Long = 0, val builderElixir: Long = 0) {
    operator fun minus(other: ResourceAmounts) = ResourceAmounts(
        gold = (gold - other.gold).coerceAtLeast(0),
        elixir = (elixir - other.elixir).coerceAtLeast(0),
        darkElixir = (darkElixir - other.darkElixir).coerceAtLeast(0),
        builderGold = (builderGold - other.builderGold).coerceAtLeast(0),
        builderElixir = (builderElixir - other.builderElixir).coerceAtLeast(0)
    )
}

data class UpgradeCost(val definitionId: String, val cost: ResourceAmounts, val durationSeconds: Long, val sourceVersion: String, val verified: Boolean)
data class ResourcePlan(val missing: ResourceAmounts, val affordable: Boolean, val warnings: List<String>)

object CocResourceCalculator {
    fun calculate(current: ResourceAmounts, upgrade: UpgradeCost): ResourcePlan {
        val missing = ResourceAmounts(
            gold = (upgrade.cost.gold - current.gold).coerceAtLeast(0),
            elixir = (upgrade.cost.elixir - current.elixir).coerceAtLeast(0),
            darkElixir = (upgrade.cost.darkElixir - current.darkElixir).coerceAtLeast(0),
            builderGold = (upgrade.cost.builderGold - current.builderGold).coerceAtLeast(0),
            builderElixir = (upgrade.cost.builderElixir - current.builderElixir).coerceAtLeast(0)
        )
        val warnings = buildList {
            if (!upgrade.verified) add("Upgrade cost is not verified")
            if (upgrade.sourceVersion.isBlank()) add("Game data version is missing")
            if (upgrade.durationSeconds < 0) add("Upgrade duration is invalid")
        }
        return ResourcePlan(missing, missing == ResourceAmounts(), warnings)
    }
}

data class PlannedUpgrade(val id: String, val durationSeconds: Long, val prerequisites: Set<String>, val priority: Int)
data class ScheduledUpgrade(val id: String, val startAfterSeconds: Long, val finishAfterSeconds: Long)

object BuildingPlanner {
    fun schedule(upgrades: List<PlannedUpgrade>, availableBuilders: Int): List<ScheduledUpgrade> {
        require(availableBuilders > 0)
        val byId = upgrades.associateBy { it.id }
        require(byId.size == upgrades.size) { "Duplicate upgrade IDs" }
        upgrades.forEach { require(it.durationSeconds >= 0 && it.prerequisites.all(byId::containsKey)) }
        val completed = mutableSetOf<String>()
        val active = mutableListOf<Pair<PlannedUpgrade, Long>>()
        val result = mutableListOf<ScheduledUpgrade>()
        var now = 0L
        while (completed.size < upgrades.size) {
            val finished = active.filter { it.second <= now }
            finished.forEach { completed += it.first.id }
            active.removeAll(finished.toSet())
            val ready = upgrades.filter { it.id !in completed && active.none { a -> a.first.id == it.id } && it.prerequisites.all(completed::contains) }
                .sortedByDescending { it.priority }
            while (active.size < availableBuilders && ready.isNotEmpty()) {
                val next = ready.firstOrNull { candidate -> active.none { it.first.id == candidate.id } } ?: break
                val end = now + next.durationSeconds
                active += next to end
                result += ScheduledUpgrade(next.id, now, end)
                if (next.durationSeconds == 0L) {
                    completed += next.id
                    active.removeAll { it.first.id == next.id }
                }
                // Remove the chosen item from this iteration's candidate list.
                // The next loop recomputes ready after the active set changes.
                if (active.size >= availableBuilders) break
                if (ready.size == 1) break
                // Mark selection locally by using the active set.
                if (ready.all { rdy -> active.any { it.first.id == rdy.id } }) break
                // Avoid repeatedly selecting the same candidate.
                break
            }
            if (active.isEmpty() && completed.size < upgrades.size) {
                throw IllegalArgumentException("Dependency cycle or unresolved prerequisites")
            }
            if (active.isNotEmpty()) now = active.minOf { it.second }
        }
        return result.sortedBy { it.startAfterSeconds }
    }
}
