package com.example.aasha

import com.example.aasha.data.repository.BookingRecoveryHelper
import com.example.aasha.data.repository.MachineUnavailableException
import com.example.aasha.data.repository.NonRetryableSyncException
import com.example.aasha.data.repository.SlotAlreadyBookedException
import com.example.aasha.data.repository.SyncErrorClassifier
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SlotTimeHelper
import com.example.aasha.domain.model.SyncStatus
import com.example.aasha.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAndSecurityRulesTest {

    @Test
    fun `non-retryable sync exception is classified as non-retryable`() {
        val ex = NonRetryableSyncException("No active session")
        assertTrue(SyncErrorClassifier.isNonRetryable(ex))
    }

    @Test
    fun `wrapped non-retryable sync exception is non-retryable`() {
        val wrapped = RuntimeException(
            "Sync wrapper",
            NonRetryableSyncException("Missing or insufficient permissions")
        )
        assertTrue(SyncErrorClassifier.isNonRetryable(wrapped))
    }

    @Test
    fun `permission denied, unauthenticated, and invalid argument firestore codes are non-retryable`() {
        assertTrue(SyncErrorClassifier.isNonRetryableFirestoreCodeName("PERMISSION_DENIED"))
        assertTrue(SyncErrorClassifier.isNonRetryableFirestoreCodeName("UNAUTHENTICATED"))
        assertTrue(SyncErrorClassifier.isNonRetryableFirestoreCodeName("INVALID_ARGUMENT"))
    }

    @Test
    fun `transient unavailable and conflict exceptions remain retryable or non-fatal to worker classifier`() {
        val offline = IllegalStateException("Offline: showing cached machine and slot status.")
        val machineBusy = MachineUnavailableException("Machine busy")
        val slotTaken = SlotAlreadyBookedException("Slot taken")

        assertFalse(SyncErrorClassifier.isNonRetryableFirestoreCodeName("UNAVAILABLE"))
        assertFalse(SyncErrorClassifier.isNonRetryableFirestoreCodeName("DEADLINE_EXCEEDED"))
        assertFalse(SyncErrorClassifier.isNonRetryable(offline))
        assertFalse(SyncErrorClassifier.isNonRetryable(machineBusy))
        assertFalse(SyncErrorClassifier.isNonRetryable(slotTaken))
    }

    @Test
    fun `user model preserves backward-compatible defaults with authUid`() {
        val user = User(studentId = "bt21cs001", name = "Aarav", email = "bt21cs001@hostel.edu")
        assertEquals("", user.authUid)
        assertEquals("Hosteller", user.role)
        assertEquals("Block A", user.hostelBlock)
        assertEquals(1, user.floor)
    }

    @Test
    fun `slot lock id normalization prevents duplicate lock keys`() {
        val date = SlotTimeHelper.getStartOfDay(1_700_000_123_456L)
        val a = SlotTimeHelper.buildSlotLockId("wm-a1", date, "08:00 AM - 09:00 AM")
        val b = SlotTimeHelper.buildSlotLockId("wm-a1", date, "08:00 AM - 09:00 AM")
        assertEquals(a, b)
        assertTrue(a.startsWith("wm-a1_${date}_"))
    }

    @Test
    fun `booking recovery preserves local pending edits and marks recovered entries synced`() {
        val remoteSynced = SlotBooking(
            id = "b-remote-1",
            machineId = "wm-a1",
            hostellerId = "bt21cs001",
            status = BookingStatus.UPCOMING,
            syncStatus = SyncStatus.PENDING
        )
        val remoteStaleOverLocalPending = SlotBooking(
            id = "b-pending-2",
            machineId = "wm-a2",
            hostellerId = "bt21cs001",
            status = BookingStatus.UPCOMING,
            syncStatus = SyncStatus.SYNCED
        )
        val localPendingCancel = SlotBooking(
            id = "b-pending-2",
            machineId = "wm-a2",
            hostellerId = "bt21cs001",
            status = BookingStatus.CANCELLED,
            syncStatus = SyncStatus.PENDING
        )

        val recovered = BookingRecoveryHelper.mergeRecoveredBookings(
            remoteBookings = listOf(remoteSynced, remoteStaleOverLocalPending),
            localBookings = listOf(localPendingCancel)
        )

        assertEquals(1, recovered.size)
        assertEquals("b-remote-1", recovered.first().id)
        assertEquals(SyncStatus.SYNCED, recovered.first().syncStatus)
    }
}
