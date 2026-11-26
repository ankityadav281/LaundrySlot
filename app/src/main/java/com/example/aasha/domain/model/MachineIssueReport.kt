package com.example.aasha.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "machine_issue_reports")
data class MachineIssueReport(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val machineId: String = "",
    val machineName: String = "",
    val reportedBy: String = "",
    val roomNumber: String = "",
    val issueCategory: String = "General",
    val description: String = "",
    val reportedAt: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
