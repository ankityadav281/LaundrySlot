package com.example.aasha.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aasha.domain.model.MachineIssueReport
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.MachineType
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.WashingMachine

@Database(
    entities = [WashingMachine::class, SlotBooking::class, MachineIssueReport::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun washingMachineDao(): WashingMachineDao
    abstract fun slotBookingDao(): SlotBookingDao
    abstract fun machineIssueDao(): MachineIssueDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_slot_bookings_machineId_date ON slot_bookings(machineId, date)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_slot_bookings_hostellerId_status ON slot_bookings(hostellerId, status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_slot_bookings_syncStatus ON slot_bookings(syncStatus)"
                )
            }
        }

        fun createSeedMachines(): List<WashingMachine> {
            val now = System.currentTimeMillis()
            return listOf(
                WashingMachine(
                    id = "WM-A101",
                    name = "Washer A1 (Front Load)",
                    blockName = "Block A",
                    floor = 1,
                    type = MachineType.FRONT_LOAD,
                    status = MachineStatus.AVAILABLE,
                    capacityKg = 8.0,
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "WM-A102",
                    name = "Washer A2 (Turbo Spin)",
                    blockName = "Block A",
                    floor = 1,
                    type = MachineType.TOP_LOAD,
                    status = MachineStatus.IN_USE,
                    capacityKg = 7.5,
                    currentCycleName = "Standard Wash (45m)",
                    cycleEstimatedEndTime = now + (22 * 60 * 1000L),
                    currentUserRoomOrName = "Room A-104",
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "DRY-A101",
                    name = "Turbo Dryer A1",
                    blockName = "Block A",
                    floor = 1,
                    type = MachineType.DRYER,
                    status = MachineStatus.AVAILABLE,
                    capacityKg = 9.0,
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "WM-A201",
                    name = "Washer A3 (Front Load)",
                    blockName = "Block A",
                    floor = 2,
                    type = MachineType.FRONT_LOAD,
                    status = MachineStatus.AVAILABLE,
                    capacityKg = 8.5,
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "WM-A202",
                    name = "Washer A4 (Eco Wash)",
                    blockName = "Block A",
                    floor = 2,
                    type = MachineType.TOP_LOAD,
                    status = MachineStatus.OUT_OF_ORDER,
                    capacityKg = 7.0,
                    currentUserRoomOrName = "Reported: Drain Blocked",
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "DRY-A201",
                    name = "Turbo Dryer A2",
                    blockName = "Block A",
                    floor = 2,
                    type = MachineType.DRYER,
                    status = MachineStatus.IN_USE,
                    capacityKg = 9.0,
                    currentCycleName = "Turbo Dry (40m)",
                    cycleEstimatedEndTime = now + (15 * 60 * 1000L),
                    currentUserRoomOrName = "Room A-212",
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "WM-B101",
                    name = "Washer B1 (Heavy Load)",
                    blockName = "Block B",
                    floor = 1,
                    type = MachineType.FRONT_LOAD,
                    status = MachineStatus.AVAILABLE,
                    capacityKg = 10.0,
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "WM-B102",
                    name = "Washer B2 (Quick Wash)",
                    blockName = "Block B",
                    floor = 1,
                    type = MachineType.TOP_LOAD,
                    status = MachineStatus.AVAILABLE,
                    capacityKg = 7.5,
                    lastMaintenanceDate = now
                ),
                WashingMachine(
                    id = "DRY-B101",
                    name = "Express Dryer B1",
                    blockName = "Block B",
                    floor = 1,
                    type = MachineType.DRYER,
                    status = MachineStatus.AVAILABLE,
                    capacityKg = 8.5,
                    lastMaintenanceDate = now
                )
            )
        }
    }
}
