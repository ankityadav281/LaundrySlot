package com.example.aasha

import com.example.aasha.data.local.AppDatabase
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.CycleType
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.MachineType
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SyncStatus
import com.example.aasha.domain.model.WashingMachine
import com.example.aasha.domain.model.supportsCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingAndCycleStateTest {

    @Test
    fun `seed machines status counts sum consistently`() {
        val seeds = AppDatabase.createSeedMachines()
        val available = seeds.count { it.status == MachineStatus.AVAILABLE }
        val inUse = seeds.count { it.status == MachineStatus.IN_USE }
        val outOfOrder = seeds.count { it.status == MachineStatus.OUT_OF_ORDER }
        val reserved = seeds.count { it.status == MachineStatus.RESERVED }

        assertEquals(seeds.size, available + inUse + outOfOrder + reserved)
    }

    @Test
    fun `seed dryer cycle name matches Turbo Dry enum display name`() {
        val seeds = AppDatabase.createSeedMachines()
        val activeDryer = seeds.first { it.type == MachineType.DRYER && it.status == MachineStatus.IN_USE }
        assertEquals(CycleType.TURBO_DRY.displayName, activeDryer.currentCycleName)
    }

    @Test
    fun `machine rejects incompatible cycle types`() {
        val dryer = WashingMachine(id = "DRY-1", type = MachineType.DRYER, status = MachineStatus.AVAILABLE)
        val washer = WashingMachine(id = "WM-1", type = MachineType.TOP_LOAD, status = MachineStatus.AVAILABLE)

        assertFalse(dryer.supportsCycle(CycleType.QUICK_WASH))
        assertTrue(dryer.supportsCycle(CycleType.TURBO_DRY))
        assertFalse(washer.supportsCycle(CycleType.TURBO_DRY))
        assertTrue(washer.supportsCycle(CycleType.STANDARD_WASH))
    }

    @Test
    fun `expired upcoming bookings are categorized into history not upcoming`() {
        val now = 2_000_000L
        val active = SlotBooking(
            id = "a1",
            status = BookingStatus.ACTIVE,
            startTimeMillis = now - 60_000L,
            endTimeMillis = now + 60_000L
        )
        val futureUpcoming = SlotBooking(
            id = "u1",
            status = BookingStatus.UPCOMING,
            startTimeMillis = now + 120_000L,
            endTimeMillis = now + 3_720_000L,
            syncStatus = SyncStatus.PENDING
        )
        val expiredUpcoming = SlotBooking(
            id = "e1",
            status = BookingStatus.UPCOMING,
            startTimeMillis = now - 4_000_000L,
            endTimeMillis = now - 400_000L
        )
        val cancelled = SlotBooking(
            id = "c1",
            status = BookingStatus.CANCELLED,
            startTimeMillis = now - 5_000_000L,
            endTimeMillis = now - 1_400_000L
        )
        val all = listOf(active, futureUpcoming, expiredUpcoming, cancelled)

        val categorizedActive = all.filter { it.status == BookingStatus.ACTIVE }
        val categorizedUpcoming = all.filter { it.status == BookingStatus.UPCOMING && it.endTimeMillis > now }
        val categorizedHistory = all.filter {
            it.status == BookingStatus.COMPLETED ||
                it.status == BookingStatus.CANCELLED ||
                (it.status == BookingStatus.UPCOMING && it.endTimeMillis <= now)
        }

        assertEquals(listOf("a1"), categorizedActive.map { it.id })
        assertEquals(listOf("u1"), categorizedUpcoming.map { it.id })
        assertEquals(setOf("e1", "c1"), categorizedHistory.map { it.id }.toSet())
        assertEquals(SyncStatus.PENDING, categorizedUpcoming.first().syncStatus)
    }
}
