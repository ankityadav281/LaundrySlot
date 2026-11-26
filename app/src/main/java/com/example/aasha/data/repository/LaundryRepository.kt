package com.example.aasha.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.aasha.R
import com.example.aasha.data.local.AppDatabase
import com.example.aasha.data.local.MachineIssueDao
import com.example.aasha.data.local.SessionManager
import com.example.aasha.data.local.SlotBookingDao
import com.example.aasha.data.local.WashingMachineDao
import com.example.aasha.data.sync.SyncWorker
import com.example.aasha.data.worker.LaundryCycleReminderWorker
import com.example.aasha.data.worker.SlotReminderWorker
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.CycleType
import com.example.aasha.domain.model.LaundryNotice
import com.example.aasha.domain.model.MachineIssueReport
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SlotTimeHelper
import com.example.aasha.domain.model.SyncStatus
import com.example.aasha.domain.model.WashingMachine
import com.example.aasha.domain.model.supportsCycle
import com.example.aasha.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class SlotAlreadyBookedException(message: String) : Exception(message)

class MachineUnavailableException(message: String) : Exception(message)

class NonRetryableSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal object BookingRecoveryHelper {
    fun mergeRecoveredBookings(
        remoteBookings: List<SlotBooking>,
        localBookings: List<SlotBooking>
    ): List<SlotBooking> {
        val pendingLocalIds = localBookings
            .asSequence()
            .filter { it.syncStatus == SyncStatus.PENDING }
            .map { it.id }
            .toSet()

        return remoteBookings
            .filter { it.id.isNotBlank() && it.id !in pendingLocalIds }
            .map { it.copy(syncStatus = SyncStatus.SYNCED) }
    }
}

internal object SyncErrorClassifier {
    private val nonRetryableFirestoreCodes = setOf(
        "PERMISSION_DENIED",
        "UNAUTHENTICATED",
        "INVALID_ARGUMENT"
    )

    fun isNonRetryableFirestoreCodeName(codeName: String?): Boolean =
        codeName in nonRetryableFirestoreCodes

    fun isNonRetryable(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            when (current) {
                is NonRetryableSyncException -> return true
                is FirebaseFirestoreException -> {
                    if (isNonRetryableFirestoreCodeName(current.code.name)) return true
                }
            }
            current = current.cause
        }
        return false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LaundryRepository @Inject constructor(
    private val database: AppDatabase,
    private val machineDao: WashingMachineDao,
    private val bookingDao: SlotBookingDao,
    private val issueDao: MachineIssueDao,
    private val sessionManager: SessionManager,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) {
    private val syncMutex = Mutex()
    private val seedMutex = Mutex()

    @Volatile
    private var hasCheckedSeeding = false

    // --- Machines ---
    val allMachines: Flow<List<WashingMachine>> = machineDao.getAllMachines()

    fun getMachinesByBlock(blockName: String): Flow<List<WashingMachine>> =
        machineDao.getMachinesByBlock(blockName)

    fun getMachinesByBlockAndFloor(blockName: String, floor: Int): Flow<List<WashingMachine>> =
        machineDao.getMachinesByBlockAndFloor(blockName, floor)

    suspend fun getMachineById(id: String): WashingMachine? = machineDao.getMachineById(id)

    suspend fun checkAndSeedMachines() {
        if (hasCheckedSeeding) return
        seedMutex.withLock {
            if (hasCheckedSeeding) return
            try {
                val count = machineDao.getTotalMachineCount().first()
                if (count == 0) {
                    machineDao.insertMachines(AppDatabase.createSeedMachines())
                }
                hasCheckedSeeding = true
            } catch (e: Exception) {
                Log.w("LaundryRepo", "Error checking/seeding machines: ${e.message}")
            }
        }
    }

    private suspend fun requireActiveSessionHostellerId(): String {
        val loggedIn = sessionManager.isLoggedIn.first()
        val hostellerId = sessionManager.hostellerId.first()?.takeIf { it.isNotBlank() }
        if (!loggedIn || hostellerId == null) {
            throw IllegalStateException("Session expired. Please log in again.")
        }
        return hostellerId
    }

    // --- Counts & Stats ---
    val availableCount: Flow<Int> = machineDao.getAvailableCount()
    val inUseCount: Flow<Int> = machineDao.getInUseCount()
    val outOfOrderCount: Flow<Int> = machineDao.getOutOfOrderCount()
    val totalCount: Flow<Int> = machineDao.getTotalMachineCount()

    val userUpcomingBookingsCount: Flow<Int> = sessionManager.hostellerId.flatMapLatest { id ->
        if (id == null) flowOf(0) else bookingDao.getUpcomingCount(id)
    }

    val userCompletedCount: Flow<Int> = sessionManager.hostellerId.flatMapLatest { id ->
        if (id == null) flowOf(0) else bookingDao.getCompletedCount(id)
    }

    val pendingSyncCount: Flow<Int> = sessionManager.hostellerId.flatMapLatest { id ->
        if (id == null) flowOf(0) else bookingDao.getPendingCount(id)
    }

    // --- Bookings ---
    val userBookings: Flow<List<SlotBooking>> = sessionManager.hostellerId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else bookingDao.getAllBookings(id)
    }

