package com.example.aasha.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aasha.domain.model.MachineIssueReport
import com.example.aasha.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MachineIssueDao {
    @Query("SELECT * FROM machine_issue_reports ORDER BY reportedAt DESC")
    fun getAllIssues(): Flow<List<MachineIssueReport>>

    @Query("SELECT * FROM machine_issue_reports WHERE machineId = :machineId ORDER BY reportedAt DESC")
    fun getIssuesForMachine(machineId: String): Flow<List<MachineIssueReport>>

    @Query("SELECT * FROM machine_issue_reports WHERE machineId = :machineId AND isResolved = 0 ORDER BY reportedAt DESC")
    suspend fun getUnresolvedIssuesForMachine(machineId: String): List<MachineIssueReport>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: MachineIssueReport)

    @Update
    suspend fun updateIssue(issue: MachineIssueReport)

    @Query("SELECT * FROM machine_issue_reports WHERE syncStatus = :syncStatus")
    suspend fun getIssuesBySyncStatus(syncStatus: SyncStatus): List<MachineIssueReport>

    @Query("SELECT COUNT(*) FROM machine_issue_reports WHERE isResolved = 0")
    fun getActiveIssuesCount(): Flow<Int>
}
