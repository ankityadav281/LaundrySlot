package com.example.aasha.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aasha.R
import com.example.aasha.data.local.SessionManager
import com.example.aasha.data.local.WashingMachineDao
import com.example.aasha.domain.model.MachineStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class LaundryCycleReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val machineDao: WashingMachineDao,
    private val sessionManager: SessionManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!sessionManager.notificationEnabled.first()) {
            return Result.success()
        }

        val machineName = inputData.getString("machineName") ?: "Washing Machine"
        val machineId = inputData.getString("machineId") ?: ""
        val roomNumber = inputData.getString("roomNumber") ?: ""

        if (machineId.isNotBlank()) {
            val machine = machineDao.getMachineById(machineId)
            if (machine == null || machine.status != MachineStatus.IN_USE) {
                return Result.success()
            }
        }

        showCycleCompletedNotification(machineName, machineId, roomNumber)
        return Result.success()
    }

    private fun showCycleCompletedNotification(machineName: String, machineId: String, roomNumber: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = "laundry_cycle_completed"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Laundry Cycle Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when washing or drying cycle is complete"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("👕 Laundry Cycle Completed!")
            .setContentText("Your wash cycle in $machineName ($machineId) is done. Please collect your clothes promptly.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your wash cycle in $machineName ($machineId) is complete! Please collect your laundry so others can use the machine.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(machineId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked; fail silently
        }
    }
}