    val userUpcomingBookings: Flow<List<SlotBooking>> = sessionManager.hostellerId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else bookingDao.getUpcomingBookings(id)
    }

    val userActiveBookings: Flow<List<SlotBooking>> = sessionManager.hostellerId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else bookingDao.getActiveBookings(id)
    }

    val userCompletedBookings: Flow<List<SlotBooking>> = sessionManager.hostellerId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else bookingDao.getCompletedBookings(id)
    }

    fun getBookingsForMachineAndDate(machineId: String, date: Long): Flow<List<SlotBooking>> =
        bookingDao.getBookingsForMachineAndDate(machineId, date)

    fun getBookingsForDate(date: Long): Flow<List<SlotBooking>> =
        bookingDao.getBookingsForDate(date)

    // --- Slot Operations ---
    suspend fun bookSlot(
        machineId: String,
        date: Long,
        timeSlot: String,
        cycleType: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        notes: String = ""
    ): Result<SlotBooking> {
        return try {
            val now = System.currentTimeMillis()
            val normalizedDate = SlotTimeHelper.getStartOfDay(date)

            if (SlotTimeHelper.isPastDate(normalizedDate, now)) {
                return Result.failure(IllegalArgumentException("Cannot book a slot on a past date."))
            }
            if (startTimeMillis <= now || endTimeMillis <= startTimeMillis) {
                return Result.failure(
                    IllegalArgumentException("This time slot has already started or passed. Please select an upcoming slot.")
                )
            }

            val machine = machineDao.getMachineById(machineId)
                ?: return Result.failure(IllegalStateException("Selected machine was not found."))

            if (machine.status == MachineStatus.OUT_OF_ORDER) {
                return Result.failure(IllegalStateException("${machine.name} is currently under maintenance."))
            }

            val parsedCycle = CycleType.entries.firstOrNull { it.displayName == cycleType }
            if (parsedCycle != null && !machine.supportsCycle(parsedCycle)) {
                return Result.failure(
                    IllegalArgumentException("${machine.name} does not support ${parsedCycle.displayName}.")
                )
            }

            val overlapCount = bookingDao.countOverlappingBookings(
                machineId = machineId,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis
            )
            if (overlapCount > 0) {
                return Result.failure(
                    SlotAlreadyBookedException("This time slot is already booked for ${machine.name}.")
                )
            }

            val hostellerId = requireActiveSessionHostellerId()
            val hostellerName = sessionManager.name.first() ?: "Hosteller"
            val roomNumber = sessionManager.roomNumber.first()
            val hostelBlock = sessionManager.hostelBlock.first()

            val randomToken = "LS-${(1000..9999).random()}"
            val bookingId = UUID.randomUUID().toString()
            val lockId = SlotTimeHelper.buildSlotLockId(machine.id, normalizedDate, timeSlot)

            val baseBooking = SlotBooking(
                id = bookingId,
                machineId = machine.id,
                machineName = machine.name,
                machineType = machine.type,
                hostellerId = hostellerId,
                hostellerName = hostellerName,
                roomNumber = roomNumber,
                hostelBlock = hostelBlock,
                date = normalizedDate,
                timeSlot = timeSlot,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                cycleType = cycleType,
                status = BookingStatus.UPCOMING,
                bookingCode = randomToken,
                reminderSet = true,
                notes = notes.trim(),
                createdAt = now,
                lastUpdated = now,
                syncStatus = SyncStatus.PENDING
            )

            var finalBooking = baseBooking
            var needsBackgroundSync = true

            if (networkMonitor.isCurrentlyOnline()) {
                try {
                    val syncedBooking = baseBooking.copy(syncStatus = SyncStatus.SYNCED)
                    acquireFirestoreSlotLockAndWriteBooking(
                        lockId = lockId,
                        booking = syncedBooking
                    )
                    finalBooking = syncedBooking
                    needsBackgroundSync = false
                } catch (e: Exception) {
                    findConflictException(e)?.let { return Result.failure(it) }
                    findMachineUnavailableException(e)?.let { unavailable ->
                        machineDao.atomicFlagMaintenance(machine.id, "Maintenance (Cloud Sync)")
                        return Result.failure(unavailable)
                    }
                    if (SyncErrorClassifier.isNonRetryable(e)) {
                        return Result.failure(
                            NonRetryableSyncException(
                                "Session expired or insufficient permissions. Please sign in again.",
                                e
                            )
                        )
                    }
                    // Transient network/Firestore error -> fall back to PENDING local queue
                    Log.w("LaundryRepo", "Cloud booking deferred to pending queue: ${e.message}")
                }
            }

            database.withTransaction {
                val txOverlap = bookingDao.countOverlappingBookings(
                    machineId = machineId,
                    startTimeMillis = startTimeMillis,
                    endTimeMillis = endTimeMillis
                )
                if (txOverlap > 0) {
                    throw SlotAlreadyBookedException("This time slot is already booked for ${machine.name}.")
                }
                bookingDao.insertBooking(finalBooking)
            }

            if (sessionManager.notificationEnabled.first()) {
                scheduleSlotReminder(finalBooking)
                showBookingConfirmationNotification(finalBooking)
            }

            if (needsBackgroundSync) {
                triggerOneTimeSync()
            } else {
                sessionManager.updateLastSyncTime(System.currentTimeMillis())
            }

            Result.success(finalBooking)
        } catch (e: Exception) {
            Result.failure(findConflictException(e) ?: findMachineUnavailableException(e) ?: e)
        }
    }

    suspend fun startWashCycle(
        machineId: String,
        cycleType: CycleType
    ): Result<Unit> {
        val machine = machineDao.getMachineById(machineId)
            ?: return Result.failure(Exception("Machine not found"))
        if (!machine.supportsCycle(cycleType)) {
            return Result.failure(
                Exception("${machine.name} does not support ${cycleType.displayName}.")
            )
        }
        return startWashCycle(machineId, cycleType.displayName, cycleType.durationMinutes)
    }

    suspend fun startWashCycle(
        machineId: String,
        cycleType: String,
        durationMinutes: Int
    ): Result<Unit> {
        return try {
            val machine = machineDao.getMachineById(machineId)
                ?: return Result.failure(IllegalStateException("Machine not found"))

            if (machine.status != MachineStatus.AVAILABLE) {
                val reason = when (machine.status) {
                    MachineStatus.OUT_OF_ORDER -> "${machine.name} is currently under maintenance."
                    MachineStatus.IN_USE -> "${machine.name} is already running a wash cycle."
                    MachineStatus.RESERVED -> "${machine.name} is currently reserved for another slot."
                    else -> "${machine.name} is unavailable."
                }
                return Result.failure(IllegalStateException(reason))
            }

            val parsedCycle = CycleType.entries.firstOrNull { it.displayName == cycleType }
            if (parsedCycle != null && !machine.supportsCycle(parsedCycle)) {
                return Result.failure(
                    IllegalArgumentException("${machine.name} does not support $cycleType.")
                )
            }

            if (!networkMonitor.isCurrentlyOnline()) {
                return Result.failure(
                    IllegalStateException(
                        "Internet connection is required to start an instant wash cycle so other hostellers see live machine status."
                    )
                )
            }

            val roomNumber = sessionManager.roomNumber.first()
            val hostellerName = sessionManager.name.first() ?: "Hosteller"
            val hostellerId = requireActiveSessionHostellerId()
            val hostelBlock = sessionManager.hostelBlock.first()

            val now = System.currentTimeMillis()
            val endTime = now + (durationMinutes * 60 * 1000L)
            val userLabel = "Room $roomNumber ($hostellerName)"

            val activeBooking = SlotBooking(
                id = UUID.randomUUID().toString(),
                machineId = machine.id,
                machineName = machine.name,
                machineType = machine.type,
                hostellerId = hostellerId,
                hostellerName = hostellerName,
                roomNumber = roomNumber,
                hostelBlock = hostelBlock,
                date = SlotTimeHelper.getStartOfDay(now),
                timeSlot = "Now (${durationMinutes}m)",
                startTimeMillis = now,
                endTimeMillis = endTime,
                cycleType = cycleType,
                status = BookingStatus.ACTIVE,
                bookingCode = "LS-LIVE-${(100..999).random()}",
                reminderSet = true,
                createdAt = now,
                lastUpdated = now,
                syncStatus = SyncStatus.SYNCED
            )

            // Atomically claim the machine remotely before mutating local Room state
            try {
                claimFirestoreMachineForStartNow(
                    machineId = machine.id,
                    machineName = machine.name,
                    cycleType = cycleType,
                    endTime = endTime,
                    userLabel = userLabel,
                    booking = activeBooking
                )
            } catch (e: Exception) {
                findMachineUnavailableException(e)?.let { unavailable ->
                    refreshSingleMachineFromRemote(machine.id)
                    return Result.failure(unavailable)
                }
                if (SyncErrorClassifier.isNonRetryable(e)) {
                    return Result.failure(
                        NonRetryableSyncException(
                            "Session expired or insufficient permissions. Please sign in again.",
                            e
                        )
                    )
                }
                return Result.failure(
                    IllegalStateException(
                        "Could not reserve ${machine.name} on the server. Please check your connection and try again.",
                        e
                    )
                )
            }

            database.withTransaction {
                if (bookingDao.countActiveBookingsForMachine(machineId) > 0) {
                    throw IllegalStateException("${machine.name} already has an active wash cycle.")
                }
                val updatedRows = machineDao.atomicStartMachine(
                    id = machineId,
                    cycleName = cycleType,
                    endTime = endTime,
                    user = userLabel
                )
                if (updatedRows == 0) {
                    throw IllegalStateException(
                        "${machine.name} is no longer available. Please refresh and try again."
                    )
                }
                bookingDao.insertBooking(activeBooking)
            }

            if (sessionManager.notificationEnabled.first()) {
                scheduleCycleCompletion(
                    machineName = machine.name,
                    machineId = machine.id,
                    roomNumber = roomNumber,
                    durationMillis = durationMinutes * 60 * 1000L
                )
            }

            sessionManager.updateLastSyncTime(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(findMachineUnavailableException(e) ?: e)
        }
    }

    suspend fun completeWashCycle(machineId: String, bookingId: String? = null): Result<Unit> {
        return try {
            val currentHostellerId = requireActiveSessionHostellerId()
            val now = System.currentTimeMillis()
            var alreadyCompleted = false

            database.withTransaction {
                if (bookingId != null) {
                    val existing = bookingDao.getBookingById(bookingId)
                    if (existing != null) {
                        if (existing.hostellerId.isNotBlank() &&
                            existing.hostellerId != currentHostellerId
                        ) {
                            throw IllegalStateException("Only the hosteller who started this cycle can mark it done.")
                        }
                        if (existing.status == BookingStatus.COMPLETED) {
                            alreadyCompleted = true
                            return@withTransaction
                        }
                        bookingDao.updateBookingStatus(
                            id = bookingId,
                            status = BookingStatus.COMPLETED,
                            lastUpdated = now,
                            syncStatus = SyncStatus.PENDING
                        )
                    }
                }
                machineDao.atomicCompleteMachine(machineId)
            }

            WorkManager.getInstance(context).cancelUniqueWork("cycle_reminder_$machineId")
            if (!alreadyCompleted) {
                var syncedNow = false
                if (networkMonitor.isCurrentlyOnline()) {
                    try {
                        updateRemoteMachineTransitionIfExists(
                            machineId = machineId,
                            targetStatus = MachineStatus.AVAILABLE,
                            allowedSourceStatuses = setOf(MachineStatus.IN_USE, MachineStatus.RESERVED),
                            cycleName = null,
                            endTime = null,
                            userOrReason = null
                        )
                        if (bookingId != null) {
                            bookingDao.getBookingById(bookingId)?.let { updated ->
                                val synced = updated.copy(syncStatus = SyncStatus.SYNCED)
                                firestore.collection("hostellers").document(currentHostellerId)
                                    .collection("bookings").document(bookingId)
                                    .set(synced)
                                    .await()
                                bookingDao.updateBooking(synced)
                            }
                        }
                        sessionManager.updateLastSyncTime(System.currentTimeMillis())
                        syncedNow = true
                    } catch (e: Exception) {
                        Log.w("LaundryRepo", "Immediate completion sync deferred: ${e.message}")
                    }
                }
                if (!syncedNow) {
                    triggerOneTimeSync()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelBooking(bookingId: String): Result<Unit> {
        return try {
            val currentHostellerId = requireActiveSessionHostellerId()
            val booking = bookingDao.getBookingById(bookingId)
                ?: return Result.failure(IllegalStateException("Booking not found."))

            if (booking.hostellerId.isNotBlank() &&
                booking.hostellerId != currentHostellerId
            ) {
                return Result.failure(IllegalStateException("You can only cancel your own reservations."))
            }

            if (booking.status == BookingStatus.CANCELLED) {
                return Result.success(Unit)
            }
            if (booking.status == BookingStatus.ACTIVE || booking.status == BookingStatus.COMPLETED) {
                return Result.failure(
                    IllegalStateException("Active or completed cycles cannot be cancelled.")
                )
            }

            val now = System.currentTimeMillis()
            bookingDao.updateBookingStatus(
                id = bookingId,
                status = BookingStatus.CANCELLED,
                lastUpdated = now,
                syncStatus = SyncStatus.PENDING
            )
            WorkManager.getInstance(context).cancelUniqueWork("slot_reminder_$bookingId")

            var syncedNow = false
            if (networkMonitor.isCurrentlyOnline()) {
                try {
                    val isHourlySlot = SlotTimeHelper.computeSlotWindow(booking.date, booking.timeSlot) != null
                    if (isHourlySlot) {
                        val lockId = SlotTimeHelper.buildSlotLockId(booking.machineId, booking.date, booking.timeSlot)
                        releaseFirestoreSlotLock(lockId, booking.hostellerId)
                    }
                    val synced = booking.copy(
                        status = BookingStatus.CANCELLED,
                        lastUpdated = now,
                        syncStatus = SyncStatus.SYNCED
                    )
                    firestore.collection("hostellers").document(currentHostellerId)
                        .collection("bookings").document(booking.id)
                        .set(synced)
                        .await()
                    bookingDao.updateBooking(synced)
                    sessionManager.updateLastSyncTime(System.currentTimeMillis())
                    syncedNow = true
                } catch (e: Exception) {
                    Log.w("LaundryRepo", "Immediate cancellation sync deferred: ${e.message}")
                }
            }

            if (!syncedNow) {
                triggerOneTimeSync()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Issue Reporting ---
    val allIssues: Flow<List<MachineIssueReport>> = issueDao.getAllIssues()

    suspend fun reportIssue(
        machineId: String,
        issueCategory: String,
        description: String
    ): Result<Unit> {
        return try {
            val currentHostellerId = requireActiveSessionHostellerId()
            val trimmedCategory = issueCategory.trim()
            if (trimmedCategory.isEmpty()) {
                return Result.failure(IllegalArgumentException("Please select at least one fault category."))
            }

            val machine = machineDao.getMachineById(machineId)
                ?: return Result.failure(IllegalStateException("Machine not found"))

            val existingUnresolved = issueDao.getUnresolvedIssuesForMachine(machineId)
            if (machine.status == MachineStatus.OUT_OF_ORDER && existingUnresolved.isNotEmpty()) {
                return Result.failure(
                    IllegalStateException("A maintenance report is already open for ${machine.name}.")
                )
            }

            val hostellerName = sessionManager.name.first() ?: "Hosteller"
            val roomNumber = sessionManager.roomNumber.first()
            val now = System.currentTimeMillis()

            val issue = MachineIssueReport(
                id = UUID.randomUUID().toString(),
                machineId = machine.id,
                machineName = machine.name,
                reportedBy = hostellerName,
                roomNumber = roomNumber,
                issueCategory = trimmedCategory,
                description = description.trim(),
                reportedAt = now,
                isResolved = false,
                syncStatus = SyncStatus.PENDING
            )

            database.withTransaction {
                issueDao.insertIssue(issue)
                machineDao.atomicFlagMaintenance(machineId, "Issue: $trimmedCategory")
                bookingDao.updateActiveBookingsForMachine(
                    machineId = machineId,
                    newStatus = BookingStatus.CANCELLED,
                    notes = "Interrupted by maintenance report: $trimmedCategory",
                    lastUpdated = now,
                    syncStatus = SyncStatus.PENDING
                )
            }

            WorkManager.getInstance(context).cancelUniqueWork("cycle_reminder_$machineId")

            var syncedNow = false
            if (networkMonitor.isCurrentlyOnline()) {
                try {
                    val syncedIssue = issue.copy(syncStatus = SyncStatus.SYNCED)
                    firestore.collection("machine_issues").document(issue.id)
                        .set(syncedIssue)
                        .await()
                    updateRemoteMachineTransitionIfExists(
                        machineId = machineId,
                        targetStatus = MachineStatus.OUT_OF_ORDER,
                        allowedSourceStatuses = setOf(
                            MachineStatus.AVAILABLE,
                            MachineStatus.IN_USE,
                            MachineStatus.RESERVED
                        ),
                        cycleName = null,
                        endTime = null,
                        userOrReason = "Issue: $trimmedCategory"
                    )
                    issueDao.updateIssue(syncedIssue)
                    // Sync any locally interrupted active booking belonging to this hosteller
                    bookingDao.getBookingsBySyncStatusForHosteller(SyncStatus.PENDING, currentHostellerId)
                        .filter { it.machineId == machineId && it.status == BookingStatus.CANCELLED }
                        .forEach { interrupted ->
                            val syncedBooking = interrupted.copy(syncStatus = SyncStatus.SYNCED)
                            firestore.collection("hostellers").document(currentHostellerId)
                                .collection("bookings").document(interrupted.id)
                                .set(syncedBooking)
                                .await()
                            bookingDao.updateBooking(syncedBooking)
                        }
                    sessionManager.updateLastSyncTime(System.currentTimeMillis())
                    syncedNow = true
                } catch (e: Exception) {
                    Log.w("LaundryRepo", "Immediate issue sync deferred: ${e.message}")
                }
            }

            if (!syncedNow) {
                triggerOneTimeSync()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Notices & Guidelines ---
    fun getLaundryNotices(): List<LaundryNotice> = DEFAULT_NOTICES

    // --- Notifications & WorkManager ---
    private fun scheduleSlotReminder(booking: SlotBooking) {
        val reminderTime = booking.startTimeMillis - (15 * 60 * 1000L)
        val delay = reminderTime - System.currentTimeMillis()

        if (delay > 0) {
            val data = Data.Builder()
                .putString("machineName", booking.machineName)
                .putString("timeSlot", booking.timeSlot)
                .putString("bookingId", booking.id)
                .build()

            val reminderRequest = OneTimeWorkRequestBuilder<SlotReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "slot_reminder_${booking.id}",
                ExistingWorkPolicy.REPLACE,
                reminderRequest
            )
        }
    }

    private fun scheduleCycleCompletion(
        machineName: String,
        machineId: String,
        roomNumber: String,
        durationMillis: Long
    ) {
        val data = Data.Builder()
            .putString("machineName", machineName)
            .putString("machineId", machineId)
            .putString("roomNumber", roomNumber)
            .build()

        val cycleRequest = OneTimeWorkRequestBuilder<LaundryCycleReminderWorker>()
            .setInitialDelay(durationMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "cycle_reminder_$machineId",
            ExistingWorkPolicy.REPLACE,
            cycleRequest
        )
    }

    private fun showBookingConfirmationNotification(booking: SlotBooking) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = "booking_confirmations"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Booking Confirmations",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        val dateString = dateFormat.format(Date(booking.date))

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Slot Booked: ${booking.bookingCode}")
            .setContentText("${booking.machineName} reserved on $dateString (${booking.timeSlot})")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(booking.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked at runtime; ignore safely
        }
    }

    suspend fun rescheduleAllReminders() {
        if (!sessionManager.notificationEnabled.first()) return
        val now = System.currentTimeMillis()
        val hostellerId = sessionManager.hostellerId.first() ?: return
        val upcoming = bookingDao.getUpcomingBookings(hostellerId).first()
        upcoming
            .filter { it.status == BookingStatus.UPCOMING && it.startTimeMillis > now }
            .forEach { booking ->
                scheduleSlotReminder(booking)
            }
    }

    // --- Sync Logic ---
    suspend fun refreshDashboard(): Result<Unit> {
        return try {
            checkAndSeedMachines()
            syncPendingChangesOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPendingChangesOrThrow() {
        syncMutex.withLock {
            syncLocalWithRemoteOrThrow()
        }
    }

    suspend fun syncSlotLocksForDate(date: Long) {
        if (!networkMonitor.isCurrentlyOnline()) return
        if (!sessionManager.isLoggedIn.first()) return
        val normalizedDate = SlotTimeHelper.getStartOfDay(date)
        val currentHostellerId = sessionManager.hostellerId.first()
        try {
            val remoteLocks = firestore.collection("machine_slot_locks")
                .whereEqualTo("date", normalizedDate)
                .get()
                .await()

            val localForDate = bookingDao.getBookingsForDateSnapshot(normalizedDate)
            val remoteActiveMirrorIds = mutableSetOf<String>()
            val mirrorsToInsert = mutableListOf<SlotBooking>()

            for (doc in remoteLocks.documents) {
                val status = doc.getString("status") ?: "UPCOMING"
                if (status == "CANCELLED") continue
                val lockHostellerId = doc.getString("hostellerId").orEmpty()
                if (lockHostellerId == currentHostellerId) continue

                val mirrorId = "lock_${doc.id}"
                remoteActiveMirrorIds += mirrorId
                val machineId = doc.getString("machineId") ?: continue
                val timeSlot = doc.getString("timeSlot") ?: continue
                val startMillis = doc.getLong("startTimeMillis") ?: continue
                val endMillis = doc.getLong("endTimeMillis") ?: continue
                val bookingId = doc.getString("bookingId") ?: doc.id

                // Do not duplicate if already tracked locally by original booking ID
                if (localForDate.any { it.id == bookingId }) continue

                mirrorsToInsert += SlotBooking(
                    id = mirrorId,
                    machineId = machineId,
                    machineName = doc.getString("machineName") ?: machineId,
                    hostellerId = lockHostellerId.ifBlank { "other_hosteller" },
                    date = normalizedDate,
                    timeSlot = timeSlot,
                    startTimeMillis = startMillis,
                    endTimeMillis = endMillis,
                    status = BookingStatus.UPCOMING,
                    bookingCode = "OCCUPIED",
                    reminderSet = false,
                    syncStatus = SyncStatus.SYNCED
                )
            }

            database.withTransaction {
                if (mirrorsToInsert.isNotEmpty()) {
                    bookingDao.insertBookings(mirrorsToInsert)
                }
                // Clean up any stale remote lock mirrors that were cancelled remotely
                localForDate
                    .filter { it.id.startsWith("lock_") && it.id !in remoteActiveMirrorIds }
                    .forEach { staleMirror ->
                        bookingDao.deleteBookingById(staleMirror.id)
                    }
            }
        } catch (e: Exception) {
            Log.w("LaundrySync", "Could not refresh slot locks: ${e.message}")
        }
    }

    private suspend fun syncLocalWithRemoteOrThrow() {
        val loggedIn = sessionManager.isLoggedIn.first()
        val hostellerId = sessionManager.hostellerId.first()?.takeIf { it.isNotBlank() }
        if (!loggedIn || hostellerId == null) {
            throw NonRetryableSyncException("No active session for background sync.")
        }
        if (firebaseAuth.currentUser == null) {
            throw NonRetryableSyncException("Firebase session expired. Please sign in again.")
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            throw IllegalStateException("Offline: showing cached machine and slot status.")
        }

        var firstError: Exception? = null

        // 1. Push pending bookings scoped strictly to the active hosteller
        val pendingBookings = bookingDao.getBookingsBySyncStatusForHosteller(SyncStatus.PENDING, hostellerId)
        for (booking in pendingBookings) {
            try {
                val isHourlySlot = SlotTimeHelper.computeSlotWindow(booking.date, booking.timeSlot) != null
                val lockId = SlotTimeHelper.buildSlotLockId(booking.machineId, booking.date, booking.timeSlot)

                when (booking.status) {
                    BookingStatus.UPCOMING -> {
                        if (isHourlySlot) {
                            try {
                                acquireFirestoreSlotLockAndWriteBooking(
                                    lockId = lockId,
                                    booking = booking.copy(syncStatus = SyncStatus.SYNCED)
                                )
                                bookingDao.updateBooking(booking.copy(syncStatus = SyncStatus.SYNCED))
                            } catch (e: Exception) {
                                val conflict = findConflictException(e)
                                val unavailable = findMachineUnavailableException(e)
                                if (conflict != null || unavailable != null) {
                                    val reason = if (unavailable != null) {
                                        "Slot cancelled on sync: machine is under maintenance"
                                    } else {
                                        "Slot conflict on sync: already reserved by another hosteller"
                                    }
                                    val cancelledConflict = booking.copy(
                                        status = BookingStatus.CANCELLED,
                                        notes = reason,
                                        lastUpdated = System.currentTimeMillis(),
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                    bookingDao.updateBooking(cancelledConflict)
                                    WorkManager.getInstance(context)
                                        .cancelUniqueWork("slot_reminder_${booking.id}")
                                    firestore.collection("hostellers").document(hostellerId)
                                        .collection("bookings").document(booking.id)
                                        .set(cancelledConflict)
                                        .await()
                                } else {
                                    throw e
                                }
                            }
                        } else {
                            firestore.collection("hostellers").document(hostellerId)
                                .collection("bookings").document(booking.id)
                                .set(booking.copy(syncStatus = SyncStatus.SYNCED))
                                .await()
                            bookingDao.updateBooking(booking.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }

                    BookingStatus.CANCELLED -> {
                        if (isHourlySlot) {
                            releaseFirestoreSlotLock(lockId, booking.hostellerId)
                        }
                        val synced = booking.copy(syncStatus = SyncStatus.SYNCED)
                        firestore.collection("hostellers").document(hostellerId)
                            .collection("bookings").document(booking.id)
                            .set(synced)
                            .await()
                        bookingDao.updateBooking(synced)
                    }

                    BookingStatus.ACTIVE -> {
                        val synced = booking.copy(syncStatus = SyncStatus.SYNCED)
                        firestore.collection("hostellers").document(hostellerId)
                            .collection("bookings").document(booking.id)
                            .set(synced)
                            .await()
                        machineDao.getMachineById(booking.machineId)?.let { machine ->
                            updateRemoteMachineTransitionIfExists(
                                machineId = machine.id,
                                targetStatus = MachineStatus.IN_USE,
                                allowedSourceStatuses = setOf(
                                    MachineStatus.AVAILABLE,
                                    MachineStatus.RESERVED
                                ),
                                cycleName = machine.currentCycleName,
                                endTime = machine.cycleEstimatedEndTime,
                                userOrReason = machine.currentUserRoomOrName
                            )
                        }
                        bookingDao.updateBooking(synced)
                    }

                    BookingStatus.COMPLETED -> {
                        val synced = booking.copy(syncStatus = SyncStatus.SYNCED)
                        firestore.collection("hostellers").document(hostellerId)
                            .collection("bookings").document(booking.id)
                            .set(synced)
                            .await()
                        updateRemoteMachineTransitionIfExists(
                            machineId = booking.machineId,
                            targetStatus = MachineStatus.AVAILABLE,
                            allowedSourceStatuses = setOf(
                                MachineStatus.IN_USE,
                                MachineStatus.RESERVED
                            ),
                            cycleName = null,
                            endTime = null,
                            userOrReason = null
                        )
                        bookingDao.updateBooking(synced)
                    }
                }
            } catch (e: Exception) {
                Log.w("LaundrySync", "Booking sync error: ${e.message}")
                if (firstError == null) firstError = e
            }
        }

        // 2. Push pending issues & maintenance machine status (never overwrite admin-only fields)
        val pendingIssues = issueDao.getIssuesBySyncStatus(SyncStatus.PENDING)
        for (issue in pendingIssues) {
            try {
                val syncedIssue = issue.copy(syncStatus = SyncStatus.SYNCED)
                firestore.collection("machine_issues").document(issue.id)
                    .set(syncedIssue)
                    .await()
                updateRemoteMachineTransitionIfExists(
                    machineId = issue.machineId,
                    targetStatus = MachineStatus.OUT_OF_ORDER,
                    allowedSourceStatuses = setOf(
                        MachineStatus.AVAILABLE,
                        MachineStatus.IN_USE,
                        MachineStatus.RESERVED
                    ),
                    cycleName = null,
                    endTime = null,
                    userOrReason = "Issue: ${issue.issueCategory}"
                )
                issueDao.updateIssue(syncedIssue)
            } catch (e: Exception) {
                Log.w("LaundrySync", "Issue sync error: ${e.message}")
                if (firstError == null) firstError = e
            }
        }

        // 3. Pull remote machines (preserving any machines with local unsynced changes)
        try {
            val remoteMachinesSnap = firestore.collection("machines").get().await()
            if (!remoteMachinesSnap.isEmpty) {
                val pendingBookingMachineIds = bookingDao
                    .getBookingsBySyncStatusForHosteller(SyncStatus.PENDING, hostellerId)
                    .map { it.machineId }
                    .toSet()
                val pendingIssueMachineIds = issueDao.getIssuesBySyncStatus(SyncStatus.PENDING)
                    .map { it.machineId }
                    .toSet()
                val protectedIds = pendingBookingMachineIds + pendingIssueMachineIds

                val toUpsert = remoteMachinesSnap.documents.mapNotNull { doc ->
                    doc.toObject(WashingMachine::class.java)?.takeIf { it.id.isNotBlank() }
                }.filter { it.id !in protectedIds }

                if (toUpsert.isNotEmpty()) {
                    machineDao.insertMachines(toUpsert)
                }
            }
        } catch (e: Exception) {
            Log.w("LaundrySync", "Skipping remote machine pull: ${e.message}")
        }

        // 4. Pull remote bookings for current hosteller (device replacement / reinstall recovery)
        try {
            val remoteBookingsSnap = firestore.collection("hostellers")
                .document(hostellerId)
                .collection("bookings")
                .get()
                .await()
            if (!remoteBookingsSnap.isEmpty) {
                val remoteBookings = remoteBookingsSnap.documents.mapNotNull { doc ->
                    doc.toObject(SlotBooking::class.java)
                }
                val localBookings = bookingDao.getAllBookingsSnapshot(hostellerId)
                val recovered = BookingRecoveryHelper.mergeRecoveredBookings(remoteBookings, localBookings)
                if (recovered.isNotEmpty()) {
                    bookingDao.insertBookings(recovered)
                    rescheduleAllReminders()
                }
            }
        } catch (e: Exception) {
            Log.w("LaundrySync", "Skipping remote booking recovery pull: ${e.message}")
        }

        // 5. Refresh today's slot locks from other hostellers
        syncSlotLocksForDate(SlotTimeHelper.getStartOfDay(System.currentTimeMillis()))

        firstError?.let { error ->
            if (SyncErrorClassifier.isNonRetryable(error)) {
                throw NonRetryableSyncException(
                    error.message ?: "Authorization error during sync",
                    error
                )
            }
            throw error
        }

        sessionManager.updateLastSyncTime(System.currentTimeMillis())
    }

    private suspend fun claimFirestoreMachineForStartNow(
        machineId: String,
        machineName: String,
        cycleType: String,
        endTime: Long,
        userLabel: String,
        booking: SlotBooking
    ) {
        val machineRef = firestore.collection("machines").document(machineId)
        val bookingRef = firestore.collection("hostellers").document(booking.hostellerId)
            .collection("bookings").document(booking.id)

        firestore.runTransaction { transaction ->
            val machineSnap = transaction.get(machineRef)
            if (machineSnap.exists()) {
                val remoteStatus = machineSnap.getString("status")
                if (remoteStatus != MachineStatus.AVAILABLE.name) {
                    val reason = when (remoteStatus) {
                        MachineStatus.OUT_OF_ORDER.name -> "$machineName is currently under maintenance."
                        MachineStatus.IN_USE.name -> "$machineName was just started by another hosteller."
                        MachineStatus.RESERVED.name -> "$machineName is currently reserved for another slot."
                        else -> "$machineName is currently unavailable."
                    }
                    throw MachineUnavailableException(reason)
                }
                transaction.update(
                    machineRef,
                    mapOf(
                        "status" to MachineStatus.IN_USE.name,
                        "currentCycleName" to cycleType,
                        "cycleEstimatedEndTime" to endTime,
                        "currentUserRoomOrName" to userLabel
                    )
                )
            }
            transaction.set(bookingRef, booking)
        }.await()
    }

    private suspend fun updateRemoteMachineTransitionIfExists(
        machineId: String,
        targetStatus: MachineStatus,
        allowedSourceStatuses: Set<MachineStatus>,
        cycleName: String?,
        endTime: Long?,
        userOrReason: String?
    ) {
        val machineRef = firestore.collection("machines").document(machineId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(machineRef)
            if (!snapshot.exists()) return@runTransaction

            val remoteStatusName = snapshot.getString("status") ?: return@runTransaction
            val remoteStatus = runCatching { MachineStatus.valueOf(remoteStatusName) }.getOrNull()
                ?: return@runTransaction

            if (remoteStatus == MachineStatus.OUT_OF_ORDER) {
                return@runTransaction
            }
            if (remoteStatus !in allowedSourceStatuses) {
                return@runTransaction
            }
            if (remoteStatus == targetStatus &&
                snapshot.getString("currentCycleName") == cycleName &&
                snapshot.getLong("cycleEstimatedEndTime") == endTime &&
                snapshot.getString("currentUserRoomOrName") == userOrReason
            ) {
                return@runTransaction
            }

            transaction.update(
                machineRef,
                mapOf(
                    "status" to targetStatus.name,
                    "currentCycleName" to cycleName,
                    "cycleEstimatedEndTime" to endTime,
                    "currentUserRoomOrName" to userOrReason
                )
            )
        }.await()
    }

    private suspend fun refreshSingleMachineFromRemote(machineId: String) {
        runCatching {
            val remote = firestore.collection("machines").document(machineId).get().await()
                .toObject(WashingMachine::class.java)
            if (remote != null && remote.id.isNotBlank()) {
                machineDao.insertMachine(remote)
            }
        }
    }

    private suspend fun acquireFirestoreSlotLockAndWriteBooking(
        lockId: String,
        booking: SlotBooking
    ) {
        val lockRef = firestore.collection("machine_slot_locks").document(lockId)
        val machineRef = firestore.collection("machines").document(booking.machineId)
        val bookingRef = firestore.collection("hostellers").document(booking.hostellerId)
            .collection("bookings").document(booking.id)
        val authUid = firebaseAuth.currentUser?.uid.orEmpty()

        firestore.runTransaction { transaction ->
            val machineSnapshot = transaction.get(machineRef)
            if (machineSnapshot.exists() &&
                machineSnapshot.getString("status") == MachineStatus.OUT_OF_ORDER.name
            ) {
                throw MachineUnavailableException(
                    "${booking.machineName} is currently under maintenance."
                )
            }

            val lockSnapshot = transaction.get(lockRef)
            if (lockSnapshot.exists()) {
                val status = lockSnapshot.getString("status")
                val ownerId = lockSnapshot.getString("hostellerId")
                if (status != "CANCELLED" && ownerId != booking.hostellerId) {
                    throw SlotAlreadyBookedException(
                        "This time slot was just booked by another hosteller."
                    )
                }
                if (status != "CANCELLED" &&
                    lockSnapshot.getString("bookingId") != booking.id
                ) {
                    throw SlotAlreadyBookedException(
                        "You have already reserved this time slot on ${booking.machineName}."
                    )
                }
            }

            val lockData = mapOf(
                "machineId" to booking.machineId,
                "machineName" to booking.machineName,
                "date" to booking.date,
                "timeSlot" to booking.timeSlot,
                "startTimeMillis" to booking.startTimeMillis,
                "endTimeMillis" to booking.endTimeMillis,
                "hostellerId" to booking.hostellerId,
                "authUid" to authUid,
                "bookingId" to booking.id,
                "status" to "UPCOMING",
                "updatedAt" to System.currentTimeMillis()
            )

            transaction.set(lockRef, lockData)
            transaction.set(bookingRef, booking)
        }.await()
    }

    private suspend fun releaseFirestoreSlotLock(lockId: String, hostellerId: String) {
        val lockRef = firestore.collection("machine_slot_locks").document(lockId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(lockRef)
            if (snapshot.exists() && snapshot.getString("hostellerId") == hostellerId) {
                transaction.update(
                    lockRef,
                    mapOf(
                        "status" to "CANCELLED",
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            }
        }.await()
    }

    private fun findConflictException(throwable: Throwable?): SlotAlreadyBookedException? {
        var current = throwable
        while (current != null) {
            if (current is SlotAlreadyBookedException) return current
            current = current.cause
        }
        return null
    }

    private fun findMachineUnavailableException(throwable: Throwable?): MachineUnavailableException? {
        var current = throwable
        while (current != null) {
            if (current is MachineUnavailableException) return current
            current = current.cause
        }
        return null
    }

    fun triggerOneTimeSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "one_time_laundry_sync",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    companion object {
        private val DEFAULT_NOTICES = listOf(
            LaundryNotice(
                id = "1",
                title = "⚡ Peak Laundry Hours: 6:00 PM – 9:30 PM",
                description = "Machines fill up fast in the evenings. Book a morning or afternoon slot for zero waiting!",
                tag = "Peak Tip"
            ),
            LaundryNotice(
                id = "2",
                title = "🧺 Prompt Clothes Pickup Policy",
                description = "Please remove your dry/washed clothes within 10 minutes of cycle completion so fellow hostellers can use the machine.",
                tag = "Etiquette"
            ),
            LaundryNotice(
                id = "3",
                title = "🔧 Weekly Drum Maintenance",
                description = "Filter and drum descaling scheduled every Sunday between 7:00 AM – 9:00 AM.",
                tag = "Maintenance"
            )
        )
    }
}
