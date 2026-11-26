package com.example.aasha.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aasha.data.local.SessionManager
import com.example.aasha.data.repository.LaundryRepository
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.CycleType
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SlotTimeHelper
import com.example.aasha.domain.model.WashingMachine
import com.example.aasha.domain.model.supportedCycles
import com.example.aasha.domain.model.supportsCycle
import com.example.aasha.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SlotAvailability {
    AVAILABLE,
    SELECTED,
    BOOKED_BY_YOU,
    OCCUPIED,
    PAST
}

sealed class BookingResult {
    object Idle : BookingResult()
    object Loading : BookingResult()
    data class Success(val booking: SlotBooking) : BookingResult()
    data class Error(val message: String) : BookingResult()
}

data class BookingUiState(
    val selectedDate: Long = SlotTimeHelper.getStartOfDay(System.currentTimeMillis()),
    val selectedMachine: WashingMachine? = null,
    val selectedTimeSlot: String = "",
    val selectedCycleType: CycleType = CycleType.STANDARD_WASH,
    val notes: String = "",
    val allMachines: List<WashingMachine> = emptyList(),
    val existingBookingsForDay: List<SlotBooking> = emptyList(),
    val currentHostellerId: String = "",
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val result: BookingResult = BookingResult.Idle
) {
    companion object {
        fun getStartOfDay(timestamp: Long): Long = SlotTimeHelper.getStartOfDay(timestamp)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookingViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
    sessionManager: SessionManager,
    networkMonitor: NetworkMonitor,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialMachineId: String? = savedStateHandle["machineId"]

    val availableTimeSlots: List<String> = SlotTimeHelper.standardHourlySlots

    private val _selectedDate = MutableStateFlow(SlotTimeHelper.getStartOfDay(System.currentTimeMillis()))
    private val _selectedMachineId = MutableStateFlow<String?>(initialMachineId)
    private val _selectedTimeSlot = MutableStateFlow("")
    private val _selectedCycleType = MutableStateFlow(CycleType.STANDARD_WASH)
    private val _notes = MutableStateFlow("")
    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    private val _result = MutableStateFlow<BookingResult>(BookingResult.Idle)

    private val bookingsForSelectedDateFlow = _selectedDate.flatMapLatest { date ->
        laundryRepository.getBookingsForDate(date)
    }

    private data class SelectionInputs(
        val date: Long,
        val machineId: String?,
        val slot: String,
        val cycle: CycleType,
        val notes: String,
        val nowMillis: Long,
        val result: BookingResult
    )

    private val selectionInputsFlow = combine(
        _selectedDate,
        _selectedMachineId,
        _selectedTimeSlot,
        _selectedCycleType,
        _notes,
        _currentTimeMillis,
        _result
    ) { params ->
        SelectionInputs(
            date = params[0] as Long,
            machineId = params[1] as String?,
            slot = params[2] as String,
            cycle = params[3] as CycleType,
            notes = params[4] as String,
            nowMillis = params[5] as Long,
            result = params[6] as BookingResult
        )
    }

    val uiState: StateFlow<BookingUiState> = combine(
        laundryRepository.allMachines,
        bookingsForSelectedDateFlow,
        sessionManager.hostellerId,
        networkMonitor.isOnline,
        selectionInputsFlow
    ) { machines, bookingsForDay, hostellerId, isOnline, inputs ->
        val resolvedMachine = inputs.machineId?.let { id ->
            machines.firstOrNull { it.id == id && it.status != MachineStatus.OUT_OF_ORDER }
        } ?: machines.firstOrNull { it.status != MachineStatus.OUT_OF_ORDER }

        val resolvedCycle = if (resolvedMachine != null && !resolvedMachine.supportsCycle(inputs.cycle)) {
            resolvedMachine.supportedCycles().firstOrNull() ?: CycleType.STANDARD_WASH
        } else {
            inputs.cycle
        }

        val resolvedSlot = resolveValidSelectedSlot(
            candidateSlot = inputs.slot,
            date = inputs.date,
            machineId = resolvedMachine?.id,
            bookingsForDay = bookingsForDay,
            nowMillis = inputs.nowMillis
        )

        BookingUiState(
            selectedDate = inputs.date,
            selectedMachine = resolvedMachine,
            selectedTimeSlot = resolvedSlot,
            selectedCycleType = resolvedCycle,
            notes = inputs.notes,
            allMachines = machines,
            existingBookingsForDay = bookingsForDay,
            currentHostellerId = hostellerId.orEmpty(),
            currentTimeMillis = inputs.nowMillis,
            isOnline = isOnline,
            result = inputs.result
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookingUiState()
    )

    init {
        viewModelScope.launch {
            laundryRepository.syncSlotLocksForDate(_selectedDate.value)
        }
    }

    fun refreshCurrentTime() {
        _currentTimeMillis.value = System.currentTimeMillis()
    }

    fun selectDate(date: Long) {
        val now = System.currentTimeMillis()
        _currentTimeMillis.value = now
        val startOfDay = SlotTimeHelper.getStartOfDay(date)
        if (SlotTimeHelper.isPastDate(startOfDay, now)) return

        _selectedDate.value = startOfDay
        viewModelScope.launch {
            laundryRepository.syncSlotLocksForDate(startOfDay)
        }
    }

    fun selectMachine(machine: WashingMachine) {
        if (machine.status == MachineStatus.OUT_OF_ORDER) return
        _selectedMachineId.value = machine.id
        if (!machine.supportsCycle(_selectedCycleType.value)) {
            _selectedCycleType.value = machine.supportedCycles().firstOrNull() ?: CycleType.STANDARD_WASH
        }
    }

    fun selectTimeSlot(slot: String) {
        val state = uiState.value
        val machine = state.selectedMachine ?: return
        val availability = getSlotAvailability(machine.id, slot, state)
        if (availability == SlotAvailability.AVAILABLE || availability == SlotAvailability.SELECTED) {
            _selectedTimeSlot.value = slot
        }
    }

    fun selectCycleType(cycleType: CycleType) {
        val machine = uiState.value.selectedMachine
        if (machine == null || machine.supportsCycle(cycleType)) {
            _selectedCycleType.value = cycleType
        }
    }

    fun updateNotes(notes: String) {
        _notes.value = notes
    }

    fun getSlotAvailability(
        machineId: String,
        slot: String,
        state: BookingUiState = uiState.value
    ): SlotAvailability {
        if (SlotTimeHelper.isSlotPastOrStarted(state.selectedDate, slot, state.currentTimeMillis)) {
            return SlotAvailability.PAST
        }

        val window = SlotTimeHelper.computeSlotWindow(state.selectedDate, slot)
        val matchingBooking = state.existingBookingsForDay.firstOrNull { existing ->
            existing.machineId == machineId &&
                existing.status != BookingStatus.CANCELLED &&
                if (window != null) {
                    existing.startTimeMillis < window.second && existing.endTimeMillis > window.first
                } else {
                    existing.timeSlot == slot
                }
        }

        if (matchingBooking != null) {
            return if (state.currentHostellerId.isNotBlank() &&
                matchingBooking.hostellerId == state.currentHostellerId
            ) {
                SlotAvailability.BOOKED_BY_YOU
            } else {
                SlotAvailability.OCCUPIED
            }
        }

        return if (state.selectedTimeSlot == slot) {
            SlotAvailability.SELECTED
        } else {
            SlotAvailability.AVAILABLE
        }
    }

    fun isSlotBooked(machineId: String, slot: String): Boolean {
        val availability = getSlotAvailability(machineId, slot)
        return availability == SlotAvailability.BOOKED_BY_YOU || availability == SlotAvailability.OCCUPIED
    }

    fun confirmBooking() {
        if (_result.value is BookingResult.Loading) return

        val now = System.currentTimeMillis()
        _currentTimeMillis.value = now
        val current = uiState.value
        val machine = current.selectedMachine

        if (machine == null) {
            _result.value = BookingResult.Error("Please select an available machine.")
            return
        }
        if (machine.status == MachineStatus.OUT_OF_ORDER) {
            _result.value = BookingResult.Error("${machine.name} is currently under maintenance.")
            return
        }
        if (!machine.supportsCycle(current.selectedCycleType)) {
            _result.value = BookingResult.Error(
                "${machine.name} does not support ${current.selectedCycleType.displayName}."
            )
            return
        }
        if (current.selectedTimeSlot.isBlank()) {
            _result.value = BookingResult.Error("No upcoming time slots remain for this date. Please pick Tomorrow or a later date.")
            return
        }

        val availability = getSlotAvailability(machine.id, current.selectedTimeSlot, current)
        when (availability) {
            SlotAvailability.PAST -> {
                _result.value = BookingResult.Error("This time slot has already started. Please select an upcoming slot.")
                return
            }
            SlotAvailability.BOOKED_BY_YOU -> {
                _result.value = BookingResult.Error("You already have a reservation for this time slot.")
                return
            }
            SlotAvailability.OCCUPIED -> {
                _result.value = BookingResult.Error("This time slot is already booked for ${machine.name}.")
                return
            }
            SlotAvailability.AVAILABLE, SlotAvailability.SELECTED -> Unit
        }

        val window = SlotTimeHelper.computeSlotWindow(current.selectedDate, current.selectedTimeSlot)
        if (window == null || window.first <= now) {
            _result.value = BookingResult.Error("This time slot has already started. Please select an upcoming slot.")
            return
        }

        _result.value = BookingResult.Loading

        viewModelScope.launch {
            val res = laundryRepository.bookSlot(
                machineId = machine.id,
                date = current.selectedDate,
                timeSlot = current.selectedTimeSlot,
                cycleType = current.selectedCycleType.displayName,
                startTimeMillis = window.first,
                endTimeMillis = window.second,
                notes = current.notes
            )

            if (res.isSuccess) {
                _notes.value = ""
                _result.value = BookingResult.Success(res.getOrThrow())
            } else {
                _result.value = BookingResult.Error(
                    res.exceptionOrNull()?.message ?: "Could not complete reservation."
                )
            }
        }
    }

    fun resetResult() {
        _result.update { BookingResult.Idle }
    }

    private fun resolveValidSelectedSlot(
        candidateSlot: String,
        date: Long,
        machineId: String?,
        bookingsForDay: List<SlotBooking>,
        nowMillis: Long
    ): String {
        if (machineId == null) return ""

        fun isSlotFree(slot: String): Boolean {
            if (SlotTimeHelper.isSlotPastOrStarted(date, slot, nowMillis)) return false
            val window = SlotTimeHelper.computeSlotWindow(date, slot) ?: return false
            return !SlotTimeHelper.hasOverlappingBooking(
                existingBookings = bookingsForDay,
                machineId = machineId,
                startMillis = window.first,
                endMillis = window.second
            )
        }

        if (candidateSlot.isNotBlank() && isSlotFree(candidateSlot)) {
            return candidateSlot
        }
        return availableTimeSlots.firstOrNull { isSlotFree(it) } ?: ""
    }
}
