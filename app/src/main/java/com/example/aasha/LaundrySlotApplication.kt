package com.example.aasha

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.aasha.data.local.SessionManager
import com.example.aasha.data.repository.LaundryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LaundrySlotApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var laundryRepository: LaundryRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installUncaughtExceptionMonitor()
        createNotificationChannels()
        appScope.launch {
            try {
                laundryRepository.checkAndSeedMachines()
                laundryRepository.rescheduleAllReminders()
            } catch (e: Exception) {
                Log.w("LaundryApp", "Startup initialization warning: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun installUncaughtExceptionMonitor() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val rootCause = generateSequence(throwable) { it.cause }.lastOrNull()
            val causeName = rootCause?.javaClass?.simpleName ?: throwable.javaClass.simpleName
            Log.e(
                "LaundryApp",
                "Uncaught exception on thread=${thread.name} type=${throwable.javaClass.simpleName} rootCause=$causeName"
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val cycleChannel = NotificationChannel(
                "laundry_cycle_completed",
                "Laundry Cycle Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when washing/drying cycle is completed"
            }

            val slotChannel = NotificationChannel(
                "slot_reminders_channel",
                "Slot Starting Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders 15 minutes before reserved slot"
            }

            val bookingChannel = NotificationChannel(
                "booking_confirmations",
                "Booking Confirmations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Confirmations when a laundry slot is reserved"
            }

            notificationManager.createNotificationChannel(cycleChannel)
            notificationManager.createNotificationChannel(slotChannel)
            notificationManager.createNotificationChannel(bookingChannel)
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(if (isDebuggable) Log.INFO else Log.ERROR)
                .build()
        }
}
