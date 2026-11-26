package com.example.aasha.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.aasha.data.repository.LaundryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var laundryRepository: LaundryRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    laundryRepository.rescheduleAllReminders()
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule reminders on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
