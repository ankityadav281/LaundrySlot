package com.example.aasha.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.WashingMachine
import kotlinx.coroutines.flow.Flow

@Dao
interface WashingMachineDao {
    @Query("SELECT * FROM washing_machines ORDER BY blockName, floor, id")
    fun getAllMachines(): Flow<List<WashingMachine>>

    @Query("SELECT * FROM washing_machines WHERE blockName = :blockName ORDER BY floor, id")
    fun getMachinesByBlock(blockName: String): Flow<List<WashingMachine>>

    @Query("SELECT * FROM washing_machines WHERE blockName = :blockName AND floor = :floor ORDER BY id")
    fun getMachinesByBlockAndFloor(blockName: String, floor: Int): Flow<List<WashingMachine>>

    @Query("SELECT * FROM washing_machines WHERE id = :id")
    suspend fun getMachineById(id: String): WashingMachine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachine(machine: WashingMachine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachines(machines: List<WashingMachine>)

    @Update
    suspend fun updateMachine(machine: WashingMachine)

    @Query("UPDATE washing_machines SET status = :status, currentCycleName = :cycleName, cycleEstimatedEndTime = :endTime, currentUserRoomOrName = :user WHERE id = :id")
    suspend fun updateMachineStatus(
        id: String,
        status: MachineStatus,
        cycleName: String?,
        endTime: Long?,
        user: String?
    )

    @Query(
        "UPDATE washing_machines " +
            "SET status = 'IN_USE', currentCycleName = :cycleName, cycleEstimatedEndTime = :endTime, currentUserRoomOrName = :user " +
            "WHERE id = :id AND status = 'AVAILABLE'"
    )
    suspend fun atomicStartMachine(
        id: String,
        cycleName: String,
        endTime: Long,
        user: String
    ): Int

    @Query(
        "UPDATE washing_machines " +
            "SET status = 'AVAILABLE', currentCycleName = NULL, cycleEstimatedEndTime = NULL, currentUserRoomOrName = NULL " +
            "WHERE id = :id AND status = 'IN_USE'"
    )
    suspend fun atomicCompleteMachine(id: String): Int

    @Query(
        "UPDATE washing_machines " +
            "SET status = 'OUT_OF_ORDER', currentCycleName = NULL, cycleEstimatedEndTime = NULL, currentUserRoomOrName = :reason " +
            "WHERE id = :id AND status != 'OUT_OF_ORDER'"
    )
    suspend fun atomicFlagMaintenance(id: String, reason: String): Int

    @Query("SELECT COUNT(*) FROM washing_machines WHERE status = 'AVAILABLE'")
    fun getAvailableCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM washing_machines WHERE status = 'IN_USE'")
    fun getInUseCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM washing_machines WHERE status = 'OUT_OF_ORDER'")
    fun getOutOfOrderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM washing_machines")
    fun getTotalMachineCount(): Flow<Int>
}
