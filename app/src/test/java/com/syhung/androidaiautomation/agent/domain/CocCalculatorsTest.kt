package com.syhung.androidaiautomation.agent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CocCalculatorsTest {
    @Test
    fun resourceCalculatorReportsOnlyMissingAmounts() {
        val plan = CocResourceCalculator.calculate(
            ResourceAmounts(gold = 100, elixir = 500, darkElixir = 10),
            UpgradeCost("wall", ResourceAmounts(gold = 250, elixir = 300, darkElixir = 40), 60, "manual-v1", true)
        )
        assertEquals(150L, plan.missing.gold)
        assertEquals(0L, plan.missing.elixir)
        assertEquals(30L, plan.missing.darkElixir)
        assertTrue(!plan.affordable)
        assertTrue(plan.warnings.isEmpty())
    }

    @Test
    fun plannerUsesAllAvailableBuildersInParallel() {
        val result = BuildingPlanner.schedule(
            listOf(
                PlannedUpgrade("a", 100, emptySet(), 10),
                PlannedUpgrade("b", 200, emptySet(), 5),
                PlannedUpgrade("c", 50, setOf("a"), 20)
            ),
            availableBuilders = 2
        ).associateBy { it.id }
        assertEquals(0L, result.getValue("a").startAfterSeconds)
        assertEquals(0L, result.getValue("b").startAfterSeconds)
        assertEquals(100L, result.getValue("c").startAfterSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun plannerRejectsDependencyCycles() {
        BuildingPlanner.schedule(
            listOf(
                PlannedUpgrade("a", 10, setOf("b"), 1),
                PlannedUpgrade("b", 10, setOf("a"), 1)
            ),
            availableBuilders = 1
        )
    }
}
