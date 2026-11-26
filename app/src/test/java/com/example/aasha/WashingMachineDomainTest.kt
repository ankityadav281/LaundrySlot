package com.example.aasha

import com.example.aasha.data.local.AppDatabase
import com.example.aasha.domain.model.CycleType
import com.example.aasha.domain.model.MachineType
import com.example.aasha.domain.model.WashingMachine
import com.example.aasha.domain.model.categoryLabel
import com.example.aasha.domain.model.supportedCycles
import com.example.aasha.domain.model.supportsCycle
import com.example.aasha.domain.model.typeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WashingMachineDomainTest {

    @Test
    fun `washer supports wash cycles and excludes turbo dry`() {
        val washer = WashingMachine(id = "WM-1", name = "Washer 1", type = MachineType.FRONT_LOAD)
        assertTrue(washer.supportsCycle(CycleType.QUICK_WASH))
        assertTrue(washer.supportsCycle(CycleType.STANDARD_WASH))
        assertTrue(washer.supportsCycle(CycleType.HEAVY_BEDDING))
        assertFalse(washer.supportsCycle(CycleType.TURBO_DRY))
        assertEquals("Washer", washer.categoryLabel())
        assertEquals("Front Load Washer", washer.typeDisplayName())
    }

    @Test
    fun `dryer supports turbo dry only`() {
        val dryer = WashingMachine(id = "DRY-1", name = "Dryer 1", type = MachineType.DRYER)
        assertEquals(listOf(CycleType.TURBO_DRY), dryer.supportedCycles())
        assertTrue(dryer.supportsCycle(CycleType.TURBO_DRY))
        assertFalse(dryer.supportsCycle(CycleType.STANDARD_WASH))
        assertEquals("Dryer", dryer.categoryLabel())
    }

    @Test
    fun `combo machine supports all cycle types`() {
        val combo = WashingMachine(id = "CMB-1", name = "Combo 1", type = MachineType.COMBO)
        assertEquals(CycleType.entries, combo.supportedCycles())
        CycleType.entries.forEach { cycle ->
            assertTrue(combo.supportsCycle(cycle))
        }
    }

    @Test
    fun `seed machines contain valid unique ids across blocks`() {
        val seeds = AppDatabase.createSeedMachines()
        assertTrue(seeds.isNotEmpty())
        assertEquals(seeds.size, seeds.map { it.id }.distinct().size)
        assertTrue(seeds.any { it.blockName == "Block A" })
        assertTrue(seeds.any { it.blockName == "Block B" })
    }
}
