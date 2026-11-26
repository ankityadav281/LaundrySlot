package com.example.aasha.domain.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CycleCountdown(
    val remainingMillis: Long,
    val remainingMinutes: Int,
    val progress: Float,
    val isExpired: Boolean
)

object SlotTimeHelper {

    val standardHourlySlots: List<String> = (6..22).map { formatHourlySlot(it) }

    fun getStartOfDay(
        timestamp: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun isPastDate(
        dateStartOfDay: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val todayStart = getStartOfDay(nowMillis, timeZone)
        return getStartOfDay(dateStartOfDay, timeZone) < todayStart
    }

    fun parseStartHour(timeSlot: String): Int? {
        val startPart = timeSlot.substringBefore(" - ").trim()
        val parts = startPart.split(" ")
        if (parts.size != 2) return null
        val hourMin = parts[0].split(":")
        val hour12 = hourMin.firstOrNull()?.toIntOrNull() ?: return null
        if (hour12 !in 1..12) return null
        val period = parts[1].uppercase(Locale.ROOT)
        return when (period) {
            "AM" -> if (hour12 == 12) 0 else hour12
            "PM" -> if (hour12 == 12) 12 else hour12 + 12
            else -> null
        }
    }

    fun computeSlotWindow(
        dateStartOfDay: Long,
        timeSlot: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Long, Long>? {
        val startHour = parseStartHour(timeSlot) ?: return null
        val startCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = getStartOfDay(dateStartOfDay, timeZone)
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }

    /**
     * Returns true if the booking date is in the past OR the slot has already started
     * (`startTimeMillis <= nowMillis`).
     */
    fun isSlotPastOrStarted(
        dateStartOfDay: Long,
        timeSlot: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (isPastDate(dateStartOfDay, nowMillis, timeZone)) return true
        val (startMillis, _) = computeSlotWindow(dateStartOfDay, timeSlot, timeZone) ?: return true
        return startMillis <= nowMillis
    }

    fun hasOverlappingBooking(
        existingBookings: List<SlotBooking>,
        machineId: String,
        startMillis: Long,
        endMillis: Long
    ): Boolean {
        return existingBookings.any { existing ->
            existing.machineId == machineId &&
                existing.status != BookingStatus.CANCELLED &&
                existing.startTimeMillis < endMillis &&
                existing.endTimeMillis > startMillis
        }
    }

    fun buildSlotLockId(machineId: String, dateStartOfDay: Long, timeSlot: String): String {
        val sanitizedSlot = timeSlot.replace(":", "").replace(" ", "").replace("-", "_")
        return "${machineId}_${dateStartOfDay}_$sanitizedSlot"
    }

    fun calculateCycleCountdown(
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): CycleCountdown {
        val remainingMillis = (endMillis - nowMillis).coerceAtLeast(0L)
        val totalDuration = (endMillis - startMillis).coerceAtLeast(1L)
        val isExpired = nowMillis >= endMillis
        val remainingMinutes = if (remainingMillis == 0L) {
            0
        } else {
            ((remainingMillis + 59_999L) / 60_000L).toInt()
        }
        val progress = if (isExpired) {
            1f
        } else {
            (1f - (remainingMillis.toFloat() / totalDuration.toFloat())).coerceIn(0.05f, 0.99f)
        }
        return CycleCountdown(
            remainingMillis = remainingMillis,
            remainingMinutes = remainingMinutes,
            progress = progress,
            isExpired = isExpired
        )
    }

    fun formatHourlySlot(startHour: Int): String {
        fun formatHour(h: Int): String {
            val normalized = h % 24
            val period = if (normalized < 12) "AM" else "PM"
            val hour12 = when {
                normalized == 0 -> 12
                normalized > 12 -> normalized - 12
                else -> normalized
            }
            return String.format(Locale.ROOT, "%02d:00 %s", hour12, period)
        }
        return "${formatHour(startHour)} - ${formatHour(startHour + 1)}"
    }
}
