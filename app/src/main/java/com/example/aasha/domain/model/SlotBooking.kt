package com.example.aasha.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class BookingStatus {
    UPCOMING,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

enum class CycleType(val displayName: String, val durationMinutes: Int, val icon: String) {
    QUICK_WASH("Quick Wash (30m)", 30, "⚡"),
    STANDARD_WASH("Standard Wash (45m)", 45, "👕"),
    HEAVY_BEDDING("Heavy / Bedding (60m)", 60, "🛏️"),
    TURBO_DRY("Turbo Dry (40m)", 40, "♨️")
}

@Entity(
    tableName = "slot_bookings",
    indices = [
        Index(value = ["machineId", "date"]),
        Index(value = ["hostellerId", "status"]),
        Index(value = ["syncStatus"])
    ]
)
data class SlotBooking(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val machineId: String = "",
    val machineName: String = "",
    val machineType: MachineType = MachineType.FRONT_LOAD,
    val hostellerId: String = "",
    val hostellerName: String = "",
    val roomNumber: String = "",
    val hostelBlock: String = "",
    val date: Long = 0L,
    val timeSlot: String = "",
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val cycleType: String = "Standard Wash (45m)",
    val status: BookingStatus = BookingStatus.UPCOMING,
    val bookingCode: String = "",
    val reminderSet: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
