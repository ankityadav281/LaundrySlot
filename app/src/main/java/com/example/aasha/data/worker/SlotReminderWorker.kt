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
import com.example.aasha.data.local.SlotBookingDao
import com.example.aasha.domain.model.BookingStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SlotReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bookingDao: SlotBookingDao,
    private val sessionManager: SessionManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!sessionManager.notificationEnabled.first()) {
            return Result.success()
        }

        val machineName = inputData.getString("machineName") ?: "Washing Machine"
        val timeSlot = inputData.getString("timeSlot") ?: "Upcoming slot"
        val bookingId = inputData.getString("bookingId") ?: ""

        if (bookingId.isNotBlank()) {
            val booking = bookingDao.getBookingById(bookingId)
            val now = System.currentTimeMillis()
            if (booking == null ||
                booking.status != BookingStatus.UPCOMING ||
                booking.endTimeMillis <= now
            ) {
                return Result.success()
            }
        }

        showSlotReminderNotification(machineName, timeSlot, bookingId)
        return Result.success()
    }

    private fun showSlotReminderNotification(machineName: String, timeSlot: String, bookingId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = "slot_reminders_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Slot Starting Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders 15 minutes before reserved laundry slot"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ Laundry Slot Starting Soon!")
            .setContentText("Your slot for $machineName starts in 15 minutes ($timeSlot).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(bookingId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked; fail silently
        }
    }
}
