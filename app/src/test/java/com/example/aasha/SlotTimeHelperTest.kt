package com.example.aasha

import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SlotTimeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SlotTimeHelperTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun octUtcMillis(day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(utc).apply {
            set(2026, Calendar.OCTOBER, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `standardHourlySlots spans 06 AM to 11 PM`() {
        val slots = SlotTimeHelper.standardHourlySlots
        assertEquals(17, slots.size)
        assertEquals("06:00 AM - 07:00 AM", slots.first())
        assertEquals("10:00 PM - 11:00 PM", slots.last())
    }

    @Test
    fun `isPastDate returns true only for dates strictly before today`() {
        val now = octUtcMillis(15, 14, 30)
        val yesterday = octUtcMillis(14, 23, 59)
        val todayMorning = octUtcMillis(15, 0, 0)
        val tomorrow = octUtcMillis(16, 8, 0)

        assertTrue(SlotTimeHelper.isPastDate(yesterday, now, utc))
        assertFalse(SlotTimeHelper.isPastDate(todayMorning, now, utc))
        assertFalse(SlotTimeHelper.isPastDate(tomorrow, now, utc))
    }

    @Test
    fun `isSlotPastOrStarted marks current hour and earlier slots as started`() {
        val now315Pm = octUtcMillis(15, 15, 15)
        val todayStart = SlotTimeHelper.getStartOfDay(now315Pm, utc)

        assertTrue(SlotTimeHelper.isSlotPastOrStarted(todayStart, "02:00 PM - 03:00 PM", now315Pm, utc))
        assertTrue(SlotTimeHelper.isSlotPastOrStarted(todayStart, "03:00 PM - 04:00 PM", now315Pm, utc))
        assertFalse(SlotTimeHelper.isSlotPastOrStarted(todayStart, "04:00 PM - 05:00 PM", now315Pm, utc))
    }

    @Test
    fun `parseStartHour handles noon midnight and invalid formats`() {
        assertEquals(6, SlotTimeHelper.parseStartHour("06:00 AM - 07:00 AM"))
        assertEquals(12, SlotTimeHelper.parseStartHour("12:00 PM - 01:00 PM"))
        assertEquals(15, SlotTimeHelper.parseStartHour("03:00 PM - 04:00 PM"))
        assertEquals(0, SlotTimeHelper.parseStartHour("12:00 AM - 01:00 AM"))
        assertNull(SlotTimeHelper.parseStartHour("Now (30m)"))
    }

    @Test
    fun `computeSlotWindow produces exact 1 hour window`() {
        val dayStart = octUtcMillis(15, 0, 0)
        val window = SlotTimeHelper.computeSlotWindow(dayStart, "04:00 PM - 05:00 PM", utc)
        assertNotNull(window)
        assertEquals(octUtcMillis(15, 16, 0), window!!.first)
        assertEquals(octUtcMillis(15, 17, 0), window.second)
    }

    @Test
    fun `hasOverlappingBooking ignores cancelled bookings and other machines`() {
        val dayStart = octUtcMillis(15, 0, 0)
        val (s1, e1) = SlotTimeHelper.computeSlotWindow(dayStart, "04:00 PM - 05:00 PM", utc)!!
        val (s2, e2) = SlotTimeHelper.computeSlotWindow(dayStart, "05:00 PM - 06:00 PM", utc)!!

        val bookings = listOf(
            SlotBooking(
                id = "b1",
                machineId = "WM-A101",
                startTimeMillis = s1,
                endTimeMillis = e1,
                status = BookingStatus.CANCELLED
            ),
            SlotBooking(
                id = "b2",
                machineId = "WM-A102",
                startTimeMillis = s1,
                endTimeMillis = e1,
                status = BookingStatus.UPCOMING
            )
        )

        assertFalse(SlotTimeHelper.hasOverlappingBooking(bookings, "WM-A101", s1, e1))
        assertTrue(SlotTimeHelper.hasOverlappingBooking(bookings, "WM-A102", s1, e1))
        assertFalse(SlotTimeHelper.hasOverlappingBooking(bookings, "WM-A102", s2, e2))
    }

    @Test
    fun `buildSlotLockId is deterministic and unique per machine date slot`() {
        val day1 = octUtcMillis(15, 0, 0)
        val day2 = octUtcMillis(16, 0, 0)

        val lockA = SlotTimeHelper.buildSlotLockId("WM-A101", day1, "04:00 PM - 05:00 PM")
        val lockASame = SlotTimeHelper.buildSlotLockId("WM-A101", day1, "04:00 PM - 05:00 PM")
        val lockOtherMachine = SlotTimeHelper.buildSlotLockId("WM-A102", day1, "04:00 PM - 05:00 PM")
        val lockOtherDate = SlotTimeHelper.buildSlotLockId("WM-A101", day2, "04:00 PM - 05:00 PM")

        assertEquals(lockA, lockASame)
        assertNotEquals(lockA, lockOtherMachine)
        assertNotEquals(lockA, lockOtherDate)
    }

    @Test
    fun `calculateCycleCountdown computes persisted remaining time and clamps expiry`() {
        val start = 1_000_000L
        val end = start + 40 * 60_000L // 40 min cycle

        val mid = SlotTimeHelper.calculateCycleCountdown(start, end, start + 20 * 60_000L)
        assertFalse(mid.isExpired)
        assertEquals(20, mid.remainingMinutes)
        assertEquals(0.5f, mid.progress, 0.01f)

        val justUnderOneMin = SlotTimeHelper.calculateCycleCountdown(start, end, end - 10_000L)
        assertFalse(justUnderOneMin.isExpired)
        assertEquals(1, justUnderOneMin.remainingMinutes)

        val expired = SlotTimeHelper.calculateCycleCountdown(start, end, end + 5_000L)
        assertTrue(expired.isExpired)
        assertEquals(0L, expired.remainingMillis)
        assertEquals(0, expired.remainingMinutes)
        assertEquals(1f, expired.progress, 0.001f)
    }
}
