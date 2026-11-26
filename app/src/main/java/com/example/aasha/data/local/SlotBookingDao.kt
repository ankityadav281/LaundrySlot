package com.example.aasha.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SlotBookingDao {
    @Query("SELECT * FROM slot_bookings WHERE hostellerId = :hostellerId ORDER BY startTimeMillis DESC")
    fun getAllBookings(hostellerId: String): Flow<List<SlotBooking>>

    @Query("SELECT * FROM slot_bookings WHERE hostellerId = :hostellerId AND status = 'UPCOMING' ORDER BY startTimeMillis ASC")
    fun getUpcomingBookings(hostellerId: String): Flow<List<SlotBooking>>

    @Query("SELECT * FROM slot_bookings WHERE hostellerId = :hostellerId AND status = 'ACTIVE' ORDER BY startTimeMillis ASC")
    fun getActiveBookings(hostellerId: String): Flow<List<SlotBooking>>

    @Query("SELECT * FROM slot_bookings WHERE hostellerId = :hostellerId AND status = 'COMPLETED' ORDER BY startTimeMillis DESC")
    fun getCompletedBookings(hostellerId: String): Flow<List<SlotBooking>>

    @Query("SELECT * FROM slot_bookings WHERE machineId = :machineId AND date = :date AND status != 'CANCELLED' ORDER BY startTimeMillis ASC")
    fun getBookingsForMachineAndDate(machineId: String, date: Long): Flow<List<SlotBooking>>

    @Query("SELECT * FROM slot_bookings WHERE date = :date AND status != 'CANCELLED' ORDER BY startTimeMillis ASC")
    fun getBookingsForDate(date: Long): Flow<List<SlotBooking>>

    @Query("SELECT * FROM slot_bookings WHERE date = :date AND status != 'CANCELLED'")
    suspend fun getBookingsForDateSnapshot(date: Long): List<SlotBooking>

    @Query("SELECT * FROM slot_bookings WHERE machineId = :machineId AND date = :date AND status != 'CANCELLED'")
    suspend fun getBookingsForMachineOnDateSnapshot(machineId: String, date: Long): List<SlotBooking>

    @Query(
        "SELECT COUNT(*) FROM slot_bookings " +
            "WHERE machineId = :machineId AND status != 'CANCELLED' " +
            "AND startTimeMillis < :endTimeMillis AND endTimeMillis > :startTimeMillis"
    )
    suspend fun countOverlappingBookings(
        machineId: String,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Int

    @Query("SELECT COUNT(*) FROM slot_bookings WHERE machineId = :machineId AND status = 'ACTIVE'")
    suspend fun countActiveBookingsForMachine(machineId: String): Int

    @Query("SELECT * FROM slot_bookings WHERE id = :id")
    suspend fun getBookingById(id: String): SlotBooking?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: SlotBooking)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookings(bookings: List<SlotBooking>)

    @Update
    suspend fun updateBooking(booking: SlotBooking)

    @Query("DELETE FROM slot_bookings WHERE id = :id")
    suspend fun deleteBookingById(id: String)

    @Query("UPDATE slot_bookings SET status = :status, lastUpdated = :lastUpdated, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateBookingStatus(
        id: String,
        status: BookingStatus,
        lastUpdated: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING
    )

    @Query(
        "UPDATE slot_bookings " +
            "SET status = :newStatus, notes = :notes, lastUpdated = :lastUpdated, syncStatus = :syncStatus " +
            "WHERE machineId = :machineId AND status = 'ACTIVE'"
    )
    suspend fun updateActiveBookingsForMachine(
        machineId: String,
        newStatus: BookingStatus,
        notes: String,
        lastUpdated: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING
    ): Int

    @Query("SELECT * FROM slot_bookings WHERE hostellerId = :hostellerId")
    suspend fun getAllBookingsSnapshot(hostellerId: String): List<SlotBooking>

    @Query("SELECT * FROM slot_bookings WHERE syncStatus = :syncStatus")
    suspend fun getBookingsBySyncStatus(syncStatus: SyncStatus): List<SlotBooking>

    @Query("SELECT * FROM slot_bookings WHERE syncStatus = :syncStatus AND hostellerId = :hostellerId")
    suspend fun getBookingsBySyncStatusForHosteller(
        syncStatus: SyncStatus,
        hostellerId: String
    ): List<SlotBooking>

    @Query("SELECT COUNT(*) FROM slot_bookings WHERE hostellerId = :hostellerId AND syncStatus = 'PENDING'")
    fun getPendingCount(hostellerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM slot_bookings WHERE hostellerId = :hostellerId AND status = 'COMPLETED'")
    fun getCompletedCount(hostellerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM slot_bookings WHERE hostellerId = :hostellerId AND status = 'UPCOMING'")
    fun getUpcomingCount(hostellerId: String): Flow<Int>
}
