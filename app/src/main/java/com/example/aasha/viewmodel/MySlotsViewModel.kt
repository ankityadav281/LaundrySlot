package com.example.aasha.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aasha.data.repository.LaundryRepository
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.SlotBooking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MySlotsUiState(
    val activeBookings: List<SlotBooking> = emptyList(),
    val upcomingBookings: List<SlotBooking> = emptyList(),
    val completedBookings: List<SlotBooking> = emptyList(),
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val message: String? = null
)

@HiltViewModel
class MySlotsViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    private val busyBookingIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                _currentTime.value = System.currentTimeMillis()
            }
        }
    }

    val uiState: StateFlow<MySlotsUiState> = combine(
        laundryRepository.userBookings,
        _currentTime,
        _message
    ) { allBookings, now, msg ->
        val active = allBookings
            .filter { it.status == BookingStatus.ACTIVE }
            .sortedBy { it.startTimeMillis }

        val upcoming = allBookings
            .filter { it.status == BookingStatus.UPCOMING && it.endTimeMillis > now }
            .sortedBy { it.startTimeMillis }

        val history = allBookings
            .filter {
                it.status == BookingStatus.COMPLETED ||
                    it.status == BookingStatus.CANCELLED ||
                    (it.status == BookingStatus.UPCOMING && it.endTimeMillis <= now)
            }
            .sortedByDescending { it.startTimeMillis }

        MySlotsUiState(
            activeBookings = active,
            upcomingBookings = upcoming,
            completedBookings = history,
            currentTimeMillis = now,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MySlotsUiState()
    )

    fun refreshTime() {
        _currentTime.value = System.currentTimeMillis()
    }

    fun cancelBooking(bookingId: String) {
        if (!busyBookingIds.add(bookingId)) return

        viewModelScope.launch {
            try {
                val result = laundryRepository.cancelBooking(bookingId)
                if (result.isSuccess) {
                    _message.value = "Slot booking cancelled"
                } else {
                    _message.value = result.exceptionOrNull()?.message ?: "Failed to cancel slot"
                }
            } finally {
                busyBookingIds.remove(bookingId)
            }
        }
    }

    fun completeCycle(machineId: String, bookingId: String) {
        if (!busyBookingIds.add(bookingId)) return

        viewModelScope.launch {
            try {
                val result = laundryRepository.completeWashCycle(machineId, bookingId)
                if (result.isSuccess) {
                    _message.value = "Laundry cycle marked as completed! Machine is now available."
                } else {
                    _message.value = result.exceptionOrNull()?.message ?: "Failed to complete cycle"
                }
            } finally {
                busyBookingIds.remove(bookingId)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
