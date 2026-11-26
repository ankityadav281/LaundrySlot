package com.example.aasha.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MachineStatus {
    AVAILABLE,
    IN_USE,
    RESERVED,
    OUT_OF_ORDER
}

enum class MachineType {
    FRONT_LOAD,
    TOP_LOAD,
    DRYER,
    COMBO
}

@Entity(tableName = "washing_machines")
data class WashingMachine(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    val blockName: String = "Block A",
    val floor: Int = 1,
    val type: MachineType = MachineType.FRONT_LOAD,
    val status: MachineStatus = MachineStatus.AVAILABLE,
    val capacityKg: Double = 7.5,
    val currentCycleName: String? = null,
    val cycleEstimatedEndTime: Long? = null,
    val currentUserRoomOrName: String? = null,
    val lastMaintenanceDate: Long = System.currentTimeMillis()
)

fun WashingMachine.supportedCycles(): List<CycleType> = when (type) {
    MachineType.DRYER -> listOf(CycleType.TURBO_DRY)
    MachineType.COMBO -> CycleType.entries
    MachineType.FRONT_LOAD, MachineType.TOP_LOAD -> listOf(
        CycleType.QUICK_WASH,
        CycleType.STANDARD_WASH,
        CycleType.HEAVY_BEDDING
    )
}

fun WashingMachine.supportsCycle(cycleType: CycleType): Boolean =
    cycleType in supportedCycles()

fun WashingMachine.typeDisplayName(): String = when (type) {
    MachineType.FRONT_LOAD -> "Front Load Washer"
    MachineType.TOP_LOAD -> "Top Load Washer"
    MachineType.DRYER -> "Dryer"
    MachineType.COMBO -> "Washer-Dryer Combo"
}

fun WashingMachine.categoryLabel(): String =
    if (type == MachineType.DRYER) "Dryer" else "Washer"
